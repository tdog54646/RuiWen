package com.tongji.llm.vision;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 图片识别（视觉模型）配置。
 * <p>默认接入阿里云百炼 QVQ 视觉推理模型，OpenAI 兼容协议。
 * 需 {@code stream=true}：QVQ 非流式响应 {@code content} 恒为空，仅流式才暴露最终回答。
 * 所有字段均可通过 application.yml 中 {@code vision.} 前缀覆盖。
 */
@Data
@ConfigurationProperties(prefix = "vision")
public class VisionProperties {

    /** 视觉模型 API Key；默认回退到通用 OPENAI_API_KEY（同一 DashScope 账号即可调用 QVQ）。 */
    private String apiKey;

    /** OpenAI 兼容入口（QVQ MaaS 专用，区别于 spring.ai.openai.base-url）。 */
    private String baseUrl = "https://llm-ciuhew9j1jjrvz3f.cn-beijing.maas.aliyuncs.com/compatible-mode/v1";

    /** 视觉模型名。 */
    private String model = "qvq-plus";

    /** 单次识别最大生成 token 数。 */
    private int maxTokens = 1024;

    /** 单次识别超时秒数（QVQ 含推理过程，需留足时间）。 */
    private int timeoutSeconds = 120;
}
