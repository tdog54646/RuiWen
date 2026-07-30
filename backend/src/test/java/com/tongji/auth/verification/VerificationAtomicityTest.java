package com.tongji.auth.verification;

import com.tongji.auth.config.AuthProperties;
import com.tongji.auth.exception.BusinessException;
import com.tongji.auth.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class VerificationAtomicityTest {

    @Test
    void sendIntervalUsesAtomicSetIfAbsent() {
        VerificationCodeStore codeStore = mock(VerificationCodeStore.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        Duration interval = Duration.ofSeconds(60);
        when(values.setIfAbsent(anyString(), eq("1"), eq(interval))).thenReturn(false);
        AuthProperties properties = new AuthProperties();
        properties.getVerification().setSendInterval(interval);
        VerificationService service = new VerificationService(
                codeStore, mock(CodeSender.class), redis, properties);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.sendCode(VerificationScene.LOGIN, "13800000000"));

        assertEquals(ErrorCode.VERIFICATION_RATE_LIMIT, error.getErrorCode());
        verify(values).setIfAbsent(anyString(), eq("1"), eq(interval));
        verifyNoInteractions(codeStore);
    }

    @Test
    void parsesAtomicLuaResults() {
        VerificationCheckResult success = RedisVerificationCodeStore.parseResult("SUCCESS:1:5");
        VerificationCheckResult mismatch = RedisVerificationCodeStore.parseResult("MISMATCH:2:5");
        VerificationCheckResult limited = RedisVerificationCodeStore.parseResult("TOO_MANY_ATTEMPTS:5:5");

        assertEquals(VerificationCodeStatus.SUCCESS, success.status());
        assertEquals(VerificationCodeStatus.MISMATCH, mismatch.status());
        assertEquals(2, mismatch.attempts());
        assertEquals(VerificationCodeStatus.TOO_MANY_ATTEMPTS, limited.status());
    }
}
