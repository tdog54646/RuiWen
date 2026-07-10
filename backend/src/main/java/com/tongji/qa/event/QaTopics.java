package com.tongji.qa.event;

/**
 * 多轮问答模块 Kafka 主题常量。
 */
public final class QaTopics {
    /** 记忆自动更新主题：载荷为 {@link MemoryUpdateEvent} 的 JSON。 */
    public static final String MEMORY_UPDATE = "qa-memory-update";

    private QaTopics() {}
}
