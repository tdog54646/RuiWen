package com.tongji.relation.service.impl;

import com.tongji.relation.mapper.RelationMapper;
import com.tongji.relation.outbox.OutboxService;
import com.tongji.user.domain.User;
import com.tongji.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RelationServiceImplTest {

    private RelationMapper mapper;
    private OutboxService outbox;
    private RelationServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(RelationMapper.class);
        outbox = mock(OutboxService.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        UserMapper users = mock(UserMapper.class);
        when(users.findById(8L)).thenReturn(User.builder().id(8L).build());
        when(redis.execute(any(DefaultRedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);
        service = new RelationServiceImpl(mapper, outbox, redis, users);
    }

    @Test
    void repeatedFollowAndUnfollowDoNotEmitEvents() {
        when(mapper.insertFollowing(anyLong(), eq(7L), eq(8L), eq(1))).thenReturn(0);
        when(mapper.cancelFollowing(7L, 8L)).thenReturn(0);

        assertFalse(service.follow(7L, 8L));
        assertFalse(service.unfollow(7L, 8L));
        verifyNoInteractions(outbox);
    }

    @Test
    void outboxFailureEscapesSoTransactionCanRollBack() {
        when(mapper.insertFollowing(anyLong(), eq(7L), eq(8L), eq(1))).thenReturn(1);
        when(mapper.findFollowingId(7L, 8L)).thenReturn(99L);
        doThrow(new IllegalStateException("outbox unavailable"))
                .when(outbox).insert(eq("following"), eq(99L), eq("FollowCreated"), any());

        assertThrows(IllegalStateException.class, () -> service.follow(7L, 8L));
    }
}
