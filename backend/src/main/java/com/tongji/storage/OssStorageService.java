package com.tongji.storage;

import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.CannedAccessControlList;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.PutObjectRequest;
import com.aliyun.oss.model.PutObjectResult;
import com.aliyun.oss.model.PolicyConditions;
import com.tongji.auth.exception.BusinessException;
import com.tongji.auth.exception.ErrorCode;
import com.tongji.storage.api.dto.ContentUploadResult;
import com.tongji.storage.config.OssProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Date;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
@RequiredArgsConstructor
public class OssStorageService {

    private final OssProperties props;

    public String uploadAvatar(long userId, MultipartFile file) {
        ensureConfigured();

        String original = file.getOriginalFilename();
        String ext = "";
        if (original != null && original.contains(".")) {
            ext = original.substring(original.lastIndexOf('.'));
        }
        String objectKey = props.getFolder() + "/" + userId + "-" + Instant.now().toEpochMilli() + ext;

        OSS client = new OSSClientBuilder().build(props.getEndpoint(), props.getAccessKeyId(), props.getAccessKeySecret());

        try {
            PutObjectRequest request = new PutObjectRequest(props.getBucket(), objectKey, file.getInputStream());
            client.putObject(request);
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "头像文件读取失败");
        } finally {
            client.shutdown();
        }

