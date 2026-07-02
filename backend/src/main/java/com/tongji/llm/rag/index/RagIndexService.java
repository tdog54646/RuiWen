package com.tongji.llm.rag.index;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.tongji.config.EsProperties;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPostDetailRow;
import com.tongji.llm.rag.chunk.SemanticChunker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
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
 * - 将公开且已发布的知文切片并写入向量库
 * - 通过指纹（SHA256/ETag）判断是否需要重建，保证幂等
 * - 采用 delete-by-query 清理旧切片，再批量 upsert 新切片
 * - 使用 {@link SemanticChunker} 实现语义分块（相比旧版固定长度截断，
 *   优先保护段落和句子边界，chunk 大小由 token 数控制，更适合 embedding 模型）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RagIndexService {

    private final VectorStore vectorStore;
    private final KnowPostMapper knowPostMapper;
    private final ElasticsearchClient es;
    private final EsProperties esProps;
    private final SemanticChunker semanticChunker;

    // 拉取 Markdown 正文内容（每个实例创建一次 RestTemplate，避免重复创建开销）
    private final RestTemplate http = new RestTemplate();

    public void ensureIndexed(long postId) {
        // 当前策略：在问答前直接尝试重建（指纹未变化时会跳过）
        reindexSinglePost(postId);
    }

    public int reindexSinglePost(long postId) {
        KnowPostDetailRow row = knowPostMapper.findDetailById(postId);
        if (row == null) {
            log.warn("Post {} not found", postId);
            return 0;
        }

        // 仅索引公开的已发布知文
        if (!"published".equalsIgnoreCase(row.getStatus()) || !"public".equalsIgnoreCase(row.getVisible())) {
            log.warn("Post {} is not public/published, skip indexing", postId);
            return 0;
        }

        // 内容地址缺失则无法抓取正文
        if (!StringUtils.hasText(row.getContentUrl())) {
            log.warn("Post {} missing contentUrl or not found", postId);
            return 0;
        }

        // 指纹检测：如未变化则跳过重建
        String currentSha = row.getContentSha256();
        String currentEtag = row.getContentEtag();
        if (isUpToDate(postId, currentSha, currentEtag)) {
            log.info("Post {} already indexed with same fingerprint, skip", postId);
            return 0;
        }

        // 抓取 Markdown 正文
        String text = fetchContent(row.getContentUrl());
        if (!StringUtils.hasText(text)) {
            log.warn("Post {} content empty", postId);
            return 0;
        }

        // 文本处理
        List<String> chunks = chunkMarkdown(text);
        log.info("Post {} content length={}, chunks size={}", postId, text.length(), chunks.size());
        if (chunks.isEmpty()) {
            log.warn("Post {} produced no chunks, skipping indexing", postId);
            return 0;
        }
        // 幂等 upsert：先删除旧切片
        deleteExistingChunks(postId);

        // 组装 Document（文本 + 业务元数据），用于向量写入与检索过滤
        long nowMs = Instant.now().toEpochMilli();
        List<Document> docs = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            String cid = postId + "#" + i;
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("postId", String.valueOf(postId));
            meta.put("chunkId", cid);
            meta.put("position", i);
            meta.put("contentEtag", currentEtag);
            meta.put("contentSha256", currentSha);
            meta.put("contentUrl", row.getContentUrl());
            meta.put("title", row.getTitle());
            // 新增字段：与 es-mapping-rag-chunk.json 中的字段保持一致
            meta.put("createdAt", nowMs);
            meta.put("updatedAt", nowMs);
            docs.add(new Document(chunks.get(i), meta));
        }
        try {
            // 批量写入向量库
            vectorStore.add(docs);
        } catch (Exception e) {
            log.error("VectorStore add failed: {} (docs size={})", e.getMessage(), docs.size(), e);
            return 0;
        }
        // 返回本次写入的切片数量
        return docs.size();
    }

    /**
     * 指纹判断是否需要重建：
     * - 以 postId 查询任意一条已索引文档的 metadata
     * - 优先比较 SHA256，其次比较 ETag；一致则视为无需重建
     */
    private boolean isUpToDate(long postId, String currentSha, String currentEtag) {
        try {
            if (!StringUtils.hasText(esProps.getIndex())) {
                // 未配置索引名则无法判断，直接视为需要重建
                return false;
            }
            SearchResponse<Map> resp = es.search(s -> s
                            .index(esProps.getIndex())
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
    private void deleteExistingChunks(long postId) {
        try {
            if (!StringUtils.hasText(esProps.getIndex())) return;
            es.deleteByQuery(d -> d
                    .index(esProps.getIndex())
                    .query(q -> q.term(t -> t
                            .field("metadata.postId")
                            .value(v -> v.stringValue(String.valueOf(postId))))));
        } catch (Exception e) {
            log.warn("Delete old chunks failed for post {}: {}", postId, e.getMessage());
        }
    }

    private static String asString(Object o) {
        // 统一处理 null → String 的转换
        return o == null ? null : String.valueOf(o);
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
            log.error("Fetch content failed: {}", e.getMessage());
            return null;
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