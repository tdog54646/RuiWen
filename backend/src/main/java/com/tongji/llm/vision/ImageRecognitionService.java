package com.tongji.llm.vision;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

/**
 * 图片识别服务（QVQ 视觉推理模型）。
 * <p>封装为 Spring AI tool 的底层 agent：给定图片 URL 与识别意图，返回图片内容的文本描述。
 *
 * <h3>流式聚合</h3>
 * QVQ 是推理模型，非流式响应 {@code content} 恒为空（思考过程不暴露）；
 * 仅 {@code stream=true} 时分阶段流出 {@code reasoning_content}（思考）与 {@code content}（最终回答）。
 * 故本服务强制流式调用，仅聚合 {@code delta.content}，忽略 {@code reasoning_content}。
 *
 * <h3>降级</h3>
 * 任何异常（网络、鉴权、解析）都回退为友好提示串，不抛异常--避免中断上层多轮对话（对齐知识库工具风格）。
 *
 * <p>HTTP 客户端使用 JDK 内置 {@link HttpClient}，无需 reactor-netty。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageRecognitionService {

    private static final String DEFAULT_PROMPT = "请识别并详细描述这张图片的内容。";
    private static final String SSE_DONE = "[DONE]";

    private final VisionProperties properties;
    private final ObjectMapper objectMapper;

    private HttpClient httpClient;

    @PostConstruct
    void init() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /**
     * 识别图片内容（支持单次多图）。
     *
     * @param imageUrls 图片 URL 列表（至少一张）
     * @param prompt    识别意图/关注点；为空时用默认描述指令
     * @return 图片内容的文本描述；输入非法或调用失败时返回降级提示串（不抛异常）
     */
    public String recognize(List<String> imageUrls, String prompt) {
        List<String> urls = imageUrls == null ? List.of()
                : imageUrls.stream().filter(StringUtils::hasText).map(String::trim).toList();
        if (urls.isEmpty()) {
            return "（未提供图片，无法识别）";
        }
        if (!StringUtils.hasText(properties.getApiKey())) {
            log.warn("Image recognition skipped: vision.api-key is empty");
            return "（图片识别未配置 API Key，无法识别）";
        }
        String focus = StringUtils.hasText(prompt) ? prompt.trim() : DEFAULT_PROMPT;

        HttpRequest request = buildRequest(urls, focus);
        try {
            HttpResponse<Stream<String>> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofLines());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                String err = response.body() == null ? "" : response.body().findFirst().orElse("");
                log.error("Image recognition HTTP {} (urls={}): {}", response.statusCode(), urls, err);
                return "（图片识别服务返回错误，请稍后重试）";
            }

            StringBuilder answer = new StringBuilder();
            response.body().forEach(line -> {
                String delta = extractDeltaContent(line);
                if (delta != null) {
                    answer.append(delta);
                }
            });
            String result = answer.toString();
            if (!StringUtils.hasText(result)) {
                log.warn("Image recognition returned empty content (urls={})", urls);
                return "（图片识别未返回有效内容，请稍后重试或换一张图片）";
            }
            log.debug("Image recognized (count={}, chars={})", urls.size(), result.length());
            return result;
        } catch (Exception e) {
            log.error("Image recognition failed (urls={}): {}", urls, e.getMessage());
            return "（图片识别失败，请稍后重试或换一张图片）";
        }
    }

    /** 构造 QVQ 流式多模态请求：POST {base-url}/chat/completions，body 为 OpenAI 兼容 JSON（多图各一个 image_url part）。 */
    private HttpRequest buildRequest(List<String> imageUrls, String prompt) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", properties.getModel());
        root.put("stream", true);
        root.put("max_tokens", properties.getMaxTokens());

        ObjectNode userMsg = objectMapper.createObjectNode();
        userMsg.put("role", "user");
        ArrayNode content = userMsg.putArray("content");
        for (String url : imageUrls) {
            ObjectNode imagePart = content.addObject();
            imagePart.put("type", "image_url");
            imagePart.putObject("image_url").put("url", url);
        }
        ObjectNode textPart = content.addObject();
        textPart.put("type", "text");
        textPart.put("text", prompt);

        root.putArray("messages").add(userMsg);
        String body = root.toString();

        return HttpRequest.newBuilder()
                .uri(URI.create(properties.getBaseUrl() + "/chat/completions"))
                .timeout(Duration.ofSeconds(Math.max(properties.getTimeoutSeconds(), 10)))
                .header("Authorization", "Bearer " + properties.getApiKey())
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
    }

    /**
     * 从一行 SSE 数据中提取 {@code choices[0].delta.content}。
     * <p>SSE 行形如 {@code data: {...}}；非 data 行、{@code [DONE]}、或仅含 reasoning_content 的帧返回 null。
     */
    private String extractDeltaContent(String line) {
        if (line == null) {
            return null;
        }
        String trimmed = line.strip();
        if (!trimmed.startsWith("data:")) {
            return null;
        }
        String data = trimmed.substring("data:".length()).strip();
        if (data.isEmpty() || SSE_DONE.equals(data)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(data);
            JsonNode delta = root.path("choices").path(0).path("delta");
            JsonNode content = delta.path("content");
            return content.isTextual() ? content.asText() : null;
        } catch (Exception e) {
            log.debug("Skip unparseable SSE chunk: {}", data);
            return null;
        }
    }
}
