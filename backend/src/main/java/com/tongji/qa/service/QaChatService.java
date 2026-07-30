package com.tongji.qa.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tongji.auth.exception.BusinessException;
import com.tongji.auth.exception.ErrorCode;
import com.tongji.knowpost.id.SnowflakeIdGenerator;
import com.tongji.llm.rag.RetrievalContext;
import com.tongji.llm.rag.model.RetrievalChunk;
import com.tongji.llm.rag.search.HybridSearchService;
import com.tongji.llm.vision.ImageRecognitionService;
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
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPost;
import com.tongji.knowpost.service.KnowPostService;
import com.tongji.llm.service.PostDraftService;
import com.tongji.storage.OssStorageService;

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
    private final PostDraftService postDraftService;
    private final KnowPostService knowPostService;
    private final KnowPostMapper knowPostMapper;
    private final ImageRecognitionService imageRecognitionService;
    private final OssStorageService ossStorageService;

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

        // 2) 持久化用户提问（含附带的图片 URL，落库以便历史回看）
        long userMessageId = idGen.nextId();
        Instant now = Instant.now();
        // 过滤空值、去空白、至多 4 张
        List<String> storedImageUrls = req.imageUrls() == null ? List.of()
                : req.imageUrls().stream()
                        .filter(s -> s != null && !s.isBlank())
                        .map(String::trim)
                        .map(url -> ossStorageService.validateChatImageUpload(url, userId))
                        .limit(4)
                        .toList();
        List<String> imageUrls = storedImageUrls.stream()
                .map(url -> ossStorageService.privateChatImageUrl(url, userId))
                .toList();
        boolean hasImage = !imageUrls.isEmpty();
        messageMapper.insert(QaMessage.builder()
                .id(userMessageId).conversationId(conversationId).userId(userId)
                .role("user").content(question).imageUrls(hasImage ? storedImageUrls : null)
                .status("completed").createdAt(now).build());

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

        // 5) RAG 上下文（按用户隔离过滤）——仅当模型调用工具时才真正检索，避免每次提问都检索
        RetrievalContext ctx = RetrievalContext.of(userId, RetrievalContext.parseScope(req.scope()));
        List<SourceRef> sourcesAcc = Collections.synchronizedList(new ArrayList<>());
        FunctionToolCallback kbTool = buildKnowledgeBaseTool(question, topK, ctx, sourcesAcc);
        AtomicReference<DraftPayload> draftAcc = new AtomicReference<>();
        FunctionToolCallback draftTool = buildDraftPostTool(userId, draftAcc);
        FunctionToolCallback publishTool = buildPublishPostTool(userId);

        // 6) 组装消息列表（不再预注入 RAG 上下文，由工具按需检索）
        // 用户附带图片时：额外注册图片识别工具（QVQ），并在喂给 LLM 的文本中提示图片存在
        String llmQuestion = hasImage
                ? question + "\n\n[系统提示：用户本次附带了图片。如需了解图片内容，请调用 recognize_image 工具识别后再作答，不要声称无法查看图片。]"
                : question;
        List<Message> messages = promptAssembler.assemble(llmQuestion, List.of(), history, memories);

        List<FunctionToolCallback> tools = new ArrayList<>(List.of(kbTool, draftTool, publishTool));
        if (hasImage) {
            tools.add(buildRecognizeImageTool(imageUrls, question));
        }

        // 预生成 assistant 消息 ID（用于 meta 事件与异步落库）
        long assistantMessageId = idGen.nextId();
        String metaJson = toJson(metaNode(conversationId, userMessageId, assistantMessageId));

        // 7) 流式生成
        List<String> fragments = new ArrayList<>();
        Flux<ServerSentEvent<String>> llmEvents = chatClient.prompt()
                .messages(messages)
                .toolCallbacks(tools.toArray(new FunctionToolCallback[0]))
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

        // meta 首发 → LLM 增量 → sources（命中知识库时）→ done
        return Flux.just(sse(metaJson))
                .concatWith(llmEvents)
                .concatWith(Flux.defer(() -> {
                    List<ServerSentEvent<String>> tail = new ArrayList<>();
                    if (!sourcesAcc.isEmpty()) {
                        tail.add(sse(toJson(sourcesNode(sourcesAcc))));
                    }
                    DraftPayload draft = draftAcc.get();
                    if (draft != null) {
                        tail.add(sse(toJson(draftNode(draft))));
                    }
                    tail.add(sse(doneNode()));
                    return Flux.fromIterable(tail);
                }));
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
                .peek(message -> {
                    if (message.getImageUrls() != null) {
                        message.setImageUrls(message.getImageUrls().stream()
                                .map(url -> ossStorageService.privateChatImageUrl(url, userId))
                                .toList());
                    }
                })
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
    // 知识库工具（按需 RAG）
    // -------------------------------------------------------------------------

    /**
     * 构建「知识库检索」工具：模型按需调用，仅在命中时检索并记录来源。
     * 闭包捕获当前问题的检索参数与来源累加器；检索异常时降级为无上下文，不中断对话。
     */
    private FunctionToolCallback buildKnowledgeBaseTool(String question, int topK,
                                                        RetrievalContext ctx, List<SourceRef> sourcesAcc) {
        return FunctionToolCallback.builder("search_knowledge_base", (KbSearchInput in) -> {
                    String q = (in != null && in.query() != null && !in.query().isBlank())
                            ? in.query() : question;
                    try {
                        List<RetrievalChunk> chunks = hybridSearchService.hybridSearch(q, topK, ctx);
                        for (RetrievalChunk c : chunks) {
                            sourcesAcc.add(new SourceRef(c.getPostId(), c.getTitle(), c.getRrfScore()));
                        }
                        return formatChunksForLlm(chunks);
                    } catch (Exception e) {
                        log.warn("KB tool retrieval failed: {}", e.getMessage());
                        return "（知识库检索失败，请结合常识直接作答，不要编造来源）";
                    }
                })
                .description("当用户提问涉及知识库内容时，检索相关文章片段。闲聊、自我介绍、通用常识问题不要调用。")
                .inputType(KbSearchInput.class)
                .build();
    }

    /**
     * 构建「录入文章」工具：收齐 title/topic 后由后端生成正文并落库为草稿，草稿摘要写入 draftAcc。
     */
    private FunctionToolCallback buildDraftPostTool(long userId, AtomicReference<DraftPayload> draftAcc) {
        return FunctionToolCallback.builder("draft_post", (DraftPostInput in) -> {
                    String title = in.title();
                    String topic = in.topic();
                    if (title == null || title.isBlank() || topic == null || topic.isBlank()) {
                        return "缺少必填字段 title 或 topic，请先向用户询问后再调用本工具。";
                    }
                    try {
                        List<String> tags = in.tags() == null ? List.of() : in.tags();
                        PostDraftService.DraftResult r = postDraftService.createDraft(userId, title.trim(), topic.trim(), tags);
                        draftAcc.set(new DraftPayload(r.postId(), r.title(), r.tags(), r.preview()));
                        return "草稿已生成（postId=" + r.postId() + "）。请如实告知用户：草稿已准备好；"
                                + "确认无误请回复「发布」，需要修改请说明。";
                    } catch (Exception e) {
                        log.warn("draft_post failed (user={})", userId, e);
                        return "草稿生成失败，请稍后重试。";
                    }
                })
                .description("录入一篇新文章并生成草稿。调用前必须先从用户处取得 title（标题）与 topic（主题要点）；"
                        + "正文由本工具自动生成。tags 可选（标签数组，可留空）。")
                .inputType(DraftPostInput.class)
                .build();
    }

    /**
     * 构建「发布文章」工具：发布当前用户的草稿。仅在用户明确表达发布意图时由模型调用。
     * <p>不强依赖 LLM 传准 18 位 postId（雪花 ID 经 LLM 传递易出错）：传入且归属本人+草稿态则用之，
     * 否则回退到该用户最近一篇草稿。
     */
    private FunctionToolCallback buildPublishPostTool(long userId) {
        return FunctionToolCallback.builder("publish_post", (PublishPostInput in) -> {
                    try {
                        Long targetId = resolvePublishTarget(userId, in.postId());
                        if (targetId == null) {
                            return "未找到可发布的草稿，请先调用 draft_post 生成草稿后再发布。";
                        }
                        knowPostService.publish(userId, targetId);
                        return "文章已发布（postId=" + targetId + "）。";
                    } catch (Exception e) {
                        log.warn("publish_post failed (user={})", userId, e);
                        return "发布失败，请稍后重试。";
                    }
                })
                .description("发布文章。仅在用户明确表达发布意图（如「发布」「确认发布」「可以发布了」）时调用。"
                        + "postId 可选——传入则发布该篇，未传或无效则发布用户最近一篇草稿。用户若未明确确认，不要调用。")
                .inputType(PublishPostInput.class)
                .build();
    }

    /**
     * 构建「图片识别」工具：用户本次附带图片时注册，调用 QVQ 视觉模型返回图片内容描述。
     * <p>图片 URL 列表经闭包捕获（不让 LLM 复述 URL，避免传错）；LLM 可选传入 query 描述关注点，
     * 缺省时用本次提问作为识别意图。识别失败在服务内部降级为提示串，不抛异常。
     */
    private FunctionToolCallback buildRecognizeImageTool(List<String> imageUrls, String question) {
        return FunctionToolCallback.builder("recognize_image", (RecognizeImageInput in) -> {
                    String focus = (in != null && in.query() != null && !in.query().isBlank())
                            ? in.query() : question;
                    return imageRecognitionService.recognize(imageUrls, focus);
                })
                .description("识别用户本次附带的图片（可能多张）并返回内容描述。仅当用户本次消息附带图片时调用；"
                        + "query 可选，传入用户对图片的具体问题或关注点，缺省时整体描述图片。"
                        + "获取描述后据其作答，不要声称无法查看图片。")
                .inputType(RecognizeImageInput.class)
                .build();
    }

    /** 解析发布目标：优先用 LLM 传入的 postId（需归属本人且为 draft），否则回退到本人最近草稿。 */
    private Long resolvePublishTarget(long userId, String postIdStr) {
        if (postIdStr != null && !postIdStr.isBlank()) {
            try {
                long pid = Long.parseLong(postIdStr.trim());
                if (isPublishableDraft(userId, pid)) {
                    return pid;
                }
            } catch (NumberFormatException ignore) {
                // 传入非合法数字，回退到最近草稿
            }
        }
        return knowPostMapper.findLatestDraftId(userId);
    }

    private boolean isPublishableDraft(long userId, long postId) {
        KnowPost post = knowPostMapper.findById(postId);
        return post != null && post.getCreatorId() != null
                && post.getCreatorId() == userId && "draft".equals(post.getStatus());
    }

    /** 将检索片段格式化为喂给 LLM 的纯文本（标题分隔 + 正文）。 */
    private String formatChunksForLlm(List<RetrievalChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "（未检索到相关内容，请结合常识作答，不要编造来源）";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < chunks.size(); i++) {
            RetrievalChunk c = chunks.get(i);
            String label = (c.getTitle() != null && !c.getTitle().isBlank())
                    ? c.getTitle() : ("来源 " + (i + 1));
            sb.append("--- [").append(label).append("] ---\n")
              .append(c.getContent() == null ? "" : c.getContent()).append("\n\n");
        }
        return sb.toString();
    }

    /** 构造 sources 事件：按 postId 去重（保留最高分）、按分降序取 top 3。 */
    private ObjectNode sourcesNode(List<SourceRef> sources) {
        List<SourceRef> snapshot;
        synchronized (sources) {
            snapshot = new ArrayList<>(sources);
        }
        Map<String, SourceRef> best = new LinkedHashMap<>();
        for (SourceRef s : snapshot) {
            if (s.postId() == null || s.postId().isBlank() || "unknown".equals(s.postId())) continue;
            best.merge(s.postId(), s, (a, b) -> a.score() >= b.score() ? a : b);
        }
        List<SourceRef> top = best.values().stream()
                .sorted((a, b) -> Double.compare(b.score(), a.score()))
                .limit(3)
                .toList();
        ArrayNode arr = objectMapper.createArrayNode();
        for (SourceRef s : top) {
            arr.add(objectMapper.createObjectNode()
                    .put("postId", s.postId())
                    .put("title", s.title() == null ? "" : s.title()));
        }
        return objectMapper.createObjectNode().put("type", "sources").set("items", arr);
    }

    /** 构造 draft 事件：下发草稿摘要供前端渲染草稿卡片。 */
    private ObjectNode draftNode(DraftPayload draft) {
        ObjectNode node = objectMapper.createObjectNode().put("type", "draft")
                .put("postId", draft.postId())
                .put("title", draft.title() == null ? "" : draft.title())
                .put("preview", draft.preview() == null ? "" : draft.preview());
        ArrayNode tags = node.putArray("tags");
        if (draft.tags() != null) {
            for (String t : draft.tags()) {
                tags.add(t == null ? "" : t);
            }
        }
        return node;
    }

    /** 工具入参：检索关键词。 */
    private record KbSearchInput(String query) {}

    /** 工具入参：录入文章。tags 可选。 */
    private record DraftPostInput(String title, String topic,
                                  @org.springframework.ai.tool.annotation.ToolParam(required = false) List<String> tags) {}

    /** 工具入参：发布文章。postId 可选。 */
    private record PublishPostInput(@org.springframework.ai.tool.annotation.ToolParam(required = false) String postId) {}

    /** 工具入参：图片识别。query 可选，用户对图片的关注点，缺省时整体描述。 */
    private record RecognizeImageInput(@org.springframework.ai.tool.annotation.ToolParam(required = false) String query) {}

    /** 草稿摘要，经 SSE draft 事件下发前端。 */
    private record DraftPayload(String postId, String title, List<String> tags, String preview) {}

    /** 命中来源（含分数），用于去重排序；下发给前端时只取 postId/title。 */
    private record SourceRef(String postId, String title, double score) {}

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
