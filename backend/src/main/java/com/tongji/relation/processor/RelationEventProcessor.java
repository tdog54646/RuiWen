package com.tongji.relation.processor;

import com.tongji.counter.service.UserCounterService;
import com.tongji.relation.event.RelationEvent;
import com.tongji.relation.mapper.RelationMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.sql.Timestamp;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * 关系事件处理器。
 * 职责：对 FollowCreated/FollowCanceled 事件进行去重、防抖与幂等处理，落库更新粉丝表，维护关注/粉丝 ZSet 缓存与 TTL，并原子更新用户维度计数（SDS）。
 */
@Service
public class RelationEventProcessor {
    private final RelationMapper mapper;
    private final StringRedisTemplate redis;
    private final UserCounterService userCounterService;
    private final DefaultRedisScript<Long> unlockScript;

    public RelationEventProcessor(RelationMapper mapper, StringRedisTemplate redis, UserCounterService userCounterService) {
        this.mapper = mapper;
        this.redis = redis;
        this.userCounterService = userCounterService;
        this.unlockScript = new DefaultRedisScript<>();
        this.unlockScript.setResultType(Long.class);
        this.unlockScript.setScriptText("if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end");
    }

    /**
     * 处理关系事件：入库、更新缓存、刷新计数，并进行幂等去重。
     * @param evt 关系事件
     */
    public void process(RelationEvent evt) {
        validate(evt);
        String eventId = evt.eventId() == null || evt.eventId().isBlank()
                ? evt.type() + ":" + evt.fromUserId() + ":" + evt.toUserId() + ":" + evt.id()
                : evt.eventId();
        String doneKey = "dedup:rel:done:" + eventId;
        if (Boolean.TRUE.equals(redis.hasKey(doneKey))) {
            return;
        }
        String lockKey = "dedup:rel:lock:" + eventId;
        String lockToken = UUID.randomUUID().toString();
        Boolean locked = redis.opsForValue().setIfAbsent(lockKey, lockToken, Duration.ofSeconds(30));
        if (!Boolean.TRUE.equals(locked)) {
            if (Boolean.TRUE.equals(redis.hasKey(doneKey))) return;
            throw new IllegalStateException("关系事件正在处理中");
        }
        try {
            if (Boolean.TRUE.equals(redis.hasKey(doneKey))) return;
            boolean currentlyFollowing = mapper.existsFollowing(evt.fromUserId(), evt.toUserId()) > 0;
            Timestamp updatedAt = mapper.findFollowingUpdatedAt(evt.fromUserId(), evt.toUserId());
            long score = updatedAt == null
                    ? (evt.occurredAt() == null ? System.currentTimeMillis() : evt.occurredAt())
                    : updatedAt.getTime();
            if (currentlyFollowing) {
                mapper.insertFollower(evt.id(), evt.toUserId(), evt.fromUserId(), 1);
                redis.opsForZSet().add("uf:flws:" + evt.fromUserId(), String.valueOf(evt.toUserId()), score);
                redis.opsForZSet().add("uf:fans:" + evt.toUserId(), String.valueOf(evt.fromUserId()), score);
            } else {
                mapper.cancelFollower(evt.toUserId(), evt.fromUserId());
                redis.opsForZSet().remove("uf:flws:" + evt.fromUserId(), String.valueOf(evt.toUserId()));
                redis.opsForZSet().remove("uf:fans:" + evt.toUserId(), String.valueOf(evt.fromUserId()));
            }
            redis.expire("uf:flws:" + evt.fromUserId(), Duration.ofHours(2));
            redis.expire("uf:fans:" + evt.toUserId(), Duration.ofHours(2));
            userCounterService.syncRelationshipCounts(evt.fromUserId(), evt.toUserId());
            redis.opsForValue().set(doneKey, "1", Duration.ofDays(7));
        } finally {
            redis.execute(unlockScript, List.of(lockKey), lockToken);
        }
    }

    private void validate(RelationEvent evt) {
        if (evt == null || evt.fromUserId() == null || evt.toUserId() == null || evt.id() == null
                || (!"FollowCreated".equals(evt.type()) && !"FollowCanceled".equals(evt.type()))) {
            throw new IllegalArgumentException("关系事件字段非法");
        }
    }
}
