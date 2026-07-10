package com.tongji.qa.api;

import com.tongji.auth.token.JwtService;
import com.tongji.qa.api.dto.ConversationCreateRequest;
import com.tongji.qa.api.dto.ConversationRenameRequest;
import com.tongji.qa.api.dto.ConversationResponse;
import com.tongji.qa.api.dto.MessageResponse;
import com.tongji.qa.api.dto.QaChatRequest;
import com.tongji.qa.service.QaChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * 多轮问答 REST 接口。
 * <p>提供多轮流式问答（SSE）与会话管理（新建/列表/历史/重命名/删除）。
 * 全部接口默认需鉴权（{@code /api/qa/**} 不在 SecurityConfig 的 permitAll 列表中）。
 */
@Slf4j
@RestController
@RequestMapping("/api/qa")
@RequiredArgsConstructor
public class QaChatController {

    private final QaChatService qaChatService;
    private final JwtService jwtService;

    /**
     * 多轮流式问答（SSE）。
     * <p>conversationId 为空时自动新建会话，并通过首个 meta 事件回传 conversationId。
     */
    @PostMapping(value = "/chat",
            consumes = MediaType.APPLICATION_JSON_VALUE,
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chat(@AuthenticationPrincipal Jwt jwt,
                                              @RequestBody QaChatRequest request) {
        long userId = jwtService.extractUserId(jwt);
        return qaChatService.streamChat(userId, request);
    }

    /** 新建空会话。 */
    @PostMapping("/conversations")
    public ConversationResponse createConversation(@AuthenticationPrincipal Jwt jwt,
                                                   @RequestBody(required = false) ConversationCreateRequest request) {
        long userId = jwtService.extractUserId(jwt);
        String title = request == null ? null : request.title();
        return qaChatService.createConversation(userId, title);
    }

    /** 当前用户会话列表（按最后活跃时间倒序）。 */
    @GetMapping("/conversations")
    public List<ConversationResponse> listConversations(@AuthenticationPrincipal Jwt jwt,
                                                        @RequestParam(value = "limit", defaultValue = "50") int limit,
                                                        @RequestParam(value = "offset", defaultValue = "0") int offset) {
        long userId = jwtService.extractUserId(jwt);
        return qaChatService.listConversations(userId, limit, offset);
    }

    /** 某会话历史消息（正序）。 */
    @GetMapping("/conversations/{id}/messages")
    public List<MessageResponse> listMessages(@AuthenticationPrincipal Jwt jwt,
                                              @PathVariable("id") String id) {
        long userId = jwtService.extractUserId(jwt);
        return qaChatService.listMessages(userId, id);
    }

    /** 重命名会话。 */
    @PatchMapping("/conversations/{id}")
    public ConversationResponse renameConversation(@AuthenticationPrincipal Jwt jwt,
                                                   @PathVariable("id") String id,
                                                   @Valid @RequestBody ConversationRenameRequest request) {
        long userId = jwtService.extractUserId(jwt);
        return qaChatService.renameConversation(userId, id, request.title());
    }

    /** 删除会话（软删除）。 */
    @DeleteMapping("/conversations/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteConversation(@AuthenticationPrincipal Jwt jwt,
                                   @PathVariable("id") String id) {
        long userId = jwtService.extractUserId(jwt);
        qaChatService.deleteConversation(userId, id);
    }
}
