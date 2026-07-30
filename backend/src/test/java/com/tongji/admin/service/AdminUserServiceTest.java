package com.tongji.admin.service;

import com.tongji.admin.dto.AdminUpdateProfileRequest;
import com.tongji.auth.exception.BusinessException;
import com.tongji.auth.exception.ErrorCode;
import com.tongji.auth.token.RefreshTokenStore;
import com.tongji.user.domain.User;
import com.tongji.user.domain.UserRole;
import com.tongji.user.domain.UserStatus;
import com.tongji.user.mapper.UserMapper;
import com.tongji.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class AdminUserServiceTest {

    private UserService userService;
    private UserMapper userMapper;
    private RefreshTokenStore refreshTokenStore;
    private AdminUserService service;

    @BeforeEach
    void setUp() {
        userService = mock(UserService.class);
        userMapper = mock(UserMapper.class);
        refreshTokenStore = mock(RefreshTokenStore.class);
        service = new AdminUserService(
                userService, userMapper, refreshTokenStore, mock(PasswordEncoder.class));
    }

    @Test
    void ordinaryAdminCannotBanSuperAdmin() {
        User target = superAdmin(1L, UserStatus.ACTIVE);
        when(userMapper.lockSuperAdmins()).thenReturn(List.of(target, superAdmin(2L, UserStatus.ACTIVE)));
        when(userMapper.findByIdForUpdate(1L)).thenReturn(target);
        when(userMapper.findByIdForUpdate(3L)).thenReturn(
                User.builder().id(3L).role(UserRole.ADMIN).status(UserStatus.ACTIVE).build());

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateStatus(1L, UserStatus.BANNED, 3L));

        assertEquals(ErrorCode.FORBIDDEN, error.getErrorCode());
        verify(userService, never()).updateStatus(anyLong(), anyString());
    }

    @Test
    void cannotBanOnlyActiveSuperAdmin() {
        User target = superAdmin(1L, UserStatus.ACTIVE);
        when(userMapper.lockSuperAdmins()).thenReturn(List.of(target, superAdmin(2L, UserStatus.BANNED)));
        when(userMapper.findByIdForUpdate(1L)).thenReturn(target);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateStatus(1L, UserStatus.BANNED, 1L));

        assertEquals(ErrorCode.LAST_SUPER_ADMIN, error.getErrorCode());
        verify(userService, never()).updateStatus(anyLong(), anyString());
    }

    @Test
    void banningSuperAdminSucceedsWhenAnotherActiveSuperAdminRemains() {
        User target = superAdmin(1L, UserStatus.ACTIVE);
        when(userMapper.lockSuperAdmins()).thenReturn(List.of(target, superAdmin(2L, UserStatus.ACTIVE)));
        when(userMapper.findByIdForUpdate(1L)).thenReturn(target);

        service.updateStatus(1L, UserStatus.BANNED, 1L);

        verify(userService).updateStatus(1L, UserStatus.BANNED);
        verify(refreshTokenStore).revokeAll(1L);
    }

    @Test
    void adminProfileUpdateRejectsEmailChange() {
        User target = User.builder().id(1L).email("old@example.com").build();
        when(userService.findById(1L)).thenReturn(Optional.of(target));
        AdminUpdateProfileRequest request = new AdminUpdateProfileRequest(
                null, null, null, null, null, null, null, "new@example.com");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateProfile(1L, request));

        assertEquals(ErrorCode.BAD_REQUEST, error.getErrorCode());
        verify(userMapper, never()).updateProfile(any());
    }

    private static User superAdmin(long id, String status) {
        return User.builder().id(id).role(UserRole.SUPER_ADMIN).status(status).build();
    }
}