        return publicUrl(objectKey);
    }

    /**
     * 服务端直接上传文章 Markdown 正文到 OSS（用于 AI 录入文章）。
     * objectKey 规则与前端直传一致：posts/{postId}/content.md。
     *
     * @param postId   文章 ID
     * @param markdown 正文 Markdown（UTF-8）
     * @return 含 objectKey/etag/size/sha256 的上传结果
     */
    public ContentUploadResult uploadPostContent(long postId, String markdown) {
        ensureConfigured();
        String objectKey = "posts/" + postId + "/content.md";
        byte[] bytes = markdown.getBytes(StandardCharsets.UTF_8);
        if (bytes.length == 0 || bytes.length > props.getMaxPostContentBytes()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "正文大小超出限制");
        }

        OSS client = new OSSClientBuilder().build(props.getEndpoint(), props.getAccessKeyId(), props.getAccessKeySecret());
        try {
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentType("text/markdown");
            metadata.setContentLength(bytes.length);
            metadata.setObjectAcl(CannedAccessControlList.Private);
            PutObjectRequest request = new PutObjectRequest(props.getBucket(), objectKey,
                    new ByteArrayInputStream(bytes), metadata);
            PutObjectResult result = client.putObject(request);
            return new ContentUploadResult(objectKey, originUrl(objectKey), result.getETag(), bytes.length, sha256Hex(bytes));
        } finally {
            client.shutdown();
        }
    }

    private static String sha256Hex(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }

    public String publicUrl(String objectKey) {
        if (props.getPublicDomain() != null && !props.getPublicDomain().isBlank()) {
            return props.getPublicDomain().replaceAll("/$", "") + "/" + objectKey;
        }
        return originUrl(objectKey);
    }

    /** 绕过 CDN 的 OSS 源站稳定地址，ACL 变更后不会继续命中 CDN 旧缓存。 */
    public String originUrl(String objectKey) {
        String endpointHost = hostOf(props.getEndpoint());
        if (endpointHost == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "对象存储 endpoint 配置无效");
        }
        return "https://" + props.getBucket() + "." + endpointHost + "/" + objectKey;
    }

    /** 生成带精确 content-length-range 条件的表单直传授权。 */
    public DirectUploadGrant generatePresignedPost(String objectKey, String contentType, long expectedSize,
                                                   int expiresInSeconds) {
        ensureConfigured();
        OSS client = newClient();
        try {
            Date expiration = new Date(System.currentTimeMillis() + expiresInSeconds * 1000L);
            PolicyConditions conditions = new PolicyConditions();
            conditions.addConditionItem(PolicyConditions.COND_KEY, objectKey);
            conditions.addConditionItem(PolicyConditions.COND_CONTENT_TYPE, contentType);
            conditions.addConditionItem(PolicyConditions.COND_CONTENT_LENGTH_RANGE, expectedSize, expectedSize);
            conditions.addConditionItem(PolicyConditions.COND_SUCCESS_ACTION_STATUS, "200");
            conditions.addConditionItem("x-oss-object-acl", "private");
            String rawPolicy = client.generatePostPolicy(expiration, conditions);
            String encodedPolicy = Base64.getEncoder().encodeToString(rawPolicy.getBytes(StandardCharsets.UTF_8));
            String signature = client.calculatePostSignature(rawPolicy);
            Map<String, String> fields = new java.util.LinkedHashMap<>();
            fields.put("key", objectKey);
            fields.put("policy", encodedPolicy);
            fields.put("OSSAccessKeyId", props.getAccessKeyId());
            fields.put("Signature", signature);
            fields.put("Content-Type", contentType);
            fields.put("success_action_status", "200");
            fields.put("x-oss-object-acl", "private");
            String uploadUrl = "https://" + props.getBucket() + "." + hostOf(props.getEndpoint());
            return new DirectUploadGrant(uploadUrl, fields);
        } finally {
            client.shutdown();
        }
    }

    public record DirectUploadGrant(String uploadUrl, Map<String, String> formFields) {}

    /** 为私有对象生成短期 GET 地址。 */
    public String generatePresignedGetUrl(String objectKey) {
        ensureConfigured();
        OSS client = new OSSClientBuilder().build(props.getEndpoint(), props.getAccessKeyId(), props.getAccessKeySecret());
        try {
            Date expiration = new Date(System.currentTimeMillis() + props.getReadUrlTtlSeconds() * 1000L);
            return client.generatePresignedUrl(props.getBucket(), objectKey, expiration, HttpMethod.GET).toString();
        } finally {
            client.shutdown();
        }
    }

    /** 将本桶的稳定 URL/objectKey 转换为私有读签名；外部 URL 原样返回。 */
    public String privateReadUrl(String reference) {
        String objectKey = extractObjectKey(reference);
        return objectKey == null ? reference : generatePresignedGetUrl(objectKey);
    }

    /** 将本桶的签名 URL 归一为稳定对象 URL，便于数据库持久化。 */
    public String canonicalObjectUrl(String reference) {
        String objectKey = extractObjectKey(reference);
        return objectKey == null ? reference : publicUrl(objectKey);
    }

    /** 校验聊天图片确属当前用户目录，并返回适合持久化的 objectKey。 */
    public String canonicalChatImageReference(String reference, long userId) {
        String objectKey = extractObjectKey(reference);
        String requiredPrefix = "qa/images/" + userId + "/";
        if (objectKey == null || !objectKey.startsWith(requiredPrefix) || objectKey.contains("..")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "聊天图片地址无效或无权限");
        }
        return objectKey;
    }

    /** 校验聊天图片的真实对象元数据后返回可持久化 objectKey。 */
    public String validateChatImageUpload(String reference, long userId) {
        String objectKey = canonicalChatImageReference(reference, userId);
        verifyImageObject(objectKey);
        return objectKey;
    }

    /** 校验聊天图片归属后生成私有读签名。 */
    public String privateChatImageUrl(String reference, long userId) {
        return generatePresignedGetUrl(canonicalChatImageReference(reference, userId));
    }

    public String validatePostContentObjectKey(String objectKey, long postId) {
        String requiredPrefix = "posts/" + postId + "/";
        if (objectKey == null || !objectKey.startsWith(requiredPrefix)
                || !objectKey.matches("^posts/" + postId + "/content(?:-[0-9a-f]{32})?\\.md$")
                || objectKey.contains("..") || objectKey.contains("://")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "正文对象地址无效或无权限");
        }
        return objectKey;
    }

    /**
     * 对上传确认参数做服务端事实校验：大小、MIME、ETag 与 SHA-256 均以 OSS 对象为准。
     */
    public void verifyPostContentUpload(String objectKey, long postId, String claimedEtag,
                                        long claimedSize, String claimedSha256) {
        String validatedKey = validatePostContentObjectKey(objectKey, postId);
        ensureConfigured();
        OSS client = newClient();
        try {
            ObjectMetadata metadata = client.getObjectMetadata(props.getBucket(), validatedKey);
            requireObjectMetadata(metadata, "text/markdown", props.getMaxPostContentBytes());
            if (claimedSize != metadata.getContentLength()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "正文大小与已上传对象不一致");
            }
            if (!normalizeEtag(claimedEtag).equalsIgnoreCase(normalizeEtag(metadata.getETag()))) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "正文 ETag 与已上传对象不一致");
            }
            String actualSha = sha256Object(client, validatedKey, props.getMaxPostContentBytes());
            if (claimedSha256 == null || !actualSha.equalsIgnoreCase(claimedSha256.trim())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "正文 SHA-256 校验失败");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("OSS 正文确认校验失败 key={} err={}", validatedKey, e.getMessage());
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无法确认已上传正文");
        } finally {
            client.shutdown();
        }
    }

    public List<String> canonicalPostImageReferences(List<String> references, long postId) {
        if (references == null) return null;
        String requiredPrefix = "posts/" + postId + "/images/";
        return references.stream().map(reference -> {
            String key = extractObjectKey(reference);
            if (key == null || !key.startsWith(requiredPrefix) || key.contains("..")) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "文章图片地址无效或无权限");
            }
            return originUrl(key);
        }).toList();
    }

    /** 校验文章图片归属及 OSS 真实元数据，成功后返回稳定源站引用。 */
    public List<String> validatePostImageUploads(List<String> references, long postId) {
        List<String> canonical = canonicalPostImageReferences(references, postId);
        if (canonical == null) return null;
        for (String reference : canonical) {
            String key = extractObjectKey(reference);
            if (key == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "文章图片地址无效");
            }
            verifyImageObject(key);
        }
        return canonical;
    }

    private void verifyImageObject(String objectKey) {
        ensureConfigured();
        OSS client = newClient();
        try {
            ObjectMetadata metadata = client.getObjectMetadata(props.getBucket(), objectKey);
            String contentType = metadata.getContentType() == null ? "" : metadata.getContentType().toLowerCase();
            if (!Set.of("image/jpeg", "image/png", "image/webp").contains(contentType)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "已上传对象不是支持的图片类型");
            }
            requireObjectMetadata(metadata, contentType, props.getMaxImageBytes());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("OSS 图片校验失败 key={} err={}", objectKey, e.getMessage());
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无法确认已上传图片");
        } finally {
            client.shutdown();
        }
    }

    private void requireObjectMetadata(ObjectMetadata metadata, String expectedContentType, long maxBytes) {
        if (metadata == null || metadata.getContentLength() <= 0 || metadata.getContentLength() > maxBytes) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已上传对象大小超出限制");
        }
        String actualType = metadata.getContentType() == null ? "" : metadata.getContentType().trim().toLowerCase();
        if (!expectedContentType.equals(actualType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "已上传对象类型不匹配");
        }
    }

    private String sha256Object(OSS client, String objectKey, long maxBytes) throws IOException {
        try (OSSObject object = client.getObject(props.getBucket(), objectKey);
             InputStream input = object.getObjectContent()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            long total = 0L;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "已上传对象大小超出限制");
                }
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private String normalizeEtag(String etag) {
        if (etag == null) return "";
        String value = etag.trim();
        return value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")
                ? value.substring(1, value.length() - 1) : value;
    }

    /** 批量设置文章正文与图片的对象 ACL。 */
    public void setPostAssetsAccess(String contentReference, List<String> imageReferences, boolean publicRead) {
        ensureConfigured();
        Set<String> objectKeys = new LinkedHashSet<>();
        String contentObjectKey = extractObjectKey(contentReference);
        if (contentObjectKey != null) {
            objectKeys.add(contentObjectKey);
        }
        if (imageReferences != null) {
            for (String reference : imageReferences) {
                String key = extractObjectKey(reference);
                if (key != null) objectKeys.add(key);
            }
        }
        if (objectKeys.isEmpty()) return;
        OSS client = new OSSClientBuilder().build(props.getEndpoint(), props.getAccessKeyId(), props.getAccessKeySecret());
        try {
            CannedAccessControlList acl = publicRead
                    ? CannedAccessControlList.PublicRead
                    : CannedAccessControlList.Private;
            for (String key : objectKeys) {
                try {
                    client.setObjectAcl(props.getBucket(), key, acl);
                } catch (OSSException error) {
                    if ("NoSuchKey".equals(error.getErrorCode())) {
                        // 种子/历史数据可能只保留数据库记录而未实际上传对象；跳过不影响其他资源校准。
                        log.debug("跳过不存在的 OSS 对象 ACL 校准 key={}", key);
                        continue;
                    }
                    throw error;
                }
            }
        } finally {
            client.shutdown();
        }
    }

    public boolean isConfigured() {
        return props.getEndpoint() != null && !props.getEndpoint().isBlank()
                && props.getAccessKeyId() != null && !props.getAccessKeyId().isBlank()
                && props.getAccessKeySecret() != null && !props.getAccessKeySecret().isBlank()
                && props.getBucket() != null && !props.getBucket().isBlank();
    }

    private String extractObjectKey(String reference) {
        if (reference == null || reference.isBlank()) return null;
        String value = reference.trim();
        if (!value.contains("://")) {
            return value.startsWith("/") ? value.substring(1) : value;
        }
        try {
            URI uri = URI.create(value);
            if (!isOwnHost(uri.getHost())) return null;
            String path = uri.getRawPath();
            if (path == null || path.length() <= 1) return null;
            return URLDecoder.decode(path.substring(1), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean isOwnHost(String host) {
        if (host == null) return false;
        String endpointHost = hostOf(props.getEndpoint());
        if (endpointHost != null && host.equalsIgnoreCase(props.getBucket() + "." + endpointHost)) {
            return true;
        }
        String publicHost = hostOf(props.getPublicDomain());
        return publicHost != null && host.equalsIgnoreCase(publicHost);
    }

    private static String hostOf(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            URI uri = URI.create(value.contains("://") ? value : "https://" + value);
            return uri.getHost();
        } catch (Exception ignored) {
            return null;
        }
    }

    private void ensureConfigured() {
        if (!isConfigured()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "对象存储未配置");
        }
    }

    private OSS newClient() {
        return new OSSClientBuilder().build(props.getEndpoint(), props.getAccessKeyId(), props.getAccessKeySecret());
    }
}
