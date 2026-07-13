package com.tongji.asr.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.asr.config.AsrProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.nio.ByteBuffer;

/**
 * 语音转文字 WebSocket 中继处理器。
 * <p>浏览器会话 ↔ DashScope 实时 ASR WebSocket 双向转发：
 * <ul>
 *   <li>浏览器二进制帧（Int16 PCM）→ DashScope；</li>
 *   <li>浏览器文本 {@code {"action":"stop"}} → 触发 finish-task；</li>
 *   <li>DashScope result-generated → 浏览器文本 {@code {"text":..,"isEnd":..}}；</li>
 *   <li>DashScope 关闭/失败 → 关闭浏览器会话。</li>
 * </ul>
 * 向浏览器发送统一在 session 上加锁（DashScope 回调运行在 IO 线程）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AsrWsHandler extends TextWebSocketHandler {

    private static final String ATTR_CLIENT = "asr.client";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AsrProperties asrProperties;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = (Long) session.getAttributes().get(JwtHandshakeInterceptor.ATTR_USER_ID);
        log.info("ASR WS connected: session={}, userId={}", session.getId(), userId);

        DashScopeAsrClient client = new DashScopeAsrClient(
                asrProperties,
                // onResult：累计文本 + 是否句末
                (text, isEnd) -> sendToBrowser(session, resultMessage(text, isEnd)),
                // onError：下发错误并关闭
                error -> {
                    sendToBrowser(session, errorMessage(error));
                    closeSession(session, CloseStatus.SERVER_ERROR);
                },
                // onClosed：DashScope 任务结束，关闭浏览器会话
                () -> closeSession(session, CloseStatus.NORMAL)
        );
        try {
            client.open();
            session.getAttributes().put(ATTR_CLIENT, client);
        } catch (Exception e) {
            log.error("ASR open failed: {}", e.getMessage(), e);
            sendToBrowser(session, errorMessage("语音识别服务连接失败"));
            closeSession(session, CloseStatus.SERVER_ERROR);
        }
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) {
        DashScopeAsrClient client = getClient(session);
        if (client != null) {
            // BinaryMessage.getPayload() 返回 ByteBuffer；拷贝为堆 byte[] 后转发
            ByteBuffer buf = message.getPayload();
            byte[] audio = new byte[buf.remaining()];
            buf.get(audio);
            client.sendAudio(audio);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        DashScopeAsrClient client = getClient(session);
        if (client == null) return;
        try {
            JsonNode root = MAPPER.readTree(message.getPayload());
            if ("stop".equals(root.path("action").asText(""))) {
                log.info("ASR stop requested: session={}", session.getId());
                client.finish();
            }
        } catch (Exception e) {
            log.warn("ASR unexpected text frame: {}", message.getPayload());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        DashScopeAsrClient client = getClient(session);
        if (client != null) {
            client.close();
        }
        log.info("ASR WS closed: session={}, status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("ASR transport error: session={}, {}", session.getId(), exception.getMessage());
    }

    // ------------------------------------------------------------------

    private DashScopeAsrClient getClient(WebSocketSession session) {
        Object c = session.getAttributes().get(ATTR_CLIENT);
        return c instanceof DashScopeAsrClient client ? client : null;
    }

    /** 向浏览器发送文本帧，加锁并校验会话开启状态。 */
    private void sendToBrowser(WebSocketSession session, TextMessage message) {
        synchronized (session) {
            try {
                if (session.isOpen()) {
                    session.sendMessage(message);
                }
            } catch (Exception e) {
                log.warn("ASR sendToBrowser failed: {}", e.getMessage());
            }
        }
    }

    private void closeSession(WebSocketSession session, CloseStatus status) {
        synchronized (session) {
            try {
                if (session.isOpen()) {
                    session.close(status);
                }
            } catch (Exception e) {
                log.debug("ASR closeSession ignored: {}", e.getMessage());
            }
        }
    }

    private TextMessage resultMessage(String text, boolean isEnd) {
        try {
            return new TextMessage(MAPPER.writeValueAsString(
                    java.util.Map.of("text", text, "isEnd", isEnd)));
        } catch (Exception e) {
            return new TextMessage("{\"text\":\"\",\"isEnd\":false}");
        }
    }

    private TextMessage errorMessage(String message) {
        try {
            return new TextMessage(MAPPER.writeValueAsString(
                    java.util.Map.of("error", message)));
        } catch (Exception e) {
            return new TextMessage("{\"error\":\"语音识别失败\"}");
        }
    }
}
