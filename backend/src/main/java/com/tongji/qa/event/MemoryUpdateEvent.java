package com.tongji.qa.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 记忆更新事件：触发后台 LLM 总结该用户的长期画像。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MemoryUpdateEvent {
    private long userId;

    public static MemoryUpdateEvent of(long userId) {
        return new MemoryUpdateEvent(userId);
    }
}
