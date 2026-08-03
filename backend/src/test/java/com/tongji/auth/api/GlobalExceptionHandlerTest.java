package com.tongji.auth.api;

import com.tongji.auth.exception.BusinessException;
import com.tongji.auth.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void invalidRefreshTokenIsAnAuthenticationFailure() {
        ResponseEntity<Map<String, Object>> response = handler.handleBusiness(
                new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID));

        assertEquals(401, response.getStatusCode().value());
        assertEquals("REFRESH_TOKEN_INVALID", response.getBody().get("code"));
    }
}
