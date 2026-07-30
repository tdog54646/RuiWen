package com.tongji.profile.service.impl;

import com.tongji.auth.exception.BusinessException;
import com.tongji.auth.exception.ErrorCode;
import com.tongji.profile.api.dto.ProfilePatchRequest;
import com.tongji.user.domain.User;
import com.tongji.user.mapper.UserMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ProfileServiceImplTest {

    @Test
    void rejectsEmailChangeAndDoesNotWriteProfile() {
        UserMapper mapper = mock(UserMapper.class);
        when(mapper.findById(1L)).thenReturn(User.builder()
                .id(1L).email("old@example.com").build());
        ProfileServiceImpl service = new ProfileServiceImpl(mapper);
        ProfilePatchRequest request = new ProfilePatchRequest(
                "name", null, null, null, null, null, "new@example.com", null);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.updateProfile(1L, request));

        assertEquals(ErrorCode.BAD_REQUEST, error.getErrorCode());
        verify(mapper, never()).updateProfile(any());
    }
}
