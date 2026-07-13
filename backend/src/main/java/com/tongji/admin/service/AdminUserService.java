package com.tongji.admin.service;

import com.tongji.admin.dto.AdminUpdateProfileRequest;
import com.tongji.admin.dto.AdminUserDetail;
import com.tongji.admin.dto.AdminUserListItem;
import com.tongji.admin.dto.PageResult;
import com.tongji.auth.exception.BusinessException;
import com.tongji.auth.exception.ErrorCode;
import com.tongji.auth.token.RefreshTokenStore;
import com.tongji.user.domain.User;
import com.tongji.user.domain.UserRole;
import com.tongji.user.domain.UserStatus;
import com.tongji.user.mapper.UserMapper;
import com.tongji.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.List;

/**
 * 后台用户管理服务：列表/搜索/详情/改角色/封禁解封/重置密码/编辑资料。
 * <p>安全策略：</p>
 * <ul>
 *   <li>改角色/封禁/重置密码均校验目标用户存在；</li>
 *   <li>禁止操作最后一个超级管理员（降级或封禁）；</li>
 *   <li>封禁与重置密码时撤销该用户所有刷新令牌，强制下线。</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final char[] PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789".toCharArray();

    private final UserService userService;
    private final UserMapper userMapper;
    private final RefreshTokenStore refreshTokenStore;
    private final PasswordEncoder passwordEncoder;

    /**
     * 用户列表/搜索。
     */
    public PageResult<AdminUserListItem> listUsers(String keyword, String role, String status, int page, int size) {
        long total = userService.countSearchUsers(keyword, role, status);
        List<User> users = userService.searchUsers(keyword, role, status, page, size);
        List<AdminUserListItem> items = users.stream()
                .map(this::toListItem)
                .toList();
        int s = Math.min(Math.max(size, 1), 100);
        return new PageResult<>(items, total, Math.max(page, 1), s);
    }

    /**
     * 用户详情。
     */
    public AdminUserDetail getDetail(long id) {
        User user = requireUser(id);
        return new AdminUserDetail(
                user.getId(),
                user.getNickname(),
                user.getPhone(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.getAvatar(),
                user.getBio(),
                user.getGender(),
                user.getBirthday(),
                user.getSchool(),
                user.getZgId(),
                StringUtils.hasText(user.getPasswordHash()),
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    /**
     * 修改用户角色。仅 SUPER_ADMIN 可调用（控制器层 @PreAuthorize）；禁止降级最后一个超管。
     */
    @Transactional
    public void updateRole(long targetId, String role) {
        if (!UserRole.isValid(role)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "非法角色");
        }
        User user = requireUser(targetId);
        if (UserRole.SUPER_ADMIN.equals(user.getRole()) && !UserRole.SUPER_ADMIN.equals(role)) {
            ensureNotLastSuperAdmin();
        }
        userService.updateRole(targetId, role);
    }

    /**
     * 修改用户状态（封禁/解封）。封禁时撤销全部刷新令牌；禁止封禁最后一个超管。
     */
    @Transactional
    public void updateStatus(long targetId, String status) {
        if (!UserStatus.isValid(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "非法状态");
        }
        User user = requireUser(targetId);
        if (UserRole.SUPER_ADMIN.equals(user.getRole()) && UserStatus.BANNED.equals(status)) {
            ensureNotLastSuperAdmin();
        }
        userService.updateStatus(targetId, status);
        if (UserStatus.BANNED.equals(status)) {
            refreshTokenStore.revokeAll(targetId);
        }
    }

    /**
     * 重置用户密码。返回明文密码（仅此次返回），并撤销全部刷新令牌。
     */
    @Transactional
    public String resetPassword(long targetId, String newPassword) {
        User user = requireUser(targetId);
        String plain = StringUtils.hasText(newPassword) ? newPassword.trim() : generateRandomPassword(12);
        userMapper.updatePassword(user.getId(), passwordEncoder.encode(plain));
        refreshTokenStore.revokeAll(targetId);
        return plain;
    }

    /**
     * 管理员编辑用户资料（复用 UserMapper.updateProfile）。
     */
    @Transactional
    public void updateProfile(long targetId, AdminUpdateProfileRequest request) {
        requireUser(targetId);
        User patch = new User();
        patch.setId(targetId);
        patch.setNickname(request.nickname());
        patch.setBio(request.bio());
        patch.setGender(request.gender());
        patch.setBirthday(request.birthday());
        patch.setSchool(request.school());
        patch.setZgId(request.zgId());
        patch.setAvatar(request.avatar());
        patch.setEmail(request.email());
        userMapper.updateProfile(patch);
    }

    private User requireUser(long id) {
        return userService.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND));
    }

    /** 校验是否为最后一个超级管理员，是则禁止降级/封禁。 */
    private void ensureNotLastSuperAdmin() {
        long count = userService.countByRole(UserRole.SUPER_ADMIN);
        if (count <= 1) {
            throw new BusinessException(ErrorCode.LAST_SUPER_ADMIN);
        }
    }

    private AdminUserListItem toListItem(User user) {
        return new AdminUserListItem(
                user.getId(),
                user.getNickname(),
                user.getPhone(),
                user.getEmail(),
                user.getRole(),
                user.getStatus(),
                user.getAvatar(),
                user.getCreatedAt()
        );
    }

    private static String generateRandomPassword(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(PASSWORD_ALPHABET[RANDOM.nextInt(PASSWORD_ALPHABET.length)]);
        }
        return sb.toString();
    }
}
