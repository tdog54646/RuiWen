package com.tongji.qa.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tongji.auth.exception.BusinessException;
import com.tongji.auth.exception.ErrorCode;
import com.tongji.knowpost.id.SnowflakeIdGenerator;
import com.tongji.llm.rag.model.RetrievalChunk;
import com.tongji.llm.rag.search.HybridSearchService;
import com.tongji.qa.api.dto.ConversationResponse;
import com.tongji.qa.api.dto.MessageResponse;
import com.tongji.qa.api.dto.QaChatRequest;
import com.tongji.qa.config.QaProperties;
import com.tongji.qa.event.QaEventProducer;
import com.tongji.qa.mapper.QaConversationMapper;
import com.tongji.qa.mapper.QaMessageMapper;
import com.tongji.qa.mapper.UserMemoryMapper;
import com.tongji.qa.model.QaConversation;
import com.tongji.qa.model.QaMessage;
import com.tongji.qa.model.UserMemory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 多轮问答编排核心。
 * <p>串联：会话解析/创建 → 用户提问持久化 → 历史窗口加载 → 用户记忆加载 → RAG 检索 →
 * Prompt 组装 → ChatClient 流式生成 → SSE 事件流。
 *
 * <h3>中断彻底性</h3>
 * 返回 {@link Flux}<{@link ServerSentEvent}>，客户端断开时 Reactor cancel 信号会传播到
 * {@code chatClient.stream()} 的上游 Flux，底层 WebClient 关闭到 DeepSeek 的连接，停止生成；
 * {@code doOnCancel} 中将已生成片段以 status=interrupted 异步落库。
 *
 * <h3>异步落库</h3>
 * assistant 回答与消息计数更新在 {@code doOnComplete/doOnCancel} 中丢到 boundedElastic 线程池，
 * 不阻塞 SSE 响应流；达到阈值时发布 Kafka 事件触发记忆自动更新。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QaChatService {

    private final QaConversationMapper conversationMapper;
    private final QaMessageMapper messageMapper;
    private final UserMemoryMapper memoryMapper;
    private final HybridSearchService hybridSearchService;
    private final QaPromptAssembler promptAssembler;
    private final ChatClient chatClient;
    private final SnowflakeIdGenerator idGen;
    private final QaProperties properties;
    private final QaEventProducer qaEventProducer;
    private final ObjectMapper objectMapper;

    // -------------------------------------------------------------------------
    // 多轮流式问答
    // -------------------------------------------------------------------------

    /**
     * 多轮流式问答（SSE）。
     *
     * @param userId 当前用户 ID
     * @param req    请求体
     * @return SSE 事件流，事件 data 为 JSON：meta / delta / done / error
     */
    public Flux<ServerSentEvent<String>> streamChat(long userId, QaChatRequest req) {
        String question = req.question() == null ? "" : req.question().trim();
        if (question.isEmpty()) {
            return Flux.just(sse(errorNode("问题不能为空")));
        }

        // 1) 解析或创建会话
        long conversationId;
        if (req.conversationId() != null && !req.conversationId().isBlank()) {
            long cid = parseId(req.conversationId());
            QaConversation owned = conversationMapper.findOwnedById(cid, userId);
            if (owned == null) {
                return Flux.just(sse(errorNode("会话不存在或无访问权限")));
            }
            conversationId = cid;
        } else {
            conversationId = createConversationId(userId, question);
        }

        int topK = (req.topK() != null && req.topK() > 0) ? req.topK() : properties.getChat().getDefaultTopK();
        int maxTokens = (req.maxTokens() != null && req.maxTokens() > 0) ? req.maxTokens() : properties.getChat().getDefaultMaxTokens();

        // 2) 持久化用户提问
        long userMessageId = idGen.nextId();
        Instant now = Instant.now();
        messageMapper.insert(QaMessage.builder()
                .id(userMessageId).conversationId(conversationId).userId(userId)
                .role("user").content(question).status("completed").createdAt(now).build());

        // 3) 历史窗口（最近 N 轮，排除刚插入的当轮 user，并反转为正序）
        int windowMsgs = properties.getHistoryWindow() * 2;
        List<QaMessage> recent = messageMapper.listRecentByConversation(conversationId, windowMsgs + 1);
        List<QaMessage> history = new ArrayList<>();
        for (QaMessage m : recent) {
            if (m.getId() != null && m.getId() == userMessageId) continue;
            history.add(m);
        }
        Collections.reverse(history);

        // 4) 用户记忆
        List<UserMemory> memories = safeList(memoryMapper.listEnabledByUser(userId));

        // 5) RAG 检索（全局；失败则降级为无上下文继续对话）
        List<RetrievalChunk> chunks;
        try {
            chunks = hybridSearchService.hybridSearch(question, topK);
        } catch (Exception e) {
            log.warn("RAG retrieval failed, fallback to no context: {}", e.getMessage());
            chunks = List.of();
        }

        // 6) 组装消息列表
        List<Message> messages = promptAssembler.assemble(question, chunks, history, memories);

        // 预生成 assistant 消息 ID（用于 meta 事件与异步落库）
        long assistantMessageId = idGen.nextId();
        String metaJson = toJson(metaNode(conversationId, userMessageId, assistantMessageId));

        // 7) 流式生成
        List<String> fragments = new ArrayList<>();
        Flux<ServerSentEvent<String>> llmEvents = chatClient.prompt()
                .messages(messages)
                .options(DeepSeekChatOptions.builder()
                        .model("deepseek-v4-flash")
                        .temperature(0.4)
                        .maxTokens(maxTokens)
                        .build())
                .stream()
                .content()
                .doOnNext(c -> {
                    if (c != null && !c.isEmpty()) {
                        fragments.add(c);
                    }
                })
                .map(c -> sse(deltaNode(c)))
                .doOnError(e -> log.error("QA stream error (conv={}): {}", conversationId, e.getMessage()))
                .doOnComplete(() -> persistAnswer(userId, conversationId, assistantMessageId, fragments, false))
                .doOnCancel(() -> {
                    log.info("QA stream cancelled by client (conv={})", conversationId);
                    persistAnswer(userId, conversationId, assistantMessageId, fragments, true);
                });

        // meta 首发 → LLM 增量 → done
        return Flux.just(sse(metaJson))
                .concatWith(llmEvents)
                .concatWith(Flux.defer(() -> Flux.just(sse(doneNode()))));
    }

    // -------------------------------------------------------------------------
    // 会话管理
    // -------------------------------------------------------------------------

    @Transactional
    public ConversationResponse createConversation(long userId, String title) {
        long id = createConversationId(userId, null);
        return ConversationResponse.of(conversationMapper.findById(id));
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> listConversations(long userId, int limit, int offset) {
        return conversationMapper.listByUser(userId, Math.min(limit, 100), Math.max(offset, 0)).stream()
                .map(ConversationResponse::of).toList();
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> listMessages(long userId, String conversationId) {
        long cid = parseId(conversationId);
        QaConversation owned = conversationMapper.findOwnedById(cid, userId);
        if (owned == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "会话不存在或无访问权限");
        }
        return messageMapper.listByConversation(cid, 500, 0).stream()
                .map(MessageResponse::of).toList();
    }

    @Transactional
    public ConversationResponse renameConversation(long userId, String conversationId, String title) {
        if (title == null || title.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "标题不能为空");
        }
        long cid = parseId(conversationId);
        int n = conversationMapper.updateTitle(cid, userId, title.trim());
        if (n == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "会话不存在或无访问权限");
        }
        return ConversationResponse.of(conversationMapper.findById(cid));
    }

    @Transactional
    public void deleteConversation(long userId, String conversationId) {
        long cid = parseId(conversationId);
        int n = conversationMapper.softDelete(cid, userId);
        if (n == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "会话不存在或无访问权限");
        }
    }

    // -------------------------------------------------------------------------
    // 内部工具
    // -------------------------------------------------------------------------

    /** 创建会话并落库，返回会话 ID。标题取问题前 16 字（无问题时用默认）。 */
    private long createConversationId(long userId, String firstQuestion) {
        long id = idGen.nextId();
        Instant now = Instant.now();
        String title;
        if (firstQuestion != null && !firstQuestion.isBlank()) {
            title = firstQuestion.length() <= 16 ? firstQuestion : firstQuestion.substring(0, 16);
        } else {
            title = "新对话";
        }
        conversationMapper.insert(QaConversation.builder()
                .id(id).userId(userId).title(title)
                .messageCount(0).lastMessageAt(null).deleted(false)
                .createdAt(now).updatedAt(now).build());
        return id;
    }

    /** 异步持久化 assistant 回答、刷新会话计数，并在达阈值时触发记忆更新。 */
    private void persistAnswer(long userId, long conversationId, long assistantMessageId,
                               List<String> fragments, boolean interrupted) {
        Mono.fromRunnable(() -> {
            try {
                String answer = String.join("", fragments);
                Instant now = Instant.now();
                if (!answer.isBlank()) {
                    messageMapper.insert(QaMessage.builder()
                            .id(assistantMessageId).conversationId(conversationId).userId(userId)
                            .role("assistant").content(answer)
                            .status(interrupted ? "interrupted" : "completed")
                            .createdAt(now).build());
                }
                conversationMapper.touchAfterMessage(conversationId, now);
                QaConversation conv = conversationMapper.findById(conversationId);
                int threshold = properties.getMemory().getUpdateThreshold();
                if (conv != null && conv.getMessageCount() != null
                        && conv.getMessageCount() > 0
                        && threshold > 0
                        && conv.getMessageCount() % threshold == 0) {
                    qaEventProducer.publishMemoryUpdate(userId);
                }
            } catch (Exception e) {
                log.error("Persist QA answer failed (conv={}): {}", conversationId, e.getMessage());
            }
        }).subscribeOn(Schedulers.boundedElastic()).subscribe();
    }

    private long parseId(String id) {
        try {
            return Long.parseLong(id.trim());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "非法的 ID: " + id);
        }
    }

    private static <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }

    // -------------------------------------------------------------------------
    // SSE 事件 JSON 构造
    // -------------------------------------------------------------------------

    private ServerSentEvent<String> sse(ObjectNode node) {
        return ServerSentEvent.<String>builder().data(toJson(node)).build();
    }

    private ServerSentEvent<String> sse(String json) {
        return ServerSentEvent.<String>builder().data(json).build();
    }

    private String toJson(ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            return "{\"type\":\"error\",\"message\":\"sse serialize failed\"}";
        }
    }

    private ObjectNode metaNode(long conversationId, long userMessageId, long assistantMessageId) {
        return objectMapper.createObjectNode()
                .put("type", "meta")
                .put("conversationId", String.valueOf(conversationId))
                .put("userMessageId", String.valueOf(userMessageId))
                .put("assistantMessageId", String.valueOf(assistantMessageId));
    }

    private ObjectNode deltaNode(String content) {
        return objectMapper.createObjectNode()
                .put("type", "delta")
                .put("content", content);
    }

    private ObjectNode doneNode() {
        return objectMapper.createObjectNode().put("type", "done");
    }

    private ObjectNode errorNode(String message) {
        return objectMapper.createObjectNode()
                .put("type", "error")
                .put("message", message);
    }
}
