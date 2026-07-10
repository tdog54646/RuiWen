package com.tongji.qa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.knowpost.id.SnowflakeIdGenerator;
import com.tongji.qa.config.QaProperties;
import com.tongji.qa.mapper.QaMessageMapper;
import com.tongji.qa.mapper.UserMemoryMapper;
import com.tongji.qa.model.UserMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * 验证记忆总结 JSON 解析的容错性（代码块包裹、前后缀噪声、空数组、缺字段）。
 */
class QaMemoryServiceTest {

    private QaMemoryService service;

    @BeforeEach
    void setUp() {
        service = new QaMemoryService(
                mock(UserMemoryMapper.class),
                mock(QaMessageMapper.class),
                mock(QaPromptAssembler.class),
                mock(ChatClient.class),
                new SnowflakeIdGenerator(),
                new QaProperties(),
                new ObjectMapper()
        );
    }

    @Test
    void parseItems_plainJson() {
        String raw = "[{\"category\":\"专业领域\",\"content\":\"Java\"},{\"category\":\"偏好\",\"content\":\"简洁\"}]";
        List<UserMemory> items = service.parseItems(1L, raw);

        assertEquals(2, items.size());
        assertEquals("专业领域", items.get(0).getCategory());
        assertEquals("Java", items.get(0).getContent());
        assertEquals("auto", items.get(0).getSource());
        assertEquals(1L, items.get(0).getUserId());
    }

    @Test
    void parseItems_codeBlockWrapped() {
        String raw = "```json\n[{\"category\":\"A\",\"content\":\"B\"}]\n```";
        List<UserMemory> items = service.parseItems(1L, raw);

        assertEquals(1, items.size());
        assertEquals("A", items.get(0).getCategory());
        assertEquals("B", items.get(0).getContent());
    }

    @Test
    void parseItems_toleratesNoisePrefixSuffix() {
        String raw = "好的，以下是分析结果：\n[{\"category\":\"A\",\"content\":\"B\"}]\n以上仅供参考。";
        assertEquals(1, service.parseItems(1L, raw).size());
    }

    @Test
    void parseItems_emptyArray() {
        assertEquals(0, service.parseItems(1L, "[]").size());
    }

    @Test
    void parseItems_invalidOrNullOrBlank() {
        assertEquals(0, service.parseItems(1L, "not json").size());
        assertEquals(0, service.parseItems(1L, null).size());
        assertEquals(0, service.parseItems(1L, "   ").size());
    }

    @Test
    void parseItems_skipsIncompleteEntries() {
        String raw = "[{\"category\":\"\",\"content\":\"B\"},{\"category\":\"A\",\"content\":\"\"},{\"category\":\"C\",\"content\":\"D\"}]";
        List<UserMemory> items = service.parseItems(1L, raw);

        assertEquals(1, items.size());
        assertEquals("C", items.get(0).getCategory());
    }

    @Test
    void parseItems_truncatesOverlongContent() {
        String longContent = "x".repeat(600);
        String raw = "[{\"category\":\"A\",\"content\":\"" + longContent + "\"}]";
        List<UserMemory> items = service.parseItems(1L, raw);

        assertEquals(1, items.size());
        assertEquals(500, items.get(0).getContent().length());
    }
}
