package com.tongji.counter.api;

import com.tongji.auth.token.JwtService;
import com.tongji.counter.api.dto.ActionRequest;
import com.tongji.counter.service.CounterService;
import com.tongji.auth.exception.BusinessException;
import com.tongji.auth.exception.ErrorCode;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPost;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 行为接口：点赞/取消点赞、收藏/取消收藏。
 *
 * <p>所有接口基于登录用户，返回操作是否改变状态以及当前状态值。</p>
 */
@RestController
@RequestMapping("/api/action")
public class ActionController {

    private final CounterService counterService;
    private final JwtService jwtService;
    private final KnowPostMapper knowPostMapper;

    public ActionController(CounterService counterService, JwtService jwtService, KnowPostMapper knowPostMapper) {
        this.counterService = counterService;
        this.jwtService = jwtService;
        this.knowPostMapper = knowPostMapper;
    }

    /**
     * 点赞操作。
     */
    @PostMapping("/like")
    public ResponseEntity<Map<String, Object>> like(@Valid @RequestBody ActionRequest req,
                                                    @AuthenticationPrincipal Jwt jwt) {
        long uid = jwtService.extractUserId(jwt);
        validateEntity(req, uid);
        boolean changed = counterService.like(req.getEntityType(), req.getEntityId(), uid);
        return ResponseEntity.ok(Map.of(
                "changed", changed, // 标识这次操作是否改变状态（避免重复点击）
                "liked", counterService.isLiked(req.getEntityType(), req.getEntityId(), uid)
        ));
    }

    /**
     * 取消点赞操作。
     */
    @PostMapping("/unlike")
    public ResponseEntity<Map<String, Object>> unlike(@Valid @RequestBody ActionRequest req,
                                                      @AuthenticationPrincipal Jwt jwt) {
        long uid = jwtService.extractUserId(jwt);
        validateEntity(req, uid);
        boolean changed = counterService.unlike(req.getEntityType(), req.getEntityId(), uid);
        return ResponseEntity.ok(Map.of(
                "changed", changed, // 状态是否发生变化
                "liked", counterService.isLiked(req.getEntityType(), req.getEntityId(), uid)
        ));
    }

    /**
     * 收藏操作。
     */
    @PostMapping("/fav")
    public ResponseEntity<Map<String, Object>> fav(@Valid @RequestBody ActionRequest req,
                                                   @AuthenticationPrincipal Jwt jwt) {
        long uid = jwtService.extractUserId(jwt);
        validateEntity(req, uid);
        boolean changed = counterService.fav(req.getEntityType(), req.getEntityId(), uid);
        return ResponseEntity.ok(Map.of(
                "changed", changed, // 状态是否发生变化
                "faved", counterService.isFaved(req.getEntityType(), req.getEntityId(), uid)
        ));
    }

    /**
     * 取消收藏操作。
     */
    @PostMapping("/unfav")
    public ResponseEntity<Map<String, Object>> unfav(@Valid @RequestBody ActionRequest req,
                                                     @AuthenticationPrincipal Jwt jwt) {
        long uid = jwtService.extractUserId(jwt);
        validateEntity(req, uid);
        boolean changed = counterService.unfav(req.getEntityType(), req.getEntityId(), uid);
        return ResponseEntity.ok(Map.of(
                "changed", changed, // 状态是否发生变化
                "faved", counterService.isFaved(req.getEntityType(), req.getEntityId(), uid)
        ));
    }

    private void validateEntity(ActionRequest request, long userId) {
        final long postId;
        try {
            postId = Long.parseLong(request.getEntityId());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "实体 ID 格式非法");
        }
        KnowPost post = knowPostMapper.findById(postId);
        if (post == null || !"published".equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND, "内容不存在");
        }
        if (!"public".equals(post.getVisible()) && !Long.valueOf(userId).equals(post.getCreatorId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无权限操作该内容");
        }
    }
}
