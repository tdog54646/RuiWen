package com.tongji.asr.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 语音转文字（ASR）模块配置。
 * <p>实时流式识别基于阿里云 DashScope paraformer-realtime-v2 WebSocket。
 * 所有字段均可通过 application.yml 中 {@code asr.} 前缀覆盖默认值。
 */
@Data
@ConfigurationProperties(prefix = "asr")
public class AsrProperties {

    /** DashScope API Key（与 OPENAI_API_KEY 独立配置）。 */
    private String apiKey;

    /** DashScope 实时 ASR WebSocket 入口。 */
    private String wsUrl = "wss://dashscope.aliyuncs.com/api-ws/v1/inference/";

    /** 实时识别模型。 */
    private String model = "paraformer-realtime-v2";
}
