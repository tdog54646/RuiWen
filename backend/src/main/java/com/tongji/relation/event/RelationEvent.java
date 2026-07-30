package com.tongji.relation.event;

/**
 * 构造关系事件。
 *
 * @param type       事件类型
 * @param fromUserId 触发方用户ID
 * @param toUserId   目标方用户ID
 * @param id         关系记录ID，可为空
 * @param eventId    单次状态迁移的唯一事件 ID
 * @param occurredAt 事件发生时间（毫秒）
 */
public record RelationEvent(
        String type,
        Long fromUserId,
        Long toUserId,
        Long id,
        String eventId,
        Long occurredAt) {
}
