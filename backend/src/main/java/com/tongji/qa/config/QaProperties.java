package com.tongji.qa.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 多轮问答模块配置。
 * <p>所有字段均可通过 application.yml 中 {@code qa.} 前缀覆盖默认值。
 */
@Data
@ConfigurationProperties(prefix = "qa")
public class QaProperties {

    /** 携带最近 N 轮历史（一轮 = user + assistant，故实际消息数为 2N）。 */
    private int historyWindow = 6;

    /** 记忆相关配置。 */
    private Memory memory = new Memory();

    /** 问答默认参数。 */
    private Chat chat = new Chat();

    @Data
    public static class Memory {
        /** 每累计 N 条新消息触发一次自动记忆更新。 */
        private int updateThreshold = 8;
        /** 记忆总结时回看最近 N 条消息。 */
        private int summaryWindow = 20;
        /** 记忆总结 LLM 最大生成 token 数。 */
        private int summaryMaxTokens = 1024;
    }

    @Data
    public static class Chat {
        /** 默认 RAG 检索 Chunk 数量上限。 */
        private int defaultTopK = 5;
        /** 默认 LLM 最大生成 token 数。 */
        private int defaultMaxTokens = 1024;
    }
}
