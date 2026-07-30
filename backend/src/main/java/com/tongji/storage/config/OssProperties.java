package com.tongji.storage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "oss")
public class OssProperties {
    private String endpoint;
    private String accessKeyId;
    private String accessKeySecret;
    private String bucket;
    private String publicDomain; // 可选：如自定义 CDN 域名
    private String folder = "avatars"; // 默认上传目录
    private int readUrlTtlSeconds = 3600;
    /**
     * 历史对象 ACL 一次性迁移开关。默认关闭，避免每次应用重启都全表扫描并请求 OSS；
     * 仅在需要迁移存量数据时通过 OSS_ACL_RECONCILE_ON_STARTUP=true 临时启用。
     */
    private boolean aclReconcileOnStartup = false;
    private long maxPostContentBytes = 2L * 1024 * 1024;
    private long maxImageBytes = 10L * 1024 * 1024;
}
