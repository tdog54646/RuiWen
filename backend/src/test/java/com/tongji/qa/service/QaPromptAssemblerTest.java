package com.tongji.qa.service;

import com.tongji.llm.rag.model.RetrievalChunk;
import com.tongji.qa.config.QaProperties;
import com.tongji.qa.model.QaMessage;
import com.tongji.qa.model.UserMemory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证多轮 Prompt 组装：System 含记忆、历史映射为 User/Assistant、当轮含 RAG 上下文。
 */
class QaPromptAssemblerTest {

    private QaPromptAssembler assembler;

    @BeforeEach
    void setUp() throws Exception {
        assembler = new QaPromptAssembler(new QaProperties());
        assembler.init(); // 加载 classpath:qa-prompts/*.md
    }

    @Test
    void assemble_startsWithSystemMessageContainingMemories() {
        UserMemory mem = UserMemory.builder().category("专业领域").content("Java 后端").build();
        List<Message> msgs = assembler.assemble("什么是 JVM?", List.of(), List.of(), List.of(mem));

        assertEquals(2, msgs.size());
        assertInstanceOf(SystemMessage.class, msgs.get(0));
        String sys = msgs.get(0).getText();
        assertTrue(sys.contains("专业领域"), "system prompt should contain memory category");
        assertTrue(sys.contains("Java 后端"), "system prompt should contain memory content");
    }

    @Test
    void assemble_noMemories_containsPlaceholder() {
        List<Message> msgs = assembler.assemble("你好", List.of(), List.of(), List.of());
        assertTrue(msgs.get(0).getText().contains("暂无"));
    }

    @Test
    void assemble_historyMappedToUserAndAssistantMessages() {
        QaMessage h1 = QaMessage.builder().role("user").content("什么是 RAG").build();
        QaMessage h2 = QaMessage.builder().role("assistant").content("RAG 是检索增强生成").build();

        List<Message> msgs = assembler.assemble("详细说说", List.of(), List.of(h1, h2), List.of());

        // System + user(history) + assistant(history) + user(current) = 4
        assertEquals(4, msgs.size());
        assertInstanceOf(UserMessage.class, msgs.get(1));
        assertTrue(msgs.get(1).getText().contains("什么是 RAG"));
        assertInstanceOf(AssistantMessage.class, msgs.get(2));
        assertTrue(msgs.get(2).getText().contains("检索增强生成"));
        assertInstanceOf(UserMessage.class, msgs.get(3));
    }

    @Test
    void assemble_withChunks_embedsContext() {
        RetrievalChunk chunk = RetrievalChunk.of("1#0", "1", "RAG 是一种技术", "RAG 详解", 0);
        List<Message> msgs = assembler.assemble("什么是 RAG", List.of(chunk), List.of(), List.of());

        String current = msgs.get(msgs.size() - 1).getText();
        assertTrue(current.contains("知识库上下文"));
        assertTrue(current.contains("RAG 详解"));
        assertTrue(current.contains("RAG 是一种技术"));
        assertTrue(current.contains("什么是 RAG"));
    }

    @Test
    void assemble_withoutChunks_userIsBareQuestion() {
        List<Message> msgs = assembler.assemble("你好", List.of(), List.of(), List.of());
        assertEquals("你好", msgs.get(msgs.size() - 1).getText());
    }

    @Test
    void systemPrompt_requiresUiConfirmationAndNeverAuthorizesModelPublishing() {
        List<Message> msgs = assembler.assemble("发布刚才的文章", List.of(), List.of(), List.of());
        String system = msgs.get(0).getText();

        assertTrue(system.contains("确认发布"), "system prompt should direct the user to the confirmation button");
        assertTrue(system.contains("没有发布文章的工具"), "model must be told it cannot publish");
        assertFalse(system.contains("`publish_post`"), "publish tool must not be advertised to the model");
    }
}
