package com.tongji.counter.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.cache.RedisKeyScanner;
import com.tongji.counter.schema.CounterKeys;
import com.tongji.counter.schema.CounterSchema;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/** 计数事件幂等聚合，并以单条 Lua 原子完成“取增量、写 SDS、删除增量”。 */
@Service
public class CounterAggregationConsumer {

    private static final long DEDUP_TTL_SECONDS = 7 * 24 * 3600L;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate redis;
    private final RedisKeyScanner keyScanner;
    private final DefaultRedisScript<Long> aggregateScript;
    private final DefaultRedisScript<Long> drainScript;

    public CounterAggregationConsumer(ObjectMapper objectMapper, StringRedisTemplate redis,
                                      RedisKeyScanner keyScanner) {
        this.objectMapper = objectMapper;
        this.redis = redis;
        this.keyScanner = keyScanner;
        this.aggregateScript = script(AGGREGATE_EVENT_LUA);
        this.drainScript = script(DRAIN_FIELD_LUA);
    }

    @KafkaListener(topics = CounterTopics.EVENTS, groupId = "counter-agg")
    public void onMessage(String message, Acknowledgment ack) throws Exception {
        CounterEvent event = objectMapper.readValue(message, CounterEvent.class);
        validate(event);
        String eventId = event.getEventId();
        if (eventId == null || eventId.isBlank()) eventId = sha256(message);
        String aggKey = CounterKeys.aggKey(event.getEntityType(), event.getEntityId());
        String dedupKey = "counter:event:dedup:" + eventId;
        redis.execute(aggregateScript, List.of(aggKey, dedupKey),
                String.valueOf(event.getIdx()), String.valueOf(event.getDelta()),
                String.valueOf(DEDUP_TTL_SECONDS));
        ack.acknowledge();
    }

    @Scheduled(fixedDelay = 1000L)
    public void flush() {
        Set<String> keys = keyScanner.scan("agg:" + CounterSchema.SCHEMA_ID + ":*");
        for (String aggKey : keys) {
            String[] parts = aggKey.split(":", 4);
            if (parts.length != 4) continue;
            String cntKey = CounterKeys.sdsKey(parts[2], parts[3]);
            Set<Object> fields = redis.opsForHash().keys(aggKey);
            for (Object rawField : fields) {
                String field = String.valueOf(rawField);
                redis.execute(drainScript, List.of(aggKey, cntKey),
                        String.valueOf(CounterSchema.SCHEMA_LEN),
                        String.valueOf(CounterSchema.FIELD_SIZE), field);
            }
        }
    }

    private void validate(CounterEvent event) {
        if (event == null || event.getEntityType() == null || event.getEntityType().isBlank()
                || event.getEntityId() == null || event.getEntityId().isBlank()
                || event.getIdx() < 0 || event.getIdx() >= CounterSchema.SCHEMA_LEN
                || (event.getDelta() != 1 && event.getDelta() != -1)) {
            throw new IllegalArgumentException("计数事件字段非法");
        }
    }

    private static DefaultRedisScript<Long> script(String source) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setResultType(Long.class);
        script.setScriptText(source);
        return script;
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static final String AGGREGATE_EVENT_LUA = """
            if redis.call('SETNX', KEYS[2], '1') == 0 then return 0 end
            redis.call('EXPIRE', KEYS[2], tonumber(ARGV[3]))
            redis.call('HINCRBY', KEYS[1], ARGV[1], tonumber(ARGV[2]))
            return 1
            """;

    private static final String DRAIN_FIELD_LUA = """
            local aggKey = KEYS[1]
            local cntKey = KEYS[2]
            local schemaLen = tonumber(ARGV[1])
            local fieldSize = tonumber(ARGV[2])
            local idx = tonumber(ARGV[3])
            if not idx or idx < 0 or idx >= schemaLen then return -1 end
            local deltaRaw = redis.call('HGET', aggKey, ARGV[3])
            if not deltaRaw then return 0 end
            local delta = tonumber(deltaRaw)
            if not delta then return -2 end
            local function read32be(s, off)
              local b = {string.byte(s, off + 1, off + 4)}
              local n = 0
              for i=1,4 do n = n * 256 + b[i] end
              return n
            end
            local function write32be(n)
              local t = {}
              for i=4,1,-1 do t[i] = n % 256; n = math.floor(n / 256) end
              return string.char(unpack(t))
            end
            local cnt = redis.call('GET', cntKey)
            if not cnt or string.len(cnt) ~= schemaLen * fieldSize then
              cnt = string.rep(string.char(0), schemaLen * fieldSize)
            end
            local off = idx * fieldSize
            local value = read32be(cnt, off) + delta
            value = math.max(0, math.min(value, 4294967295))
            cnt = string.sub(cnt, 1, off) .. write32be(value) .. string.sub(cnt, off + fieldSize + 1)
            redis.call('SET', cntKey, cnt)
            redis.call('HDEL', aggKey, ARGV[3])
            if redis.call('HLEN', aggKey) == 0 then redis.call('DEL', aggKey) end
            return value
            """;
}
