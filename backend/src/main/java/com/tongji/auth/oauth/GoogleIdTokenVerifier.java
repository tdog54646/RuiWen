package com.tongji.auth.oauth;

import com.tongji.auth.config.AuthProperties;
import com.tongji.auth.exception.BusinessException;
import com.tongji.auth.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestOperations;
import org.springframework.web.client.RestTemplate;

import java.net.InetSocketAddress;

/**
 * Google ID Token 校验器。
 *
 * <p>使用 Google 的 JWKS 公钥（RS256）在本地校验前端传来的 ID Token：
 * 校验签名、签发者（iss）、过期（exp/not-before）以及受众（aud 必须为本站 Client ID）。
 * 校验通过后解析出 Google 用户身份。</p>
 *
 * <p>国内服务器无法直连 googleapis.com，可通过 {@code auth.oauth.google.proxy} 配置出墙代理；
 * 也可用 {@code auth.oauth.google.jwks-uri} 指向自建反代。解码器懒初始化并复用 JWKS 缓存，
 * 拉取公钥带超时，避免网络不通时挂起请求。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleIdTokenVerifier {

    private static final String ISSUER = "https://accounts.google.com";

    private final AuthProperties authProperties;

    /** 解码器懒初始化：避免 Client ID 未配置时启动失败，且复用 JWKS 缓存。 */
    private volatile NimbusJwtDecoder decoder;

    /**
     * 校验 ID Token 并解析出 Google 用户身份。
     *
     * @param idToken 前端 GIS 回调拿到的 credential（ID Token）。
     * @return Google 用户身份（sub / email / emailVerified / name / picture）。
     * @throws BusinessException 当未配置 Client ID、令牌非法或校验失败时抛出。
     */
    public GoogleIdentity verify(String idToken) {
        AuthProperties.Google google = authProperties.getOauth().getGoogle();
        if (!StringUtils.hasText(google.getClientId())) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Google 登录未配置");
        }
        try {
            Jwt jwt = decoder(google).decode(idToken);
            boolean emailVerified = Boolean.TRUE.equals(jwt.getClaim("email_verified"));
            return new GoogleIdentity(
                    jwt.getSubject(),
                    jwt.getClaimAsString("email"),
                    emailVerified,
                    jwt.getClaimAsString("name"),
                    jwt.getClaimAsString("picture")
            );
        } catch (JwtException ex) {
            log.warn("Google ID Token 校验失败: {} | cause: {}", ex.getMessage(),
                    ex.getCause() == null ? "" : ex.getCause().getMessage());
            throw new BusinessException(ErrorCode.OAUTH_TOKEN_INVALID);
        }
    }

    /**
     * 懒初始化 JWT 解码器，叠加 issuer 与 audience 校验。
     */
    private NimbusJwtDecoder decoder(AuthProperties.Google google) {
        NimbusJwtDecoder local = decoder;
        if (local == null) {
            synchronized (this) {
                local = decoder;
                if (local == null) {
                    NimbusJwtDecoder created = NimbusJwtDecoder.withJwkSetUri(google.getJwksUri())
                            .jwsAlgorithm(SignatureAlgorithm.RS256)
                            .restOperations(buildRestOperations(google.getProxy()))
                            .build();
                    OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                            JwtValidators.createDefaultWithIssuer(ISSUER),
                            new AudienceValidator(google.getClientId()));
                    created.setJwtValidator(validator);
                    local = created;
                    decoder = created;
                }
            }
        }
        return local;
    }

    /**
     * 构建带超时（与可选出墙代理）的 RestOperations，用于拉取 JWKS。
     *
     * @param proxy 代理配置；host 为空则直连。
     * @return 配置好的 {@link RestOperations}。
     */
    private RestOperations buildRestOperations(AuthProperties.ProxyConfig proxy) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) proxy.getConnectTimeout().toMillis());
        factory.setReadTimeout((int) proxy.getReadTimeout().toMillis());
        if (StringUtils.hasText(proxy.getHost()) && proxy.getPort() > 0) {
            java.net.Proxy.Type type = "SOCKS".equalsIgnoreCase(proxy.getType())
                    ? java.net.Proxy.Type.SOCKS
                    : java.net.Proxy.Type.HTTP;
            factory.setProxy(new java.net.Proxy(type, new InetSocketAddress(proxy.getHost(), proxy.getPort())));
        }
        return new RestTemplate(factory);
    }

    /**
     * 受众校验：ID Token 的 aud 必须包含本站 Client ID，防止为他站签发的令牌被本站接受。
     */
    private record AudienceValidator(String clientId) implements OAuth2TokenValidator<Jwt> {
        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            if (token.getAudience() != null && token.getAudience().contains(clientId)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Invalid audience", null));
        }
    }
}
