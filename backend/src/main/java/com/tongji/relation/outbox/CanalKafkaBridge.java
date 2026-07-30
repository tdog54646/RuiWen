package com.tongji.relation.outbox;

import com.alibaba.otter.canal.client.CanalConnector;
import com.alibaba.otter.canal.client.CanalConnectors;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.core.task.TaskExecutor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * Canal→Kafka 桥接器。
 * 职责：订阅 outbox 表的行级变更（ROWDATA），仅转发 INSERT/UPDATE 的 payload 字段到 Kafka 主题；批次确认位点确保至少一次语义。
 * 可靠性：解析失败或非关心类型不提交位点；停止时断开 Canal 连接并清理资源。
 */
@Service
public class CanalKafkaBridge implements SmartLifecycle {
    private final KafkaTemplate<String, String> kafka;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String host;
    private final int port;
    private final String destination;
    private final String username;
    private final String password;
    private final String filter;
    private final int batchSize;
    private final long intervalMs;
    private final long reconnectBackoffMs;
    private final long kafkaSendTimeoutSeconds;
    private volatile boolean running;
    private final TaskExecutor taskExecutor;
    private volatile CanalConnector connector;
    private static final Logger log = LoggerFactory.getLogger(CanalKafkaBridge.class);

    /**
     * Canal 到 Kafka 的桥接器。
     * @param kafka Kafka 模板
     * @param objectMapper JSON 序列化器
     * @param enabled 是否启用
     * @param host Canal 主机
     * @param port Canal 端口
     * @param destination 实例名
     * @param username 用户名
     * @param password 密码
     * @param filter 订阅过滤表达式
     * @param batchSize 拉取批次大小
     * @param intervalMs 空轮询间隔毫秒
     */
    public CanalKafkaBridge(KafkaTemplate<String, String> kafka,
                            ObjectMapper objectMapper,
                            @Qualifier("taskExecutor") TaskExecutor taskExecutor,
                            @Value("${canal.enabled}") boolean enabled,
                            @Value("${canal.host}") String host,
                            @Value("${canal.port}") int port,
                            @Value("${canal.destination}") String destination,
                            @Value("${canal.username}") String username,
                            @Value("${canal.password}") String password,
                            @Value("${canal.filter}") String filter,
                            @Value("${canal.batchSize}") int batchSize,
                            @Value("${canal.intervalMs}") long intervalMs,
                            @Value("${canal.reconnect-backoff-ms:5000}") long reconnectBackoffMs,
                            @Value("${canal.kafka-send-timeout-seconds:30}") long kafkaSendTimeoutSeconds) {
        this.kafka = kafka;
        this.objectMapper = objectMapper;
        this.taskExecutor = taskExecutor;
        this.enabled = enabled;
        this.host = host;
        this.port = port;
        this.destination = destination;
        this.username = username;
        this.password = password;
        this.filter = filter;
        this.batchSize = batchSize;
        this.intervalMs = intervalMs;
        this.reconnectBackoffMs = reconnectBackoffMs;
        this.kafkaSendTimeoutSeconds = kafkaSendTimeoutSeconds;
    }

    /**
     * 启动桥接器：消费 Canal 并投递到 Kafka。
     */
    @Override
    public void start() {
        if (!enabled) {
            log.info("Canal bridge disabled");
            return;
        }
        if (running) {
            log.info("Canal bridge start skipped: running={} enabled={} host={} port={} dest={} filter={}", running, enabled, host, port, destination, filter);
            return;
        }
        running = true;
        taskExecutor.execute(this::runLoop);
    }

    private void runLoop() {
        try {
            while (running) {
                CanalConnector current = null;
                try {
                    current = CanalConnectors.newSingleConnector(
                            new InetSocketAddress(host, port), destination, username, password);
                    connector = current;
                    log.info("Canal connecting to {}:{} dest={} filter={}", host, port, destination, filter);
                    current.connect();
                    current.subscribe(filter);
                    current.rollback();
                    log.info("Canal connected and subscribed: host={} port={} dest={} filter={} batchSize={} intervalMs={}ms",
                            host, port, destination, filter, batchSize, intervalMs);
                    consumeConnected(current);
                } catch (Exception e) {
                    if (running) {
                        log.warn("Canal bridge disconnected; retrying in {}ms: {}", reconnectBackoffMs, e.getMessage(), e);
                    }
                } finally {
                    disconnect(current);
                    if (connector == current) connector = null;
                }
                if (running) sleep(reconnectBackoffMs);
            }
        } finally {
            running = false;
            connector = null;
        }
    }

    private void consumeConnected(CanalConnector current) throws Exception {
        while (running) {
            Message message = current.getWithoutAck(batchSize);
            long batchId = message.getId();
            if (batchId == -1 || message.getEntries() == null || message.getEntries().isEmpty()) {
                sleep(intervalMs);
                continue;
            }
            try {
                forwardEntries(message);
                current.ack(batchId);
            } catch (Exception e) {
                try {
                    current.rollback(batchId);
                } catch (Exception rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw e;
            }
        }
    }

    void forwardEntries(Message message) throws Exception {
        for (CanalEntry.Entry entry : message.getEntries()) {
            if (entry.getEntryType() != CanalEntry.EntryType.ROWDATA) continue;
            CanalEntry.RowChange rowChange = CanalEntry.RowChange.parseFrom(entry.getStoreValue());
            CanalEntry.EventType eventType = rowChange.getEventType();
            if (eventType != CanalEntry.EventType.INSERT && eventType != CanalEntry.EventType.UPDATE) continue;

            ArrayNode dataArray = objectMapper.createArrayNode();
            for (CanalEntry.RowData rowData : rowChange.getRowDatasList()) {
                for (CanalEntry.Column col : rowData.getAfterColumnsList()) {
                    if ("payload".equalsIgnoreCase(col.getName())) {
                        ObjectNode rowNode = objectMapper.createObjectNode();
                        rowNode.put("payload", col.getValue());
                        dataArray.add(rowNode);
                        break;
                    }
                }
            }
            if (dataArray.isEmpty()) continue;

            String table = entry.getHeader().getTableName();
            ObjectNode msgNode = objectMapper.createObjectNode();
            msgNode.put("table", table);
            msgNode.put("type", eventType == CanalEntry.EventType.INSERT ? "INSERT" : "UPDATE");
            msgNode.set("data", dataArray);
            String json = objectMapper.writeValueAsString(msgNode);
            kafka.send(OutboxTopics.CANAL_OUTBOX, table, json)
                    .get(kafkaSendTimeoutSeconds, TimeUnit.SECONDS);
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(Math.max(1L, millis));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (running) throw new IllegalStateException("Canal bridge thread interrupted", e);
        }
    }

    private void disconnect(CanalConnector current) {
        if (current == null) return;
        try {
            current.disconnect();
            log.info("Canal disconnected: dest={}", destination);
        } catch (Exception ex) {
            log.warn("Canal disconnect failed: dest={} err={}", destination, ex.getMessage());
        }
    }

    /**
     * 停止桥接器。
     */
    @Override
    public void stop() {
        running = false;
        disconnect(connector);
    }

    /**
     * 是否处于运行状态。
     * @return 运行状态
     */
    @Override
    public boolean isRunning() {
        return running;
    }
}
