package com.tongji.asr.ws;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 语音转文字 WebSocket 端点注册：{@code /ws/asr}。
 * <p>握手经 {@link JwtHandshakeInterceptor} 校验 token（query 参数）；
 * 允许所有 origin（dev 下浏览器跨源直连后端，生产经 nginx 同源反代）。
 * {@code AsrProperties} 的绑定由 {@code AsrConfig} 完成，此处不重复声明。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class AsrWebSocketConfig implements WebSocketConfigurer {

    private final AsrWsHandler asrWsHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(asrWsHandler, "/ws/asr")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOrigins("*");
    }
}
