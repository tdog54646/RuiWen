package com.tongji.auth.registration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.systemconfig.SystemConfig;
import com.tongji.systemconfig.SystemConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * 注册策略服务：读取/更新当前注册方式（邮箱+密码 / 手机号+验证码）。
 * <p>
 * 存储于 {@code system_config} 表（key = {@link RegistrationPolicy#CONFIG_KEY}），
 * JSON 形如 {@code {"enabled":true,"mode":"PHONE_CODE"}}。读路径加 10s 本地缓存（cache-aside），
 * 写后立即失效缓存；并发由“管理员单点操作”天然规避。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RegistrationPolicyService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(10);

    private final SystemConfigMapper systemConfigMapper;
    private final ObjectMapper objectMapper;

    private volatile Cached cached;

    /**
     * 获取当前注册策略（命中缓存或回源 DB）。
     *
     * @return 注册策略。
     */
    public RegistrationPolicy getPolicy() {
        Cached current = this.cached;
        if (current != null && !current.isExpired()) {
            return current.policy;
        }
        RegistrationPolicy policy = loadFromDb();
        this.cached = new Cached(policy, Instant.now().plus(CACHE_TTL));
        return policy;
    }

    /**
     * 更新注册策略并失效缓存。
     *
     * @param enabled    是否开放注册。
     * @param mode       注册方式。
     * @param operatorId 操作人用户 ID（用于审计）。
     * @return 更新后的策略。
     */
    @Transactional
    public RegistrationPolicy updatePolicy(boolean enabled, RegistrationMode mode, Long operatorId) {
        RegistrationPolicy policy = new RegistrationPolicy(enabled, mode);
        String json = serialize(enabled, mode);
        if (systemConfigMapper.findByKey(RegistrationPolicy.CONFIG_KEY) == null) {
            systemConfigMapper.upsert(SystemConfig.builder()
                    .configKey(RegistrationPolicy.CONFIG_KEY)
                    .configValue(json)
                    .description("注册策略：enabled 是否开放注册；mode=EMAIL_PASSWORD/PHONE_CODE")
                    .updatedBy(operatorId)
                    .build());
        } else {
            systemConfigMapper.updateValue(RegistrationPolicy.CONFIG_KEY, json, operatorId);
        }
        this.cached = null;
        log.info("[RegistrationPolicy] 已更新：enabled={}, mode={}, operatorId={}", enabled, mode, operatorId);
        return policy;
    }

    private RegistrationPolicy loadFromDb() {
        String json = systemConfigMapper.findValueByKey(RegistrationPolicy.CONFIG_KEY);
        if (!StringUtils.hasText(json)) {
            return RegistrationPolicy.defaultValue();
        }
        try {
            JsonNode node = objectMapper.readTree(json);
            boolean enabled = node.path("enabled").asBoolean(true);
            RegistrationMode mode = RegistrationMode.fromString(node.path("mode").asText("PHONE_CODE"));
            return new RegistrationPolicy(enabled, mode);
        } catch (Exception ex) {
            log.warn("[RegistrationPolicy] 解析注册策略失败，回退默认值：{}", ex.getMessage());
            return RegistrationPolicy.defaultValue();
        }
    }

    private String serialize(boolean enabled, RegistrationMode mode) {
        try {
            return objectMapper.writeValueAsString(Map.of("enabled", enabled, "mode", mode.name()));
        } catch (Exception ex) {
            throw new IllegalStateException("序列化注册策略失败", ex);
        }
    }

    /** 缓存项：策略 + 过期时间。 */
    private record Cached(RegistrationPolicy policy, Instant expiresAt) {
        boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }
}
