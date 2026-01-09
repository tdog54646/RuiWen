package com.tongji.llm.cache;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
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
        if (!StringUtils.hasText(namespace) || queryVec == null || queryVec.length == 0) {
            return Optional.empty();
        }

        byte[] q = encodeFloat32(queryVec);

        // Lua：取 zset 最近 scanLimit 个成员，然后对每个成员读取 hash.vec，计算 cosine（vec 已归一化则仅点积）
        String lua = """
                local zkey = KEYS[1]
                local hprefix = ARGV[1]
                local q = ARGV[2]
                local threshold = tonumber(ARGV[3])
                local limit = tonumber(ARGV[4])

                local ids = redis.call('ZREVRANGE', zkey, 0, limit - 1)
                local bestId = nil
                local bestScore = -1

                local function toFloats(b)
                  local n = string.len(b) / 4
                  local out = {}
                  for i = 1, n do
                    local p = (i - 1) * 4 + 1
                    local b1 = string.byte(b, p)
                    local b2 = string.byte(b, p + 1)
                    local b3 = string.byte(b, p + 2)
                    local b4 = string.byte(b, p + 3)
                    local sign = (b4 > 127) and -1 or 1
                    local exp = ((b4 % 128) * 2) + math.floor(b3 / 128)
                    local mant = (b3 % 128) * 65536 + b2 * 256 + b1
                    if exp == 0 and mant == 0 then
                      out[i] = 0.0
                    else
                      local m = 1.0 + mant / 8388608.0
                      out[i] = sign * m * (2.0 ^ (exp - 127))
                    end
                  end
                  return out
                end

                local qf = toFloats(q)
                for _, id in ipairs(ids) do
                  local hkey = hprefix .. id
                  local v = redis.call('HGET', hkey, 'vec')
                  if v then
                    local vf = toFloats(v)
                    local dot = 0.0
                    local len = math.min(#qf, #vf)
                    for i = 1, len do
                      dot = dot + qf[i] * vf[i]
                    end
                    if dot > bestScore then
                      bestScore = dot
                      bestId = id
                    end
                  end
                end

                if bestId and bestScore >= threshold then
                  local hkey = hprefix .. bestId
                  local ans = redis.call('HGET', hkey, 'ans')
                  local qtext = redis.call('HGET', hkey, 'q')
                  return {bestId, tostring(bestScore), ans, qtext}
                end
                return nil
                """;

        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptText(lua);
        script.setResultType(List.class);

        String zkey = zsetKey(namespace);
        String hprefix = "sc:h:" + namespace + ":";

        try {
            List res = redis.execute(
                    script,
                    List.of(zkey),
                    hprefix,
                    Base64.getEncoder().encodeToString(q),
                    String.valueOf(threshold),
                    String.valueOf(scanLimit)
            );
            if (res == null || res.size() < 3) {
                return Optional.empty();
            }
            String id = String.valueOf(res.get(0));
            double score = Double.parseDouble(String.valueOf(res.get(1)));
            String ans = res.get(2) == null ? null : String.valueOf(res.get(2));
            String qtext = res.size() >= 4 && res.get(3) != null ? String.valueOf(res.get(3)) : null;
            if (!StringUtils.hasText(ans)) return Optional.empty();
            return Optional.of(new CacheHit(id, score, ans, qtext));
        } catch (DataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * 写入语义缓存：ZSet 记录最近 id，Hash 存 vec/ans/q，并设置 TTL。
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

        // score 用时间戳（毫秒），方便按最近窗口选 topN
        redis.opsForZSet().add(zkey, id, System.currentTimeMillis());
        // zset 本身也可给个较长 ttl（可选）
        if (ttlSeconds > 0) {
            redis.expire(zkey, java.time.Duration.ofSeconds(Math.max(ttlSeconds, 3600)));
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

    public record CacheHit(String id, double score, String answer, String cachedQuestion) {}
}
