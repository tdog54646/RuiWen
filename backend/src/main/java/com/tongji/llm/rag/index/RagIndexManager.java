package com.tongji.llm.rag.index;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tongji.llm.rag.model.RagProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 管理 RAG 的稳定读写别名与版本化物理索引。
 *
 * <p>启动时只做兼容性接管：若稳定别名不存在，则优先把它指向旧索引；旧索引也
 * 不存在时才创建当前版本索引。真正的 mapping 迁移由管理员显式触发，避免启动
 * 过程执行不可逆的数据切换。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RagIndexManager {

    private static final Pattern SAFE_INDEX_NAME = Pattern.compile("[a-z0-9._-]+");

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final RagProperties properties;

    @PostConstruct
    public void ensureAliasReady() {
        try {
            String alias = readAlias();
            if (aliasExists(alias)) {
                return;
            }

            String legacy = legacyIndex();
            if (indexExists(legacy)) {
                addAlias(legacy, alias);
                log.info("RAG read alias {} attached to legacy index {}", alias, legacy);
                return;
            }

            String target = targetIndex();
            createVersionedIndexIfMissing(target);
            addAlias(target, alias);
            log.info("RAG read alias {} initialized with index {}", alias, target);
        } catch (Exception e) {
            throw new IllegalStateException("RAG 索引别名初始化失败", e);
        }
    }

    public String readAlias() {
        return safeName(properties.getIndex().getAlias(), "rag.index.alias");
    }

    public String legacyIndex() {
        return safeName(properties.getIndex().getLegacyName(), "rag.index.legacy-name");
    }

    public String targetIndex() {
        return safeName(properties.getIndex().physicalName(), "rag.index target");
    }

    public int dimensions() {
        return properties.getIndex().getDimensions();
    }

    /**
     * 将稳定别名迁移到当前版本索引。新索引先完成 mapping 创建和数据复制，计数一致
     * 后再原子切换别名；任何前置步骤失败都不会影响线上旧索引。
     */
    public synchronized MigrationResult migrateToCurrentVersion() {
        try {
            ensureAliasReady();
            String alias = readAlias();
            String target = targetIndex();
            List<String> current = aliasTargets(alias);
            if (current.size() == 1 && current.contains(target)) {
                long count = count(target);
                return new MigrationResult(alias, current, target, count, count, false);
            }

            if (current.contains(target)) {
                throw new IllegalStateException("RAG 目标索引已被别名使用且别名还指向其他索引，拒绝自动删除: "
                        + current);
            }
            if (indexExists(target)) {
                deleteIndex(target);
            }
            createVersionedIndexIfMissing(target);

            long sourceCount = count(alias);
            reindex(alias, target);
            long targetCount = count(target);
            if (sourceCount != targetCount) {
                throw new IllegalStateException("RAG 迁移计数不一致: source=" + sourceCount
                        + ", target=" + targetCount);
            }

            swapAlias(alias, current, target);
            log.info("RAG index migrated: alias={}, from={}, to={}, documents={}",
                    alias, current, target, targetCount);
            return new MigrationResult(alias, current, target, sourceCount, targetCount, true);
        } catch (Exception e) {
            throw new IllegalStateException("RAG 索引迁移失败", e);
        }
    }

    void createVersionedIndexIfMissing(String index) throws IOException {
        if (indexExists(index)) return;
        Request request = new Request("PUT", "/" + index);
        request.setJsonEntity(mappingJson(objectMapper, properties));
        perform(request);
    }

    static String mappingJson(ObjectMapper mapper, RagProperties properties) {
        RagProperties.Index cfg = properties.getIndex();
        ObjectNode root = mapper.createObjectNode();
        ObjectNode props = root.putObject("mappings").putObject("properties");
        props.putObject("id").put("type", "keyword");
        props.putObject("content")
                .put("type", "text")
                .put("analyzer", cfg.getAnalyzer())
                .put("search_analyzer", cfg.getSearchAnalyzer());
        props.putObject("embedding")
                .put("type", "dense_vector")
                .put("dims", cfg.getDimensions())
                .put("index", true)
                .put("similarity", "cosine");

        ObjectNode meta = props.putObject("metadata").put("type", "object").putObject("properties");
        meta.putObject("postId").put("type", "keyword");
        meta.putObject("chunkId").put("type", "keyword");
        meta.putObject("position").put("type", "integer");
        ObjectNode title = meta.putObject("title")
                .put("type", "text")
                .put("analyzer", cfg.getAnalyzer())
                .put("search_analyzer", cfg.getSearchAnalyzer());
        title.putObject("fields").putObject("keyword")
                .put("type", "keyword").put("ignore_above", 256);
        meta.putObject("visible").put("type", "keyword");
        meta.putObject("creatorId").put("type", "keyword");
        meta.putObject("contentEtag").put("type", "keyword");
        meta.putObject("contentSha256").put("type", "keyword");
        meta.putObject("contentUrl").put("type", "keyword").put("index", false);
        meta.putObject("createdAt").put("type", "date").put("format", "epoch_millis");
        meta.putObject("updatedAt").put("type", "date").put("format", "epoch_millis");
        try {
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("RAG mapping 序列化失败", e);
        }
    }

    private void reindex(String source, String target) throws IOException {
        ObjectNode body = objectMapper.createObjectNode();
        body.putObject("source").put("index", source);
        body.putObject("dest").put("index", target).put("op_type", "create");
        body.put("conflicts", "proceed");
        Request request = new Request("POST", "/_reindex");
        request.addParameter("wait_for_completion", "true");
        request.addParameter("refresh", "true");
        request.setJsonEntity(objectMapper.writeValueAsString(body));
        JsonNode result = performJson(request);
        if (result.path("failures").isArray() && !result.path("failures").isEmpty()) {
            throw new IllegalStateException("RAG reindex 出现失败项: " + result.path("failures"));
        }
    }

    private void swapAlias(String alias, List<String> current, String target) throws IOException {
        ObjectNode body = objectMapper.createObjectNode();
        var actions = body.putArray("actions");
        for (String index : current) {
            actions.addObject().putObject("remove").put("index", index).put("alias", alias);
        }
        actions.addObject().putObject("add")
                .put("index", target).put("alias", alias).put("is_write_index", true);
        Request request = new Request("POST", "/_aliases");
        request.setJsonEntity(objectMapper.writeValueAsString(body));
        perform(request);
    }

    private void addAlias(String index, String alias) throws IOException {
        ObjectNode body = objectMapper.createObjectNode();
        body.putArray("actions").addObject().putObject("add")
                .put("index", index).put("alias", alias).put("is_write_index", true);
        Request request = new Request("POST", "/_aliases");
        request.setJsonEntity(objectMapper.writeValueAsString(body));
        perform(request);
    }

    private List<String> aliasTargets(String alias) throws IOException {
        JsonNode json = performJson(new Request("GET", "/_alias/" + alias));
        List<String> names = new ArrayList<>();
        Iterator<Map.Entry<String, JsonNode>> fields = json.fields();
        while (fields.hasNext()) names.add(fields.next().getKey());
        return names;
    }

    private long count(String index) throws IOException {
        return performJson(new Request("GET", "/" + index + "/_count")).path("count").asLong();
    }

    private boolean aliasExists(String alias) throws IOException {
        return exists(new Request("HEAD", "/_alias/" + alias));
    }

    private boolean indexExists(String index) throws IOException {
        return exists(new Request("HEAD", "/" + index));
    }

    private boolean exists(Request request) throws IOException {
        try {
            return perform(request).getStatusLine().getStatusCode() == 200;
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 404) return false;
            throw e;
        }
    }

    private void deleteIndex(String index) throws IOException {
        perform(new Request("DELETE", "/" + index));
    }

    private JsonNode performJson(Request request) throws IOException {
        Response response = perform(request);
        if (response.getEntity() == null) return objectMapper.createObjectNode();
        return objectMapper.readTree(EntityUtils.toString(response.getEntity()));
    }

    private Response perform(Request request) throws IOException {
        return restClient.performRequest(request);
    }

    private static String safeName(String value, String property) {
        if (!StringUtils.hasText(value) || !SAFE_INDEX_NAME.matcher(value).matches()) {
            throw new IllegalArgumentException(property + " 不是安全的 Elasticsearch 索引名");
        }
        return value;
    }

    public record MigrationResult(
            String alias,
            List<String> previousIndices,
            String targetIndex,
            long sourceDocuments,
            long targetDocuments,
            boolean switched) {
    }
}
