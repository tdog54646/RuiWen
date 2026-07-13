package com.tongji.admin.api;

import com.tongji.admin.dto.UpdateRegistrationPolicyRequest;
import com.tongji.auth.api.dto.RegistrationConfigResponse;
import com.tongji.auth.exception.BusinessException;
import com.tongji.auth.exception.ErrorCode;
import com.tongji.auth.registration.RegistrationMode;
import com.tongji.auth.registration.RegistrationPolicy;
import com.tongji.auth.registration.RegistrationPolicyService;
import com.tongji.auth.token.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台注册策略管理接口（核心：手动切换注册方式 A/B）。
 */
@RestController
@RequestMapping("/api/admin/registration")
@RequiredArgsConstructor
public class AdminRegistrationController {

    private final RegistrationPolicyService registrationPolicyService;
    private final JwtService jwtService;

    /**
     * 查询当前注册策略。
     *
     * @return 注册策略。
     */
    @GetMapping
    public RegistrationConfigResponse get() {
        RegistrationPolicy policy = registrationPolicyService.getPolicy();
        return new RegistrationConfigResponse(policy.enabled(), policy.mode().name());
    }

    /**
     * 更新注册策略。
     *
     * @param request 策略请求（enabled + mode: EMAIL_PASSWORD / PHONE_CODE）。
     * @param jwt     当前管理员令牌（用于记录操作人）。
     * @return 更新后的注册策略。
     */
    @PutMapping
    public ResponseEntity<RegistrationConfigResponse> update(@Valid @RequestBody UpdateRegistrationPolicyRequest request,
                                                             @AuthenticationPrincipal Jwt jwt) {
        RegistrationMode mode = parseMode(request.mode());
        Long operatorId = jwt != null ? jwtService.extractUserId(jwt) : null;
        RegistrationPolicy policy = registrationPolicyService.updatePolicy(request.enabled(), mode, operatorId);
        return ResponseEntity.ok(new RegistrationConfigResponse(policy.enabled(), policy.mode().name()));
    }

    private RegistrationMode parseMode(String mode) {
        try {
            return RegistrationMode.valueOf(mode.trim().toUpperCase());
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "非法注册方式，仅支持 EMAIL_PASSWORD / PHONE_CODE");
        }
    }
}
