package com.tongji.admin.service;

import com.tongji.admin.dto.SystemSettingsResponse;
import com.tongji.admin.dto.UpdateSystemSettingsRequest;
import com.tongji.auth.config.AuthProperties;
import com.tongji.auth.exception.BusinessException;
import com.tongji.auth.exception.ErrorCode;
import com.tongji.auth.registration.RegistrationPolicy;
import com.tongji.auth.registration.RegistrationPolicyService;
import com.tongji.systemconfig.SystemConfig;
import com.tongji.systemconfig.SystemConfigMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 后台系统配置服务。
 * <p>
 * 读：快照展示密码策略 / 验证码配置 / JWT TTL / 注册策略 / 站点公告；
 * 写：密码最小长度（持久化到 system_config 并即时覆盖内存 AuthProperties，使注册校验立即生效）、站点公告。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class AdminSettingsService {

    /** system_config 中的 key：密码最小长度。 */
    public static final String KEY_PASSWORD_MIN_LENGTH = "security.password-min-length";
    /** system_config 中的 key：站点公告。 */
    public static final String KEY_SITE_ANNOUNCEMENT = "site.announcement";

    private final AuthProperties authProperties;
    private final RegistrationPolicyService registrationPolicyService;
    private final SystemConfigMapper systemConfigMapper;

    /**
     * 获取系统配置快照。
     *
     * @return 配置快照。
     */
    public SystemSettingsResponse snapshot() {
        AuthProperties.Password pw = authProperties.getPassword();
        AuthProperties.Verification v = authProperties.getVerification();
        AuthProperties.Jwt jwt = authProperties.getJwt();
        RegistrationPolicy policy = registrationPolicyService.getPolicy();
        String announcement = readValue(KEY_SITE_ANNOUNCEMENT, "");
        return new SystemSettingsResponse(
                new SystemSettingsResponse.PasswordPolicy(pw.getMinLength(), pw.getBcryptStrength()),
                new SystemSettingsResponse.Verification(v.getCodeLength(), v.getTtl(), v.getMaxAttempts(),
                        v.getSendInterval(), v.getDailyLimit()),
                new SystemSettingsResponse.Jwt(jwt.getAccessTokenTtl(), jwt.getRefreshTokenTtl()),
                new SystemSettingsResponse.Registration(policy.enabled(), policy.mode().name()),
                announcement
        );
    }

    /**
     * 更新可改项。
     *
     * @param request 更新请求。
     * @param operatorId 操作人 ID。
     */
    @Transactional
    public void update(UpdateSystemSettingsRequest request, Long operatorId) {
        if (request.passwordMinLength() != null) {
            int min = request.passwordMinLength();
            if (min < 6 || min > 64) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "密码最小长度需在 6~64 之间");
            }
            upsert(KEY_PASSWORD_MIN_LENGTH, String.valueOf(min), "密码最小长度", operatorId);
            // 即时覆盖内存配置，使注册/重置密码校验立即生效
            authProperties.getPassword().setMinLength(min);
        }
        if (request.announcement() != null) {
            upsert(KEY_SITE_ANNOUNCEMENT, request.announcement(), "站点公告", operatorId);
        }
    }

    private void upsert(String key, String value, String description, Long operatorId) {
        if (systemConfigMapper.findByKey(key) == null) {
            systemConfigMapper.upsert(SystemConfig.builder()
                    .configKey(key)
                    .configValue(value)
                    .description(description)
                    .updatedBy(operatorId)
                    .build());
        } else {
            systemConfigMapper.updateValue(key, value, operatorId);
        }
    }

    private String readValue(String key, String fallback) {
        String value = systemConfigMapper.findValueByKey(key);
        return value == null ? fallback : value;
    }
}
