package com.tongji.asr.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 语音转文字（ASR）模块配置入口：注册 {@link AsrProperties}。
 */
@Configuration
@EnableConfigurationProperties(AsrProperties.class)
public class AsrConfig {
}
