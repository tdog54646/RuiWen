package com.tongji.llm.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

@Service
@RequiredArgsConstructor
public class SemanticCacheService {

    private final StringRedisTemplate redis;

    // Key 命名：按业务可再细分（比如 postId 维度）
    private static String zsetKey(String namespace) {
        return "sc:z:" + namespace;
    }

    private static String hashKey(String namespace, String id) {
        return "sc:h:" + namespace + ":" + id;
    }

    /**
     * 从语义缓存中尝试命中。
     *
     * @param namespace 缓存命名空间（建议包含 postId 或业务域，避免跨域污染）
     * @param queryVec  归一化后的 query 向量
     * @param threshold cosine 阈值（如 0.95）
     * @param scanLimit 本次最多扫描条数（控制延迟）
     */
    public Optional<CacheHit> getIfHit(String namespace, float[] queryVec, double threshold, int scanLimit) {
        if (!StringUtils.hasText(namespace) || queryVec == null || queryVec.length == 0 || scanLimit <= 0) {
            return Optional.empty();
        }
        String zkey = zsetKey(namespace);

        try {
            Set<String> ids = redis.opsForZSet().reverseRange(zkey, 0, scanLimit - 1L);
            if (ids == null || ids.isEmpty()) {
                return Optional.empty();
            }

            String bestId = null;
            String bestAnswer = null;
            String bestQuestion = null;
            double bestScore = Double.NEGATIVE_INFINITY;

            for (String id : ids) {
                if (!StringUtils.hasText(id)) {
                    continue;
                }
                String hkey = hashKey(namespace, id);
                List<Object> fields = redis.opsForHash().multiGet(hkey, List.of("vec", "ans", "q"));
                if (fields == null || fields.size() < 2) {
                    redis.opsForZSet().remove(zkey, id);
                    continue;
                }

                String vecBase64 = fields.get(0) == null ? null : String.valueOf(fields.get(0));
                String answer = fields.get(1) == null ? null : String.valueOf(fields.get(1));
                String qtext = fields.size() >= 3 && fields.get(2) != null ? String.valueOf(fields.get(2)) : null;
                if (!StringUtils.hasText(vecBase64) || !StringUtils.hasText(answer)) {
                    redis.opsForZSet().remove(zkey, id);
                    continue;
                }

                float[] cachedVec = decodeFloat32(vecBase64);
                if (cachedVec == null || cachedVec.length == 0) {
                    continue;
                }

                double score = dot(queryVec, cachedVec);
                if (score > bestScore) {
                    bestScore = score;
                    bestId = id;
                    bestAnswer = answer;
                    bestQuestion = qtext;
                }
            }

            if (bestId == null || bestScore < threshold || !StringUtils.hasText(bestAnswer)) {
                return Optional.empty();
            }

            // 命中后增加计数，让 zset score 表示真实命中次数
            redis.opsForZSet().incrementScore(zkey, bestId, 1.0);
            return Optional.of(new CacheHit(bestId, bestScore, bestAnswer, bestQuestion));
        } catch (DataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * 写入语义缓存：ZSet score 记录命中次数（首次写入为 1），Hash 存 vec/ans/q，并设置 TTL。
     */
    public void put(String namespace, String id, String question, float[] normalizedVec, String answer, long ttlSeconds) {
        if (!StringUtils.hasText(namespace) || !StringUtils.hasText(id) || normalizedVec == null || normalizedVec.length == 0) {
            return;
        }
        if (!StringUtils.hasText(answer)) return;

        byte[] vecBytes = encodeFloat32(normalizedVec);
        String hkey = hashKey(namespace, id);
        String zkey = zsetKey(namespace);

        Map<String, String> m = new HashMap<>();
        m.put("vec", Base64.getEncoder().encodeToString(vecBytes));
        m.put("ans", answer);
        if (StringUtils.hasText(question)) m.put("q", question);

        redis.opsForHash().putAll(hkey, m);
        if (ttlSeconds > 0) {
            redis.expire(hkey, java.time.Duration.ofSeconds(ttlSeconds));
        }

        // score 记录命中次数：仅首次写入时初始化为 1，避免覆盖已有计数
        redis.opsForZSet().addIfAbsent(zkey, id, 1.0);
        // zset 本身也可给个较长 ttl（可选）
        if (ttlSeconds > 0) {
            redis.expire(zkey, java.time.Duration.ofSeconds(Math.max(ttlSeconds, 3600)));
        }
    }

    /**
     * 读取热点问答候选（按命中次数降序），并惰性清理 hash 已过期的脏成员。
     */
    public List<HotQaItem> listHotQuestions(String namespace, int limit) {
        if (!StringUtils.hasText(namespace) || limit <= 0) {
            return List.of();
        }
        try {
            String zkey = zsetKey(namespace);
            Set<org.springframework.data.redis.core.ZSetOperations.TypedTuple<String>> tuples =
                    redis.opsForZSet().reverseRangeWithScores(zkey, 0, limit - 1L);
            if (tuples == null || tuples.isEmpty()) {
                return List.of();
            }

            List<HotQaItem> items = new ArrayList<>(tuples.size());
            for (org.springframework.data.redis.core.ZSetOperations.TypedTuple<String> tuple : tuples) {
                if (tuple == null || !StringUtils.hasText(tuple.getValue())) {
                    continue;
                }
                String cacheId = tuple.getValue();
                String hkey = hashKey(namespace, cacheId);
                Object q = redis.opsForHash().get(hkey, "q");
                if (!StringUtils.hasText(q == null ? null : String.valueOf(q))) {
                    // hash 已不存在或无问题文案，视为脏成员并惰性清理 zset
                    redis.opsForZSet().remove(zkey, cacheId);
                    continue;
                }
                long hitCount = tuple.getScore() == null ? 0L : Math.round(tuple.getScore());
                items.add(new HotQaItem(String.valueOf(q), hitCount));
            }
            return items;
        } catch (DataAccessException e) {
            // Redis 异常时降级为空，避免接口抛 5xx
            return List.of();
        }
    }

    /**
     * 归一化：让 cosine 相似度简化为点积。
     */
    public static float[] l2Normalize(float[] v) {
        double sum = 0.0;
        for (float x : v) sum += (double) x * x;
        double norm = Math.sqrt(sum);
        if (norm <= 0.0) return v;

        float[] out = new float[v.length];
        for (int i = 0; i < v.length; i++) {
            out[i] = (float) (v[i] / norm);
        }
        return out;
    }

    private static byte[] encodeFloat32(float[] v) {
        ByteBuffer bb = ByteBuffer.allocate(v.length * 4).order(ByteOrder.LITTLE_ENDIAN);
        for (float x : v) bb.putFloat(x);
        return bb.array();
    }

    private static float[] decodeFloat32(String base64) {
        if (!StringUtils.hasText(base64)) {
            return null;
        }
        try {
            byte[] bytes = Base64.getDecoder().decode(base64);
            if (bytes.length == 0 || bytes.length % 4 != 0) {
                return null;
            }
            ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
            float[] out = new float[bytes.length / 4];
            for (int i = 0; i < out.length; i++) {
                out[i] = bb.getFloat();
            }
            return out;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static double dot(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || b.length == 0) {
            return Double.NEGATIVE_INFINITY;
        }
        int len = Math.min(a.length, b.length);
        double sum = 0.0;
        for (int i = 0; i < len; i++) {
            sum += (double) a[i] * b[i];
        }
        return sum;
    }

    public record CacheHit(String id, double score, String answer, String cachedQuestion) {}

    public record HotQaItem(String question, long hitCount) {}
}
