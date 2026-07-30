package com.tongji.auth.verification;

import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 基于 Redis 的验证码存储实现。
 * <p>
 * 使用 Hash 结构保存 `code`、`maxAttempts` 与 `attempts`，TTL 控制有效期。
 * 校验时支持尝试计数与错误状态返回，成功后删除键以防重用。
 * 格式：auth:code:场景名称:手机号或邮箱
 */
@Component
public class RedisVerificationCodeStore implements VerificationCodeStore {

    private static final DefaultRedisScript<Long> SAVE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('HSET', KEYS[1],
              'code', ARGV[1],
              'maxAttempts', ARGV[2],
              'attempts', '0')
            redis.call('PEXPIRE', KEYS[1], ARGV[3])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<String> VERIFY_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 0 then
              return 'NOT_FOUND:0:0'
            end
            local stored = redis.call('HGET', KEYS[1], 'code')
            local maxAttempts = tonumber(redis.call('HGET', KEYS[1], 'maxAttempts')) or 5
            local attempts = tonumber(redis.call('HGET', KEYS[1], 'attempts')) or 0
            if attempts >= maxAttempts then
              return 'TOO_MANY_ATTEMPTS:' .. attempts .. ':' .. maxAttempts
            end
            if stored == ARGV[1] then
              redis.call('DEL', KEYS[1])
              return 'SUCCESS:' .. attempts .. ':' .. maxAttempts
            end
            attempts = attempts + 1
            redis.call('HSET', KEYS[1], 'attempts', tostring(attempts))
            if attempts >= maxAttempts then
              redis.call('EXPIRE', KEYS[1], tonumber(ARGV[2]))
              return 'TOO_MANY_ATTEMPTS:' .. attempts .. ':' .. maxAttempts
            end
            return 'MISMATCH:' .. attempts .. ':' .. maxAttempts
            """, String.class);

    private final StringRedisTemplate redisTemplate;

    public RedisVerificationCodeStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 保存验证码到 Redis Hash，并设置 TTL。
     *
     * @param scene       场景名称。
     * @param identifier  标识（手机号或邮箱）。
     * @param code        验证码字符串。
     * @param ttl         有效期。
     * @param maxAttempts 最大尝试次数。
     * @throws RedisSystemException 保存失败时抛出。
     */
    @Override
    public void saveCode(String scene, String identifier, String code, Duration ttl, int maxAttempts) {
        String key = buildKey(scene, identifier);
        try {
            redisTemplate.execute(
                    SAVE_SCRIPT,
                    List.of(key),
                    code,
                    String.valueOf(maxAttempts),
                    String.valueOf(ttl.toMillis()));
        } catch (DataAccessException ex) {
            throw new RedisSystemException("Failed to save verification code", ex);
        }
    }

    /**
     * 校验验证码是否匹配，更新尝试计数并在成功时删除记录。
     *
     * @param scene      场景名称。
     * @param identifier 标识（手机号或邮箱）。
     * @param code       用户输入的验证码。
     * @return 校验结果（成功、未找到、错误、尝试过多）。
     */
    @Override
    public VerificationCheckResult verify(String scene, String identifier, String code) {
        String key = buildKey(scene, identifier);
        try {
            String result = redisTemplate.execute(
                    VERIFY_SCRIPT,
                    List.of(key),
                    code,
                    String.valueOf(Duration.ofMinutes(30).toSeconds()));
            return parseResult(result);
        } catch (DataAccessException ex) {
            throw new RedisSystemException("Failed to verify verification code", ex);
        }
    }

    /**
     * 使验证码失效（删除存储记录）。
     *
     * @param scene      场景名称。
     * @param identifier 标识（手机号或邮箱）。
     */
    @Override
    public void invalidate(String scene, String identifier) {
        redisTemplate.delete(buildKey(scene, identifier));
    }

    /**
     * 生成验证码的 Redis 键名。
     *
     * @param scene      场景名称。
     * @param identifier 标识（手机号或邮箱）。
     * @return 键名字符串。
     */
    private static String buildKey(String scene, String identifier) {
        return "auth:code:%s:%s".formatted(scene, identifier);
    }

    /** 解析 Lua 返回的“状态:次数:上限”结果。 */
    static VerificationCheckResult parseResult(String result) {
        if (result == null || result.isBlank()) {
            return new VerificationCheckResult(VerificationCodeStatus.NOT_FOUND, 0, 0);
        }
        String[] parts = result.split(":", 3);
        VerificationCodeStatus status;
        try {
            status = VerificationCodeStatus.valueOf(parts[0]);
        } catch (IllegalArgumentException ex) {
            status = VerificationCodeStatus.NOT_FOUND;
        }
        int attempts = parts.length > 1 ? parseInt(parts[1]) : 0;
        int maxAttempts = parts.length > 2 ? parseInt(parts[2]) : 0;
        return new VerificationCheckResult(status, attempts, maxAttempts);
    }

    private static int parseInt(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
