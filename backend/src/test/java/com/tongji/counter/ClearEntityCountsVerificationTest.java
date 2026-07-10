package com.tongji.counter;

import com.tongji.counter.event.CounterEventProducer;
import com.tongji.counter.schema.BitmapShard;
import com.tongji.counter.schema.CounterKeys;
import com.tongji.counter.service.impl.CounterServiceImpl;
import com.tongji.counter.service.impl.UserCounterServiceImpl;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.relation.mapper.RelationMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 验证“删除文章 → 回收计数”修复的核心逻辑：
 * 1. {@link CounterServiceImpl#clearEntityCounts} 先基于位图读取 like/fav，再删除全部计数 key；
 * 2. {@link UserCounterServiceImpl} 的 incrementPosts / incrementLikesReceived 正确扣减 ucnt 对应段。
 *
 * <p>连接信息从环境变量读取（REDIS_HOST/REDIS_PORT/REDIS_PASSWORD），不硬编码密码。
 * 使用隔离的测试 key（zzverify_* / 88888888），{@code @AfterEach} 清理，绝不触碰真实业务数据。</p>
 */
class ClearEntityCountsVerificationTest {

    private static final String HOST = System.getenv().getOrDefault("REDIS_HOST", "localhost");
    private static final int PORT = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
    private static final String PASSWORD = System.getenv().getOrDefault("REDIS_PASSWORD", "");

    /** 隔离测试文章 ID（绝不与真实雪花 ID 冲突） */
    private static final String TEST_EID = "zzverify_7777";
    /** 隔离测试作者 ID */
    private static final long TEST_UID = 88888888L;

    private static StringRedisTemplate redis;
    private static CounterServiceImpl counterService;
    private static UserCounterServiceImpl userCounterService;
    private static LettuceConnectionFactory factory;

    @BeforeAll
    static void init() {
        RedisStandaloneConfiguration cfg = new RedisStandaloneConfiguration(HOST, PORT);
        if (!PASSWORD.isEmpty()) {
            cfg.setPassword(PASSWORD);
        }
        factory = new LettuceConnectionFactory(cfg);
        factory.setDatabase(0);
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();

        counterService = new CounterServiceImpl(
                redis, mock(CounterEventProducer.class),
                mock(ApplicationEventPublisher.class), mock(RedissonClient.class));
        userCounterService = new UserCounterServiceImpl(
                redis, mock(KnowPostMapper.class), counterService, mock(RelationMapper.class));
    }

    @AfterAll
    static void teardown() {
        if (factory != null) {
            factory.destroy();
        }
    }

    @AfterEach
    void cleanup() {
        // 清理全部测试 key，确保在共享 Redis 上不留痕
        redis.delete("ucnt:" + TEST_UID);
        redis.delete(CounterKeys.sdsKey("knowpost", TEST_EID));
        redis.delete(CounterKeys.aggKey("knowpost", TEST_EID));
        deleteByPattern("bm:like:knowpost:" + TEST_EID + ":*");
        deleteByPattern("bm:fav:knowpost:" + TEST_EID + ":*");
    }

    @Test
    void clearEntityCounts_readsBitmapLikesThenDeletesAllKeys() {
        // 构造 3 个用户对该测试文章的点赞位图
        setLikeBitmap(TEST_EID, 999001L);
        setLikeBitmap(TEST_EID, 999002L);
        setLikeBitmap(TEST_EID, 999003L);
        // 构造实体 SDS 快照与聚合桶（验证它们也会被清理）
        redis.execute((RedisCallback<Void>) c -> {
            c.stringCommands().set(sdsKeyBytes(), new byte[20]);
            return null;
        });
        redis.opsForHash().increment(CounterKeys.aggKey("knowpost", TEST_EID), "1", 2);

        // 执行：先基于位图读计数 → 再删全部 key
        Map<String, Long> counts = counterService.clearEntityCounts("knowpost", TEST_EID);

        assertEquals(3L, counts.get("like"), "like 计数应等于位图中的点赞数 3");
        assertEquals(0L, counts.get("fav"), "无收藏位图，fav 应为 0");

        assertTrue(keysEmpty("bm:like:knowpost:" + TEST_EID + ":*"), "like 位图分片应被删除");
        assertTrue(keysEmpty("bm:fav:knowpost:" + TEST_EID + ":*"), "fav 位图分片应被删除");
        assertFalse(redis.hasKey(CounterKeys.sdsKey("knowpost", TEST_EID)), "实体 SDS 快照应被删除");
        assertFalse(redis.hasKey(CounterKeys.aggKey("knowpost", TEST_EID)), "聚合桶应被删除");
    }

    @Test
    void clearEntityCounts_withNoBitmaps_returnsZero() {
        Map<String, Long> counts = counterService.clearEntityCounts("knowpost", TEST_EID);
        assertEquals(0L, counts.get("like"), "无位图时 like 应为 0");
        assertEquals(0L, counts.get("fav"), "无位图时 fav 应为 0");
    }

    @Test
    void incrementPostsAndLikesReceived_decrementUcntSegments() {
        // 构造 ucnt：发文=2(offset 8)，获赞=5(offset 12)
        byte[] buf = new byte[20];
        write32be(buf, 8, 2L);
        write32be(buf, 12, 5L);
        setUcnt(buf);

        userCounterService.incrementPosts(TEST_UID, -1);          // 发文 2 -> 1
        userCounterService.incrementLikesReceived(TEST_UID, -3);  // 获赞 5 -> 2

        byte[] raw = readUcnt();
        assertEquals(1L, read32be(raw, 8), "发文数应 2-1=1");
        assertEquals(2L, read32be(raw, 12), "获赞数应 5-3=2");
    }

    /** 端到端复现 delete() 的计数回收序列：读位图赞数 → 清理事实层 → 扣减 ucnt。 */
    @Test
    void deleteFlow_clearThenDecrement() {
        setLikeBitmap(TEST_EID, 999010L);
        setLikeBitmap(TEST_EID, 999011L);
        setLikeBitmap(TEST_EID, 999012L);
        byte[] buf = new byte[20];
        write32be(buf, 8, 1L);   // 发文 1
        write32be(buf, 12, 3L);  // 获赞 3（恰好等于该文被赞数）
        setUcnt(buf);

        // —— delete() 里的核心序列 ——
        Map<String, Long> counts = counterService.clearEntityCounts("knowpost", TEST_EID);
        userCounterService.incrementPosts(TEST_UID, -1);
        long like = counts.getOrDefault("like", 0L);
        if (like > 0) {
            userCounterService.incrementLikesReceived(TEST_UID, -(int) like);
        }
        // ————————————————

        byte[] raw = readUcnt();
        assertEquals(0L, read32be(raw, 8), "发文数应 1-1=0");
        assertEquals(0L, read32be(raw, 12), "获赞数应 3-3=0");
        assertTrue(keysEmpty("bm:like:knowpost:" + TEST_EID + ":*"), "删除后位图应被清理，防止重建误统计");
    }

    // ---------- helpers ----------

    private void setLikeBitmap(String eid, long uid) {
        String bmKey = "bm:like:knowpost:" + eid + ":" + BitmapShard.chunkOf(uid);
        redis.opsForValue().setBit(bmKey, BitmapShard.bitOf(uid), true);
    }

    private void setUcnt(byte[] buf) {
        redis.execute((RedisCallback<Void>) c -> {
            c.stringCommands().set(ucntKeyBytes(), buf);
            return null;
        });
    }

    private byte[] readUcnt() {
        return redis.execute((RedisCallback<byte[]>) c -> c.stringCommands().get(ucntKeyBytes()));
    }

    private byte[] sdsKeyBytes() {
        return CounterKeys.sdsKey("knowpost", TEST_EID).getBytes(StandardCharsets.UTF_8);
    }

    private byte[] ucntKeyBytes() {
        return ("ucnt:" + TEST_UID).getBytes(StandardCharsets.UTF_8);
    }

    private boolean keysEmpty(String pattern) {
        Set<String> keys = redis.keys(pattern);
        return keys == null || keys.isEmpty();
    }

    private void deleteByPattern(String pattern) {
        Set<String> keys = redis.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redis.delete(keys);
        }
    }

    private static void write32be(byte[] buf, int off, long val) {
        long n = Math.max(0, Math.min(val, 0xFFFF_FFFFL));
        buf[off] = (byte) ((n >>> 24) & 0xFF);
        buf[off + 1] = (byte) ((n >>> 16) & 0xFF);
        buf[off + 2] = (byte) ((n >>> 8) & 0xFF);
        buf[off + 3] = (byte) (n & 0xFF);
    }

    private static long read32be(byte[] buf, int off) {
        if (buf == null || buf.length < off + 4) {
            return 0L;
        }
        long n = 0L;
        for (int i = 0; i < 4; i++) {
            n = (n << 8) | (buf[off + i] & 0xFFL);
        }
        return n;
    }
}
