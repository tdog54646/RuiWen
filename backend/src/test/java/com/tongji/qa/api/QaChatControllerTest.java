package com.tongji.qa.api;

import com.tongji.auth.token.JwtService;
import com.tongji.qa.api.dto.QaChatRequest;
import com.tongji.qa.service.QaChatService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class QaChatControllerTest {

    @Test
    void chatDisablesProxyBufferingAndTransformation() {
        QaChatService qaChatService = mock(QaChatService.class);
        JwtService jwtService = mock(JwtService.class);
        Jwt jwt = mock(Jwt.class);
        QaChatRequest request = new QaChatRequest(null, "test", List.of(), null, null, "all");
        Flux<ServerSentEvent<String>> expected = Flux.empty();
        when(jwtService.extractUserId(jwt)).thenReturn(7L);
        when(qaChatService.streamChat(7L, request)).thenReturn(expected);

        MockHttpServletResponse response = new MockHttpServletResponse();
        QaChatController controller = new QaChatController(qaChatService, jwtService);

        Flux<ServerSentEvent<String>> actual = controller.chat(jwt, request, response);

        assertSame(expected, actual);
        assertEquals("no", response.getHeader("X-Accel-Buffering"));
        assertEquals("no-cache, no-store, no-transform", response.getHeader(HttpHeaders.CACHE_CONTROL));
    }
}
