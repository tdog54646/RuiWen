package com.tongji.admin.api;

import com.tongji.admin.dto.SystemSettingsResponse;
import com.tongji.admin.dto.UpdateSystemSettingsRequest;
import com.tongji.admin.service.AdminSettingsService;
import com.tongji.auth.token.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台系统配置接口（修改系统配置为敏感操作，仅 SUPER_ADMIN）。
 */
@RestController
@RequestMapping("/api/admin/settings")
@RequiredArgsConstructor
public class AdminSettingsController {

    private final AdminSettingsService adminSettingsService;
    private final JwtService jwtService;

    /**
     * 获取系统配置快照。
     *
     * @return 配置快照。
     */
    @GetMapping
    public SystemSettingsResponse get() {
        return adminSettingsService.snapshot();
    }

    /**
     * 更新系统配置（密码最小长度 / 站点公告）。
     *
     * @param request 更新请求。
     * @param jwt     当前管理员令牌。
     */
    @PutMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> update(@Valid @RequestBody UpdateSystemSettingsRequest request,
                                       @AuthenticationPrincipal Jwt jwt) {
        Long operatorId = jwt != null ? jwtService.extractUserId(jwt) : null;
        adminSettingsService.update(request, operatorId);
        return ResponseEntity.noContent().build();
    }
}
