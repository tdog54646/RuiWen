package com.tongji.search.outbox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.llm.rag.index.RagIndexService;
import com.tongji.relation.outbox.OutboxTopics;
import com.tongji.search.index.SearchIndexService;
import com.tongji.util.OutboxMessageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 搜索索引的 Outbox 消费者：监听 canal-outbox，驱动 ES 索引的增量更新。
 * 仅处理 entity=knowpost 的 upsert 与软删。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CanalOutboxConsumerSearch {
    private final ObjectMapper objectMapper;
    private final SearchIndexService indexService;
    private final RagIndexService ragIndexService;

    /**
     * 消费 outbox 消息，解析合法行并按实体类型更新索引。
     */
    @KafkaListener(topics = OutboxTopics.CANAL_OUTBOX, groupId = "search-index-consumer")
    public void onMessage(String message, Acknowledgment ack) {
        try {
            List<JsonNode> rows = OutboxMessageUtil.extractRows(objectMapper, message);

            if (rows.isEmpty()) {
                ack.acknowledge();
                return;
            }

            for (JsonNode row : rows) {
                JsonNode payloadNode = row.get("payload");
                if (payloadNode == null) {
                    throw new IllegalArgumentException("outbox 行缺少 payload");
                }

                JsonNode payload = objectMapper.readTree(payloadNode.asText());
                String entity;
                String op;
                Long id;
                if (isUnifiedPayload(payload)) {
                    entity = text(payload.get("aggregateType"));
                    id = asLong(payload.get("aggregateId"));
                    JsonNode data = payload.get("data");
                    op = text(data == null ? null : data.get("op"));
                    if (op == null) {
                        op = text(payload.get("eventType"));
                    }
                } else {
                    entity = text(payload.get("entity"));
                    op = text(payload.get("op"));
                    id = asLong(payload.get("id"));
                }
                if (!"knowpost".equals(entity)) {
                    continue;
                }
                if (id == null || op == null || op.isBlank()) {
                    throw new IllegalArgumentException("knowpost outbox payload 字段不完整");
                }

                // 软删与 upsert，均覆盖同一文档 ID；RAG 先删旧切片再按当前 DB 状态重建。
                if (isDeleteOperation(op)) {
                    indexService.softDeleteKnowPost(id);
                    ragIndexService.deletePostChunks(id);
                } else {
                    indexService.upsertKnowPost(id);
                    ragIndexService.rebuildSinglePost(id);
                }
            }
            // 处理成功才提交位点；处理中抛出的异常交给全局 DefaultErrorHandler（重试 + 死信队列 canal-outbox.DLT）
            ack.acknowledge();
        } catch (Exception e) {
            log.error("处理 canal-outbox 消息失败，将重试后转死信队列 canal-outbox.DLT: msg={}", message, e);
            throw new RuntimeException(e);
        }
    }

    private String text(JsonNode n) {
        return n == null ? null : n.asText();
    }

    private boolean isUnifiedPayload(JsonNode payload) {
        return payload != null
                && payload.has("aggregateType")
                && payload.has("eventType")
                && payload.has("data");
    }

    private boolean isDeleteOperation(String op) {
        return "delete".equalsIgnoreCase(op)
                || "KnowPostDeleted".equalsIgnoreCase(op);
    }

    private Long asLong(JsonNode n) {
        if (n == null) {
            return null;
        }

        try {
            return Long.parseLong(n.asText());
        } catch (Exception e) {
            return null;
        }
    }
}
