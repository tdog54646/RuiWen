package com.tongji.llm.rag.rerank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tongji.llm.rag.model.RagProperties;
import com.tongji.llm.rag.model.RetrievalChunk;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** 使用 DashScope Cross-Encoder 对 RRF 候选进行真实语义精排。 */
@Slf4j
@Service
public class RerankerService {

    private final RagProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public RerankerService(RagProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(properties.getRerank().getConnectTimeoutMs()))
                .build();
    }

    public boolean isAvailable() {
        RagProperties.Rerank cfg = properties.getRerank();
        return StringUtils.hasText(cfg.getApiUrl())
                && StringUtils.hasText(cfg.getApiKey());
    }

    public List<RetrievalChunk> rerank(String query, List<? extends RetrievalChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) return List.of();
        if (!isAvailable()) {
            throw new IllegalStateException("Reranker 已启用但 API 地址或 Key 未配置");
        }

        RagProperties.Rerank cfg = properties.getRerank();
        try {
            String requestBody = buildRequest(query, chunks, cfg);
            HttpRequest request = HttpRequest.newBuilder(URI.create(cfg.getApiUrl()))
                    .timeout(Duration.ofMillis(cfg.getReadTimeoutMs()))
                    .header("Authorization", "Bearer " + cfg.getApiKey())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                    .build();
            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Reranker HTTP " + response.statusCode()
                        + ": " + safeError(response.body()));
            }
            return parseResponse(response.body(), chunks, cfg);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Reranker 请求被中断", e);
        } catch (Exception e) {
            throw new IllegalStateException("Reranker 调用失败: " + e.getMessage(), e);
        }
    }

    public List<RetrievalChunk> rerank(String query, List<? extends RetrievalChunk> chunks, int topK) {
        return rerank(query, chunks).stream().limit(topK).toList();
    }

    String buildRequest(String query, List<? extends RetrievalChunk> chunks, RagProperties.Rerank cfg) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", cfg.getModel());
        ArrayNode documents;
        if (cfg.getModel() != null && cfg.getModel().startsWith("qwen3-rerank")) {
            root.put("query", query);
            documents = root.putArray("documents");
            root.put("top_n", chunks.size());
            root.put("instruct", "Given a knowledge-base question, retrieve passages that directly answer it.");
        } else {
            ObjectNode input = root.putObject("input");
            input.put("query", query);
            documents = input.putArray("documents");
            root.putObject("parameters")
                    .put("return_documents", false)
                    .put("top_n", chunks.size());
        }
        for (RetrievalChunk chunk : chunks) {
            documents.add(composeDocument(chunk));
        }
        try {
            return objectMapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Reranker 请求序列化失败", e);
        }
    }

    List<RetrievalChunk> parseResponse(
            String json,
            List<? extends RetrievalChunk> chunks,
            RagProperties.Rerank cfg) throws Exception {
        JsonNode root = objectMapper.readTree(json);
        JsonNode results = root.path("results");
        if (!results.isArray()) results = root.path("output").path("results");
        if (!results.isArray()) {
            String code = root.path("code").asText("");
            String message = root.path("message").asText("响应缺少 results");
            throw new IllegalStateException((code.isBlank() ? "" : code + ": ") + message);
        }

        List<RetrievalChunk> ranked = new ArrayList<>();
        for (JsonNode result : results) {
            int index = result.path("index").asInt(-1);
            if (index < 0 || index >= chunks.size()) {
                log.warn("Ignore invalid reranker result index={} candidates={}", index, chunks.size());
                continue;
            }
            double score = result.path("relevance_score").asDouble(Double.NaN);
            if (!Double.isFinite(score)) continue;
            RetrievalChunk chunk = chunks.get(index);
            chunk.setRerankScore(score);
            if (cfg.getMinScore() <= 0 || score >= cfg.getMinScore()) {
                ranked.add(chunk);
            }
        }
        ranked.sort(Comparator.comparingDouble(RetrievalChunk::getRerankScore).reversed());
        if (cfg.getMinScore() <= 0 && ranked.size() != chunks.size()) {
            throw new IllegalStateException("Reranker 返回结果不完整: expected="
                    + chunks.size() + ", actual=" + ranked.size());
        }
        return List.copyOf(ranked);
    }

    private static String composeDocument(RetrievalChunk chunk) {
        String title = StringUtils.hasText(chunk.getTitle()) ? chunk.getTitle().trim() : "";
        String content = chunk.getContent() == null ? "" : chunk.getContent().trim();
        return title.isEmpty() ? content : "标题：" + title + "\n正文：" + content;
    }

    private static String safeError(String body) {
        if (body == null) return "empty response";
        String normalized = body.replaceAll("[\\r\\n]+", " ").trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }
}
