package com.tongji.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 后台管理相关配置，绑定前缀 {@code admin.*}。
 *
 * <p>当前仅含 {@link Bootstrap}：启动时自动将指定手机号/邮箱的用户提升为超级管理员，
 * 用于在系统无任何管理员时引导第一个 SUPER_ADMIN。</p>
 */
@Data
@ConfigurationProperties(prefix = "admin")
public class AdminProperties {

    /** 第一个超级管理员的引导配置。 */
    private final Bootstrap bootstrap = new Bootstrap();

    @Data
    public static class Bootstrap {
        /** 是否启用启动时自动提升。 */
        private boolean enabled = false;
        /** 被提升为超级管理员的手机号或邮箱（按邮箱优先、其次手机号匹配）。 */
        private String identifier = "";
    }
}
