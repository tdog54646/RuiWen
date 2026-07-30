package com.tongji.admin.api;

import com.tongji.admin.dto.AdminResetPasswordRequest;
import com.tongji.admin.dto.AdminUpdateProfileRequest;
import com.tongji.admin.dto.AdminUserDetail;
import com.tongji.admin.dto.AdminUserListItem;
import com.tongji.admin.dto.PageResult;
import com.tongji.admin.dto.UpdateRoleRequest;
import com.tongji.admin.dto.UpdateStatusRequest;
import com.tongji.admin.service.AdminUserService;
import com.tongji.auth.token.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 后台用户管理接口。
 * <p>改角色与重置密码为敏感操作，仅 SUPER_ADMIN 可调用（路径已被 /api/admin/** 限制为管理员）。</p>
 */
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final JwtService jwtService;

    /**
     * 用户列表/搜索。
     *
     * @param keyword 关键字（昵称/手机号/邮箱）。
     * @param role    角色筛选。
     * @param status  状态筛选。
     * @param page    页码。
     * @param size    每页大小。
     * @return 分页结果。
     */
    @GetMapping
    public PageResult<AdminUserListItem> list(@RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) String role,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(defaultValue = "1") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return adminUserService.listUsers(keyword, role, status, page, size);
    }

    /**
     * 用户详情。
     *
     * @param id 用户 ID。
     * @return 用户详情。
     */
    @GetMapping("/{id}")
    public AdminUserDetail detail(@PathVariable long id) {
        return adminUserService.getDetail(id);
    }

    /**
     * 修改用户角色（仅 SUPER_ADMIN）。
     *
     * @param id      用户 ID。
     * @param request 角色请求。
     */
    @PatchMapping("/{id}/role")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> updateRole(@PathVariable long id, @Valid @RequestBody UpdateRoleRequest request) {
        adminUserService.updateRole(id, request.role());
        return ResponseEntity.noContent().build();
    }

    /**
     * 修改用户状态（封禁/解封）。
     *
     * @param id      用户 ID。
     * @param request 状态请求。
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<Void> updateStatus(@PathVariable long id,
                                             @Valid @RequestBody UpdateStatusRequest request,
                                             @AuthenticationPrincipal Jwt jwt) {
        adminUserService.updateStatus(id, request.status(), jwtService.extractUserId(jwt));
        return ResponseEntity.noContent().build();
    }

    /**
     * 重置用户密码（仅 SUPER_ADMIN）。返回新明文密码，仅此次可见。
     *
     * @param id      用户 ID。
     * @param request 重置请求（newPassword 为空则系统生成）。
     * @return 新明文密码。
     */
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Map<String, String>> resetPassword(@PathVariable long id,
                                                             @RequestBody(required = false) AdminResetPasswordRequest request) {
        String newPassword = request == null ? null : request.newPassword();
        String plain = adminUserService.resetPassword(id, newPassword);
        return ResponseEntity.ok(Map.of("password", plain));
    }

    /**
     * 管理员编辑用户资料。
     *
     * @param id      用户 ID。
     * @param request 资料请求。
     */
    @PutMapping("/{id}/profile")
    public ResponseEntity<Void> updateProfile(@PathVariable long id, @Valid @RequestBody AdminUpdateProfileRequest request) {
        adminUserService.updateProfile(id, request);
        return ResponseEntity.noContent().build();
    }
}
