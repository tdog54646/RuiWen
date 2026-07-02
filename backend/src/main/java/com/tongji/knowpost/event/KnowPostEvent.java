package com.tongji.knowpost.event;

/**
 * 构造知文 Outbox 事件。
 *
 * @param entity 实体类型
 * @param op     操作类型
 * @param id     知文ID
 */
public record KnowPostEvent(
        String entity,
        String op,
        Long id) {
}
