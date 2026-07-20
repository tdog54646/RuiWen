package com.tongji.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.io.Resource;

import java.time.Duration;

/**
 * 认证相关配置属性，绑定前缀 {@code auth.*}。
 *
 * <p>包含以下分组：</p>
 * - Jwt：令牌签发与验证配置；
 * - Verification：验证码发送与校验配置；
 * - Password：密码策略与加密强度配置；
 * - OAuth：第三方登录配置（Google 等）。
 */
@Data
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    /** JWT 配置项。 */
    private final Jwt jwt = new Jwt();
    /** 验证码配置项。 */
    private final Verification verification = new Verification();
    /** 密码策略配置项。 */
    private final Password password = new Password();
    /** 第三方登录（OAuth）配置项。 */
    private final OAuth oauth = new OAuth();

    @Data
    public static class Jwt {
        /** JWT 签发者标识（iss）。 */
        private String issuer = "ruiwen";
        /** 访问令牌有效期（TTL）。 */
        private Duration accessTokenTtl = Duration.ofMinutes(15);
        /** 刷新令牌有效期（TTL）。 */
        private Duration refreshTokenTtl = Duration.ofDays(7);
        /** JWK 密钥标识（kid），用于下游校验与轮换。 */
        private String keyId = "ruiwen-key";
        /** RSA 私钥 PEM（PKCS#8）资源。 */
        private Resource privateKey;
        /** RSA 公钥 PEM（X.509）资源。 */
        private Resource publicKey;
    }

    /**
     * 验证码配置：位数、有效期、最大尝试次数、发送间隔与每日上限。
     */
    @Data
    public static class Verification {
        /** 验证码位数。 */
        private int codeLength = 6;
        /** 验证码有效时间。 */
        private Duration ttl = Duration.ofMinutes(5);
        /** 最大校验尝试次数。 */
        private int maxAttempts = 5;
        /** 同标识连续发送的最小间隔。 */
        private Duration sendInterval = Duration.ofSeconds(60);
        /** 同标识每日发送上限。 */
        private int dailyLimit = 10;
    }

    /** 密码策略配置。 */
    @Data
    public static class Password {
        /** 密码哈希强度（BCrypt cost）。 */
        private int bcryptStrength = 12;
        /** 密码最小长度。 */
        private int minLength = 8;
    }

    /** 第三方登录（OAuth）配置。 */
    @Data
    public static class OAuth {
        /** Google 登录配置。 */
        private final Google google = new Google();
    }

    /** Google OAuth 登录配置。 */
    @Data
    public static class Google {
        /** Google OAuth Client ID，用于校验 ID Token 的 audience。 */
        private String clientId;
        /** Google JWKS 地址，默认官方；国内服务器可通过自建反代覆盖。 */
        private String jwksUri = "https://www.googleapis.com/oauth2/v3/certs";
        /** 出墙代理（国内服务器拉 Google 公钥用）；不配置则直连。 */
        private final ProxyConfig proxy = new ProxyConfig();
    }

    /** HTTP/SOCKS 代理与超时配置。 */
    @Data
    public static class ProxyConfig {
        /** 代理主机，留空则不走代理（直连）。 */
        private String host;
        /** 代理端口。 */
        private int port;
        /** 代理类型：HTTP（默认）或 SOCKS。 */
        private String type = "HTTP";
        /** 连接超时。 */
        private Duration connectTimeout = Duration.ofSeconds(5);
        /** 读取超时。 */
        private Duration readTimeout = Duration.ofSeconds(10);
    }
}
