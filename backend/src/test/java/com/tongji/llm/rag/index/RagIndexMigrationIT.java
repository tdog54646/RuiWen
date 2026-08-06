package com.tongji.llm.rag.index;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 显式执行的远程版本化索引迁移；默认测试不会触发外部写操作。 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = {
                "spring.kafka.listener.auto-startup=false",
                "canal.enabled=false",
                "counter.rebuild.enabled=false",
                "spring.main.keep-alive=false"
        })
@EnabledIfEnvironmentVariable(named = "RUN_RAG_INDEX_MIGRATION", matches = "(?i)true")
class RagIndexMigrationIT {

    @Autowired private RagIndexManager indexManager;

    @Test
    void migrateAndVerifyCounts() {
        RagIndexManager.MigrationResult result = indexManager.migrateToCurrentVersion();
        assertEquals(result.sourceDocuments(), result.targetDocuments());
        assertEquals(indexManager.targetIndex(), result.targetIndex());
    }
}
