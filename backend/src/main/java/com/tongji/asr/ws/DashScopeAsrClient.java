package com.tongji.asr.ws;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.asr.config.AsrProperties;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * DashScope 实时 ASR（paraformer-realtime-v2）出站 WebSocket 封装。
 * <p>每条浏览器语音会话对应一个本实例：open() 建连并发 run-task，
 * sendAudio() 转发 PCM 二进制，finish() 发 finish-task，close() 收尾。
 * <p>结果解析：DashScope 以 {@code result-generated} 文本帧推送增量句子，
 * 本类累计"已结束句"文本，回调 {@code onResult(累计文本, isEnd)}。
 * 使用 JDK 内置 {@link java.net.http.WebSocket}，零第三方依赖。
 */
@Slf4j
public class DashScopeAsrClient {

    private static final java.net.http.HttpClient HTTP_CLIENT = java.net.http.HttpClient.newHttpClient();
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AsrProperties props;
    private final BiConsumer<String, Boolean> onResult; // (累计文本, 是否句子结束)
    private final Consumer<String> onError;
    private final Runnable onClosed;

    private final String taskId = UUID.randomUUID().toString().replace("-", "");
    private java.net.http.WebSocket ds;

    // 仅在 DashScope 回调线程中访问（JDK 保证 listener 方法串行调用）
    private String committed = "";
    private final StringBuilder fragmentBuf = new StringBuilder();

    public DashScopeAsrClient(AsrProperties props,
                               BiConsumer<String, Boolean> onResult,
                               Consumer<String> onError,
                               Runnable onClosed) {
        this.props = props;
        this.onResult = onResult;
        this.onError = onError;
        this.onClosed = onClosed;
    }

    /** 建连并发送 run-task。 */
    public void open() {
        java.net.http.WebSocket.Listener listener = new java.net.http.WebSocket.Listener() {
            @Override public void onOpen(java.net.http.WebSocket webSocket) {
                webSocket.request(1);
            }

            @Override public java.util.concurrent.CompletionStage<?> onText(
                    java.net.http.WebSocket webSocket, CharSequence data, boolean last) {
                fragmentBuf.append(data);
                if (last) {
                    handleFrame(fragmentBuf.toString());
                    fragmentBuf.setLength(0);
                }
                webSocket.request(1);
                return null;
            }

            @Override public java.util.concurrent.CompletionStage<?> onClose(
                    java.net.http.WebSocket webSocket, int statusCode, String reason) {
                log.info("DashScope ASR closed: code={}, reason={}", statusCode, reason);
                onClosed.run();
                return null;
            }

            @Override public void onError(java.net.http.WebSocket webSocket, Throwable error) {
                log.error("DashScope ASR error: {}", error.getMessage(), error);
                onError.accept(error.getMessage());
            }
        };

        try {
            this.ds = HTTP_CLIENT.newWebSocketBuilder()
                    .header("Authorization", "bearer " + props.getApiKey())
                    .header("X-DashScope-DataInspection", "enable")
                    .buildAsync(URI.create(props.getWsUrl()), listener)
                    .orTimeout(10, TimeUnit.SECONDS)
                    .join();
        } catch (Exception e) {
            throw new RuntimeException("连接 DashScope ASR 失败: " + e.getMessage(), e);
        }
        sendControl(buildRunTask(), "run-task");
        log.info("ASR session opened: task_id={}", taskId);
    }

    /** 转发一段 PCM（Int16 LE）二进制。 */
    public void sendAudio(byte[] pcm) {
        java.net.http.WebSocket socket = this.ds;
        if (socket == null) return;
        try {
            socket.sendBinary(ByteBuffer.wrap(pcm), true);
        } catch (Exception e) {
            log.warn("ASR sendAudio failed: {}", e.getMessage());
        }
    }

    /** 发送 finish-task（结束本次识别，触发最终结果回传）。 */
    public synchronized void finish() {
        if (ds == null) return;
        sendControl(buildFinishTask(), "finish-task");
    }

    /** 关闭与 DashScope 的连接。 */
    public synchronized void close() {
        java.net.http.WebSocket socket = this.ds;
        if (socket == null) return;
        try {
            socket.sendClose(java.net.http.WebSocket.NORMAL_CLOSURE, "client closed");
        } catch (Exception e) {
            log.debug("ASR close ignored: {}", e.getMessage());
        }
        this.ds = null;
    }

    // ------------------------------------------------------------------
    // 协议构造与响应解析
    // ------------------------------------------------------------------

    private void sendControl(String json, String name) {
        try {
            ds.sendText(json, true).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("发送 " + name + " 失败: " + e.getMessage(), e);
        }
    }

    private String buildRunTask() {
        // LinkedHashMap 保证字段顺序，便于排查
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("header", java.util.Map.of(
                "action", "run-task",
                "task_id", taskId,
                "streaming", "duplex"));
        body.put("payload", java.util.Map.of(
                "task_group", "audio",
                "task", "asr",
                "function", "recognition",
                "model", props.getModel(),
                "parameters", java.util.Map.of(
                        "format", "pcm",
                        "sample_rate", 16000,
                        "language_hints", java.util.List.of("zh", "en")),
                "input", java.util.Map.of()));
        return toJson(body);
    }

    private String buildFinishTask() {
        // finish-task 必须带 payload，否则服务端返回 task-failed
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("header", java.util.Map.of(
                "action", "finish-task",
                "task_id", taskId,
                "streaming", "duplex"));
        body.put("payload", java.util.Map.of("input", java.util.Map.of()));
        return toJson(body);
    }

    private String toJson(Object o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            throw new RuntimeException("JSON 序列化失败", e);
        }
    }

    /** 解析 DashScope 文本帧，提取增量结果并回调。 */
    private void handleFrame(String frame) {
        try {
            JsonNode root = MAPPER.readTree(frame);
            String event = root.path("header").path("event").asText("");
            switch (event) {
                case "task-started" -> log.debug("ASR task-started: {}", taskId);
                case "result-generated" -> handleResult(root);
                case "task-failed" -> {
                    String msg = root.path("header").path("error_message").asText("识别失败");
                    log.error("ASR task-failed: {}", msg);
                    onError.accept(msg);
                }
                case "task-finished" -> {
                    log.info("ASR task-finished: {}", taskId);
                    onClosed.run();
                }
                default -> log.debug("ASR event ignored: {} ({})", event, taskId);
            }
        } catch (Exception e) {
            log.error("ASR frame parse failed: {}", e.getMessage());
        }
    }

    private void handleResult(JsonNode root) {
        JsonNode sentence = root.path("payload").path("output").path("sentence");
        String text = sentence.path("text").asText("");
        boolean isEnd = sentence.path("is_sentence_end").asBoolean(false);
        // 句子结束时并入累计文本；否则以 累计+当前句 输出
        if (isEnd) {
            committed = committed + text;
            onResult.accept(committed, true);
        } else {
            onResult.accept(committed + text, false);
        }
    }
}
