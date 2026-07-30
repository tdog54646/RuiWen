package com.tongji.storage.api;

import com.tongji.auth.exception.BusinessException;
import com.tongji.auth.exception.ErrorCode;
import com.tongji.auth.token.JwtService;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPost;
import com.tongji.storage.OssStorageService;
import com.tongji.storage.api.dto.ChatImagePresignRequest;
import com.tongji.storage.api.dto.StoragePresignRequest;
import com.tongji.storage.api.dto.StoragePresignResponse;
import com.tongji.storage.config.OssProperties;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/storage")
@Validated
@RequiredArgsConstructor
public class StorageController {

    private final OssStorageService ossStorageService;
    private final JwtService jwtService;
    private final KnowPostMapper knowPostMapper;
    private final OssProperties ossProperties;

    /**
     * 获取带 MIME、对象键和精确大小约束的 OSS 表单直传授权。
     */
    @PostMapping("/presign")
    public StoragePresignResponse presign(@Valid @RequestBody StoragePresignRequest request,
                                          @AuthenticationPrincipal Jwt jwt) {
        long userId = jwtService.extractUserId(jwt);

        long postId;
        try {
            postId = Long.parseLong(request.postId());
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "postId 非法");
        }

        // 权限校验：postId 必须属于当前用户
        KnowPost post = knowPostMapper.findById(postId);
        if (post == null || post.getCreatorId() == null || post.getCreatorId() != userId) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "草稿不存在或无权限");
        }
        if (!"draft".equals(post.getStatus()) && !"published".equals(post.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前状态不允许上传资源");
        }

        String scene = request.scene();
        String objectKey;
        String contentType = normalizeContentType(request.contentType());
        String ext = validateAndNormalize(scene, contentType, request.ext(), request.size());

        if ("knowpost_content".equals(scene)) {
            String version = UUID.randomUUID().toString().replace("-", "");
            objectKey = "posts/" + postId + "/content-" + version + ext;
        } else if ("knowpost_image".equals(scene)) {
            String date = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.of("UTC")).format(Instant.now());
            String rand = UUID.randomUUID().toString().replace("-", "");
            objectKey = "posts/" + postId + "/images/" + date + "/" + rand + ext;
        } else {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的上传场景");
        }

        int expiresIn = 600; // 10 分钟
        OssStorageService.DirectUploadGrant grant = ossStorageService.generatePresignedPost(
                objectKey, contentType, request.size(), expiresIn);
        return new StoragePresignResponse(
                objectKey, grant.uploadUrl(), ossStorageService.originUrl(objectKey),
                ossStorageService.generatePresignedGetUrl(objectKey), "POST", Map.of(),
                grant.formFields(), expiresIn);
    }

    /**
     * 聊天图片预签名直传：不依赖 postId，按用户+日期隔离。
     * <p>对象键 {@code qa/images/{userId}/{yyyyMMdd}/{uuid32}{ext}}；
     * 公网 URL 由前端从 {@code putUrl} 去除签名串得到（与文章图片一致）。
     */
    @PostMapping("/presign-chat")
    public StoragePresignResponse presignChat(@Valid @RequestBody ChatImagePresignRequest request,
                                              @AuthenticationPrincipal Jwt jwt) {
        long userId = jwtService.extractUserId(jwt);
        String contentType = normalizeContentType(request.contentType());
        String ext = validateAndNormalize("knowpost_image", contentType, request.ext(), request.size());

        String date = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneId.of("UTC")).format(Instant.now());
        String rand = UUID.randomUUID().toString().replace("-", "");
        String objectKey = "qa/images/" + userId + "/" + date + "/" + rand + ext;

        int expiresIn = 600; // 10 分钟
        OssStorageService.DirectUploadGrant grant = ossStorageService.generatePresignedPost(
                objectKey, contentType, request.size(), expiresIn);
        return new StoragePresignResponse(
                objectKey, grant.uploadUrl(), ossStorageService.originUrl(objectKey),
                ossStorageService.generatePresignedGetUrl(objectKey), "POST", Map.of(),
                grant.formFields(), expiresIn);
    }

    private String validateAndNormalize(String scene, String contentType, String ext, long size) {
        if ("knowpost_content".equals(scene)) {
            if (!"text/markdown".equals(contentType)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "正文仅支持 Markdown");
            }
            requireSizeWithin(size, ossProperties.getMaxPostContentBytes());
            requireExtension(ext, ".md");
            return ".md";
        }
        if (!"knowpost_image".equals(scene)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不支持的上传场景");
        }
        requireSizeWithin(size, ossProperties.getMaxImageBytes());
        return switch (contentType) {
            case "image/jpeg" -> {
                requireExtension(ext, ".jpg", ".jpeg");
                yield ".jpg";
            }
            case "image/png" -> {
                requireExtension(ext, ".png");
                yield ".png";
            }
            case "image/webp" -> {
                requireExtension(ext, ".webp");
                yield ".webp";
            }
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "图片仅支持 JPEG、PNG 或 WebP");
        };
    }

    private void requireSizeWithin(long size, long max) {
        if (size <= 0 || size > max) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上传文件大小超出限制");
        }
    }

    private void requireExtension(String ext, String... allowed) {
        if (ext == null || ext.isBlank()) return;
        String normalized = ext.startsWith(".") ? ext.toLowerCase() : "." + ext.toLowerCase();
        for (String candidate : allowed) {
            if (candidate.equals(normalized)) return;
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "文件扩展名与 Content-Type 不匹配");
    }

    private String normalizeContentType(String contentType) {
        String normalized = contentType == null ? "" : contentType.trim().toLowerCase();
        int separator = normalized.indexOf(';');
        return separator < 0 ? normalized : normalized.substring(0, separator).trim();
    }
}
