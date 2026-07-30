package com.tongji.knowpost.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Cache;
import com.tongji.cache.hotkey.HotKeyDetector;
import com.tongji.counter.service.CounterService;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPostFeedRow;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class KnowPostFeedServiceImplTest {

    @Test
    void userPublicFeedUsesDatabaseVisibilityQueryBeforePagination() {
        KnowPostMapper mapper = mock(KnowPostMapper.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        CounterService counters = mock(CounterService.class);
        HotKeyDetector hotKey = mock(HotKeyDetector.class);
        KnowPostFeedRow row = new KnowPostFeedRow();
        row.setId(12L);
        row.setTitle("公开内容");
        row.setVisible("public");
        when(mapper.listUserPublicPublished(7L, 21, 0)).thenReturn(List.of(row));
        when(counters.getCountsBatch(eq("knowpost"), eq(List.of("12")), anyList()))
                .thenReturn(Map.of("12", Map.of("like", 2L, "fav", 1L)));
        Cache<String, com.tongji.knowpost.api.dto.FeedPageResponse> publicCache = Caffeine.newBuilder().build();
        Cache<String, com.tongji.knowpost.api.dto.FeedPageResponse> mineCache = Caffeine.newBuilder().build();
        var service = new KnowPostFeedServiceImpl(
                mapper, redis, new ObjectMapper(), counters,
                publicCache, mineCache, hotKey);

        var result = service.getUserPublicPublished(7L, 1, 20, null);

        assertEquals(1, result.items().size());
        assertEquals(2L, result.items().getFirst().likeCount());
        verify(mapper).listUserPublicPublished(7L, 21, 0);
        verify(mapper, never()).listMyPublished(anyLong(), anyInt(), anyInt());
        verify(mapper, never()).findDetailById(anyLong());
    }
}
