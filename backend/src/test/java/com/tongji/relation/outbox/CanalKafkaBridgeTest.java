package com.tongji.relation.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.alibaba.otter.canal.protocol.CanalEntry;
import com.alibaba.otter.canal.protocol.Message;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.TaskExecutor;
import org.springframework.kafka.core.KafkaTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class CanalKafkaBridgeTest {

    @Test
    void disabledBridgeDoesNotStartBackgroundLoop() {
        @SuppressWarnings("unchecked") KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        TaskExecutor executor = mock(TaskExecutor.class);
        CanalKafkaBridge bridge = new CanalKafkaBridge(
                kafka, new ObjectMapper(), executor, false,
                "localhost", 11111, "example", "", "", ".*\\.outbox",
                100, 100L, 1000L, 5L);

        bridge.start();

        assertFalse(bridge.isRunning());
        verifyNoInteractions(executor);
    }

    @Test
    void brokerFailureEscapesBeforeCanalBatchCanBeAcknowledged() {
        @SuppressWarnings("unchecked") KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(anyString(), anyString(), anyString()))
                .thenReturn(java.util.concurrent.CompletableFuture.failedFuture(
                        new IllegalStateException("broker unavailable")));
        CanalKafkaBridge bridge = new CanalKafkaBridge(
                kafka, new ObjectMapper(), mock(TaskExecutor.class), true,
                "localhost", 11111, "example", "", "", ".*\\.outbox",
                100, 100L, 1000L, 5L);
        CanalEntry.Column payload = CanalEntry.Column.newBuilder()
                .setName("payload").setValue("{\"x\":1}").build();
        CanalEntry.RowData row = CanalEntry.RowData.newBuilder()
                .addAfterColumns(payload).build();
        CanalEntry.RowChange change = CanalEntry.RowChange.newBuilder()
                .setEventType(CanalEntry.EventType.INSERT).addRowDatas(row).build();
        CanalEntry.Entry entry = CanalEntry.Entry.newBuilder()
                .setEntryType(CanalEntry.EntryType.ROWDATA)
                .setHeader(CanalEntry.Header.newBuilder().setTableName("outbox"))
                .setStoreValue(change.toByteString())
                .build();

        assertThrows(Exception.class, () -> bridge.forwardEntries(new Message(1L, java.util.List.of(entry))));
    }
}
