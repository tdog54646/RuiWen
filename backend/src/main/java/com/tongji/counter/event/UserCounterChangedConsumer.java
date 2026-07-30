package com.tongji.counter.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.counter.schema.UserCounterKeys;
import com.tongji.counter.service.UserCounterService;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPost;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/** 通过 Kafka 可靠、幂等地维护作者获赞/获收藏计数。 */
@Service
public class UserCounterChangedConsumer {

    private final ObjectMapper objectMapper;
    private final KnowPostMapper knowPostMapper;
    private final StringRedisTemplate redis;
    private final UserCounterService userCounterService;
    private final DefaultRedisScript<Long> applyScript;

    public UserCounterChangedConsumer(ObjectMapper objectMapper, KnowPostMapper knowPostMapper,
                                      StringRedisTemplate redis, UserCounterService userCounterService) {
        this.objectMapper = objectMapper;
        this.knowPostMapper = knowPostMapper;
        this.redis = redis;
        this.userCounterService = userCounterService;
        this.applyScript = new DefaultRedisScript<>();
        this.applyScript.setResultType(Long.class);
        this.applyScript.setScriptText(APPLY_LUA);
    }

    @KafkaListener(topics = CounterTopics.EVENTS, groupId = "user-counter-write")
    public void onMessage(String message, Acknowledgment ack) throws Exception {
        CounterEvent event = objectMapper.readValue(message, CounterEvent.class);
        if (!"knowpost".equals(event.getEntityType())
                || (!"like".equals(event.getMetric()) && !"fav".equals(event.getMetric()))) {
            ack.acknowledge();
            return;
        }
        long postId = Long.parseLong(event.getEntityId());
        KnowPost post = knowPostMapper.findById(postId);
        // 删除流程会按位图事实统一扣减；删除后到达的旧事件不能重新加回作者计数。
        if (post == null || post.getCreatorId() == null || "deleted".equals(post.getStatus())) {
            ack.acknowledge();
            return;
        }
        if (event.getDelta() != 1 && event.getDelta() != -1) {
            throw new IllegalArgumentException("用户计数事件增量非法");
        }
        String eventId = event.getEventId();
        if (eventId == null || eventId.isBlank()) {
            eventId = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(message.getBytes(StandardCharsets.UTF_8)));
        }
        int fieldIndex = "like".equals(event.getMetric()) ? 4 : 5;
        redis.execute(applyScript, List.of(
                        UserCounterKeys.sdsKey(post.getCreatorId()),
                        "counter:user:dedup:" + eventId),
                "5", "4", String.valueOf(fieldIndex), String.valueOf(event.getDelta()),
                String.valueOf(7 * 24 * 3600));
        KnowPost current = knowPostMapper.findById(postId);
        if (current == null || "deleted".equals(current.getStatus())) {
            userCounterService.rebuildAllCounters(post.getCreatorId());
        }
        ack.acknowledge();
    }

    private static final String APPLY_LUA = """
            if redis.call('SETNX', KEYS[2], '1') == 0 then return 0 end
            redis.call('EXPIRE', KEYS[2], tonumber(ARGV[5]))
            local schemaLen = tonumber(ARGV[1])
            local fieldSize = tonumber(ARGV[2])
            local idx = tonumber(ARGV[3])
            local delta = tonumber(ARGV[4])
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
            local cnt = redis.call('GET', KEYS[1])
            if not cnt or string.len(cnt) ~= schemaLen * fieldSize then
              cnt = string.rep(string.char(0), schemaLen * fieldSize)
            end
            local off = (idx - 1) * fieldSize
            local value = math.max(0, math.min(read32be(cnt, off) + delta, 4294967295))
            cnt = string.sub(cnt, 1, off) .. write32be(value) .. string.sub(cnt, off + fieldSize + 1)
            redis.call('SET', KEYS[1], cnt)
            return value
            """;
}
