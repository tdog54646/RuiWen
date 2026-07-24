package com.tongji.llm.vision;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 图片识别模块配置入口：注册 {@link VisionProperties}。
 * <p>{@link ImageRecognitionService} 使用 JDK 内置 {@link java.net.http.HttpClient}
 * 直接调用 QVQ 的 OpenAI 兼容流式接口，无需引入 reactor-netty / WebClient。
 */
@Configuration
@EnableConfigurationProperties(VisionProperties.class)
public class VisionConfig {
}
