package com.tongji.llm.rag.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPostDetailRow;
import com.tongji.llm.rag.chunk.SemanticChunker;
import com.tongji.storage.OssStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

/**
 * RAG 索引构建服务：
 * - 将已发布知文切片并写入向量库（公开内容进公共检索，非公开内容进个人私有库，检索时按 visible/creatorId 过滤）
 * - 通过指纹（SHA256/ETag）判断是否需要重建，保证幂等
 * - 采用 delete-by-query 清理旧切片，再批量 upsert 新切片
 * - 使用 {@link SemanticChunker} 实现语义分块（相比旧版固定长度截断，
 *   优先保护段落和句子边界，chunk 大小由 token 数控制，更适合 embedding 模型）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagIndexService {

    private final RagVectorStoreFactory vectorStoreFactory;
    private final RagIndexManager indexManager;
    private final KnowPostMapper knowPostMapper;
    private final OssStorageService ossStorageService;
    private final ElasticsearchClient es;
    private final SemanticChunker semanticChunker;

    // 拉取 Markdown 正文内容（每个实例创建一次 RestTemplate，避免重复创建开销）
    // 必须设置超时：拉取正文一旦阻塞会拖死整个消费线程，导致后续消息全部积压。
    private final RestTemplate http = buildHttp();

    private static RestTemplate buildHttp() {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        return new RestTemplate(factory);
    }

    public void ensureIndexed(long postId) {
        // 当前策略：在问答前直接尝试重建（指纹未变化时会跳过）
        reindexSinglePost(postId);
    }

    public int reindexSinglePost(long postId) {
        return reindexSinglePost(postId, false);
    }

    public int rebuildSinglePost(long postId) {
        return reindexSinglePost(postId, true);
    }

    /** 向指定版本索引强制写入，供零停机全量重建使用。 */
    public int rebuildSinglePost(long postId, String targetIndex) {
        return reindexSinglePost(postId, true, targetIndex);
    }

    private int reindexSinglePost(long postId, boolean force) {
        return reindexSinglePost(postId, force, indexManager.readAlias());
    }

    private int reindexSinglePost(long postId, boolean force, String targetIndex) {
        KnowPostDetailRow row = knowPostMapper.findDetailById(postId);
        if (row == null) {
            deletePostChunks(postId, targetIndex);
            return 0;
        }

        // 仅索引已发布知文；不再限制 visible——非公开内容进入个人私有库，检索时按 creatorId 过滤
        if (!"published".equalsIgnoreCase(row.getStatus())) {
            deletePostChunks(postId, targetIndex);
            return 0;
        }

        // 内容地址缺失则无法抓取正文
        if (!StringUtils.hasText(row.getContentUrl())) {
            throw new IllegalStateException("知文正文地址缺失: " + postId);
        }

        // 指纹检测：如未变化则跳过重建
        String currentSha = row.getContentSha256();
        String currentEtag = row.getContentEtag();
        if (!force && isUpToDate(postId, row, targetIndex)) {
            log.info("Post {} already indexed with same fingerprint, skip", postId);
            return 0;
        }

        // 抓取 Markdown 正文
        String contentReference = row.getContentObjectKey() == null
                ? row.getContentUrl() : row.getContentObjectKey();
        String fetchUrl = "public".equals(row.getVisible())
                ? row.getContentUrl()
                : ossStorageService.privateReadUrl(contentReference);
        String text = fetchContent(fetchUrl);
        if (!StringUtils.hasText(text)) {
            throw new IllegalStateException("知文正文拉取为空: " + postId);
        }

        // 文本处理
        List<String> chunks = chunkMarkdown(text);
        log.info("Post {} content length={}, chunks size={}", postId, text.length(), chunks.size());
        if (chunks.isEmpty()) {
            throw new IllegalStateException("知文无法生成向量切片: " + postId);
        }

        Set<String> previousDocumentIds = findDocumentIds(postId, targetIndex);

        // 组装 Document（文本 + 业务元数据），用于向量写入与检索过滤
        long nowMs = Instant.now().toEpochMilli();
        String indexVersion = UUID.nameUUIDFromBytes((String.valueOf(currentSha) + "|"
                + String.valueOf(currentEtag) + "|" + String.valueOf(row.getTitle()) + "|"
                + String.valueOf(row.getVisible()) + "|" + row.getCreatorId())
                .getBytes(StandardCharsets.UTF_8)).toString();
        List<Document> docs = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            String cid = postId + "#" + indexVersion + "#" + i;
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("postId", String.valueOf(postId));
            meta.put("chunkId", cid);
            meta.put("position", i);
            putIfNonNull(meta, "contentEtag", currentEtag);
            putIfNonNull(meta, "contentSha256", currentSha);
            putIfNonNull(meta, "contentUrl", row.getContentUrl());
            putIfNonNull(meta, "title", row.getTitle());
            // 新增字段：与 es-mapping-rag-chunk.json 中的字段保持一致
            meta.put("createdAt", nowMs);
            meta.put("updatedAt", nowMs);
            // 用户隔离：检索时按 visible/creatorId 过滤
            putIfNonNull(meta, "visible", row.getVisible());
            putIfNonNull(meta, "creatorId", row.getCreatorId() == null ? null : String.valueOf(row.getCreatorId()));
            docs.add(new Document(cid, chunks.get(i), meta));
        }
        // 先以稳定 ID upsert 新切片；只有全部写入成功后才清理旧的随机 ID 或多余尾部切片。
        var vectorStore = vectorStoreFactory.forIndex(targetIndex);
        vectorStore.add(docs);
        Set<String> currentDocumentIds = new LinkedHashSet<>();
        for (Document doc : docs) currentDocumentIds.add(doc.getId());
        previousDocumentIds.removeAll(currentDocumentIds);
        if (!previousDocumentIds.isEmpty()) {
            vectorStore.delete(new ArrayList<>(previousDocumentIds));
        }
        // 返回本次写入的切片数量
        return docs.size();
    }

    /**
     * 指纹判断是否需要重建：
     * - 以 postId 查询任意一条已索引文档的 metadata
     * - 优先比较 SHA256，其次比较 ETag；一致则视为无需重建
     */
    private boolean isUpToDate(long postId, KnowPostDetailRow current, String indexName) {
        try {
            if (!StringUtils.hasText(indexName)) {
                // 未配置索引名则无法判断，直接视为需要重建
                return false;
            }
            SearchResponse<Map> resp = es.search(s -> s
                            .index(indexName)
                            .size(1)
                            .query(q -> q.term(t -> t
                                    .field("metadata.postId")
                                    .value(v -> v.stringValue(String.valueOf(postId))))),
                    Map.class);
            List<Hit<Map>> hits = resp.hits().hits();
            if (hits == null || hits.isEmpty()) return false;
            Map source = hits.getFirst().source();
            if (source == null) return false;
            Object metaObj = source.get("metadata");
            if (!(metaObj instanceof Map<?, ?> meta)) return false;
            // 权限与展示元数据同样属于索引指纹。字段缺失时必须重建，不能沿用旧的宽松语义。
            if (!Objects.equals(asString(meta.get("visible")), current.getVisible())
                    || !Objects.equals(asString(meta.get("creatorId")), String.valueOf(current.getCreatorId()))
                    || !Objects.equals(asString(meta.get("title")), current.getTitle())) {
                return false;
            }
            String currentSha = current.getContentSha256();
            String currentEtag = current.getContentEtag();
            String indexedSha = asString(meta.get("contentSha256"));
            String indexedEtag = asString(meta.get("contentEtag"));
            if (StringUtils.hasText(currentSha) && StringUtils.hasText(indexedSha)) {
                return Objects.equals(currentSha, indexedSha);
            }
            if (StringUtils.hasText(currentEtag) && StringUtils.hasText(indexedEtag)) {
                return Objects.equals(currentEtag, indexedEtag);
            }
            return false;
        } catch (Exception e) {
            log.warn("Fingerprint check failed for post {}: {}", postId, e.getMessage());
            return false;
        }
    }

    /**
     * 删除旧切片：按 metadata.postId 精确删除，确保 upsert 幂等
     */
    public void deletePostChunks(long postId) {
        deletePostChunks(postId, indexManager.readAlias());
    }

    private void deletePostChunks(long postId, String indexName) {
        try {
            requireIndexConfigured(indexName);
            es.deleteByQuery(d -> d
                    .index(indexName)
                    .query(q -> q.term(t -> t
                            .field("metadata.postId")
                            .value(v -> v.stringValue(String.valueOf(postId))))));
        } catch (Exception e) {
            log.error("Delete old chunks failed for post {}", postId, e);
            throw new IllegalStateException("RAG 索引删除失败: " + postId, e);
        }
    }

    private Set<String> findDocumentIds(long postId, String indexName) {
        try {
            requireIndexConfigured(indexName);
            SearchResponse<Map> response = es.search(s -> s
                            .index(indexName)
                            .size(10_000)
                            .source(source -> source.fetch(false))
                            .query(q -> q.term(t -> t
                                    .field("metadata.postId")
                                    .value(v -> v.stringValue(String.valueOf(postId))))),
                    Map.class);
            Set<String> ids = new LinkedHashSet<>();
            for (Hit<Map> hit : response.hits().hits()) {
                if (hit.id() != null) ids.add(hit.id());
            }
            return ids;
        } catch (Exception e) {
            throw new IllegalStateException("RAG 旧切片查询失败: " + postId, e);
        }
    }

    private void requireIndexConfigured(String indexName) {
        if (!StringUtils.hasText(indexName)) {
            throw new IllegalStateException("RAG Elasticsearch 索引名未配置");
        }
    }

    private static String asString(Object o) {
        // 统一处理 null → String 的转换
        return o == null ? null : String.valueOf(o);
    }

    private static void putIfNonNull(Map<String, Object> metadata, String key, Object value) {
        if (value != null) metadata.put(key, value);
    }

    /**
     * 拉取正文内容（Markdown 文本）。
     */
    private String fetchContent(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(List.of(MediaType.TEXT_HTML, MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON));
            ResponseEntity<byte[]> resp = http.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
            byte[] bytes = resp.getBody();
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            MediaType contentType = resp.getHeaders().getContentType();
            Charset charset = (contentType != null && contentType.getCharset() != null)
                    ? contentType.getCharset()
                    : StandardCharsets.UTF_8;
            return new String(bytes, charset);
        } catch (Exception e) {
            throw new IllegalStateException("RAG 正文拉取失败", e);
        }
    }

    /**
     * 文本预处理 + 语义分块。
     * <p>
     * 相比旧版按 Markdown 标题切段 + 固定字符数截断（800字符/100重叠），
     * 新版语义分块器 {@link SemanticChunker} 采用两阶段策略：
     * <ol>
     *   <li>按段落边界（\n\n）和中文标点（。！？；）切分语义单元。</li>
     *   <li>交由 TokenTextSplitter 在 token 层面做细粒度截断
     *      （默认 chunk_size=500 tokens, overlap=50 tokens）。</li>
     * </ol>
     * 这种方式更好地保护句子和段落的语义完整性，
     * 适合 embedding 模型对固定 token 数输入的需求。
     *
     * @param text 归一化后的 Markdown 正文
     * @return 语义分块后的文本列表
     */
    private List<String> chunkMarkdown(String text) {
        // 折叠多余空白、去掉行首行尾空格
        String normalized = text
                .replaceAll("\\r\\n", "\n")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\n[ \\t]+", "\n")
                .trim();

        // 交给 SemanticChunker 做语义分块
        return semanticChunker.chunk(normalized);
    }

    // -------------------------------------------------------------------------
    // 旧版 getChunks 方法保留（供参考），新代码不再使用
    // -------------------------------------------------------------------------

    /**
     * @deprecated 请使用 {@link SemanticChunker#chunk(String)}，
     *             本方法仅在历史兼容性场景下保留。
     */
    @Deprecated
    private static List<String> getChunks(List<String> paras) {
        List<String> chunks = new ArrayList<>();
        for (String p : paras) {
            if (p.codePointCount(0, p.length()) <= 800) {
                chunks.add(p);
            } else {
                int start = 0;
                int limit = 800;
                while (start < p.length()) {
                    int end = Math.min(start + limit, p.length());
                    int cpCount = p.codePointCount(start, end);
                    while (cpCount > limit && end > start) {
                        end--;
                        cpCount = p.codePointCount(start, end);
                    }
                    chunks.add(p.substring(start, end));
                    if (end >= p.length()) break;
                    start = Math.max(end - 100, start + 1);
                }
            }
        }
        return chunks;
    }
}
