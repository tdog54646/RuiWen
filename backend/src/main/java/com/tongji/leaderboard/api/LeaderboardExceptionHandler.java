package com.tongji.leaderboard.api;

import com.tongji.leaderboard.api.dto.common.ApiEnvelope;
import com.tongji.leaderboard.api.error.LeaderboardErrorCode;
import com.tongji.leaderboard.api.error.LeaderboardException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.UUID;

/**
 * 排行榜模块统一异常处理器。
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.tongji.leaderboard")
public class LeaderboardExceptionHandler {

    /**
     * 处理业务异常并返回业务码。
     */
    @ExceptionHandler(LeaderboardException.class)
    public ApiEnvelope<Void> handleBusiness(LeaderboardException ex, HttpServletRequest request) {
        return ApiEnvelope.fail(ex.getErrorCode().getCode(), ex.getMessage(), resolveRequestId(request));
    }

    /**
     * 处理请求体参数校验异常。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ApiEnvelope<Void> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpServletRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse(LeaderboardErrorCode.BAD_REQUEST.getDefaultMessage());
        return ApiEnvelope.fail(LeaderboardErrorCode.BAD_REQUEST.getCode(), message, resolveRequestId(request));
    }

    /**
     * 处理 URL 参数等约束校验异常。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ApiEnvelope<Void> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        return ApiEnvelope.fail(LeaderboardErrorCode.BAD_REQUEST.getCode(), ex.getMessage(), resolveRequestId(request));
    }

    /**
     * 处理参数非法异常。
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ApiEnvelope<Void> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return ApiEnvelope.fail(LeaderboardErrorCode.BAD_REQUEST.getCode(), ex.getMessage(), resolveRequestId(request));
    }

    /**
     * 处理兜底未知异常。
     */
    @ExceptionHandler(Exception.class)
    public ApiEnvelope<Void> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Leaderboard module unhandled exception", ex);
        return ApiEnvelope.fail(
                LeaderboardErrorCode.INTERNAL_ERROR.getCode(),
                LeaderboardErrorCode.INTERNAL_ERROR.getDefaultMessage(),
                resolveRequestId(request));
    }

    /**
     * 提取或生成 requestId。
     */
    private String resolveRequestId(HttpServletRequest request) {
        String fromHeader = request.getHeader("X-Request-Id");
        if (fromHeader != null && !fromHeader.isBlank()) {
            return fromHeader;
        }
        return UUID.randomUUID().toString();
    }
}
