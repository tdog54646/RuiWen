package com.tongji.auth.oauth;

import com.tongji.auth.config.AuthProperties;
import com.tongji.auth.exception.BusinessException;
import com.tongji.auth.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
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

/**
 * Google ID Token 校验器。
 *
 * <p>使用 Google 的 JWKS 公钥（RS256）在本地校验前端传来的 ID Token：
 * 校验签名、签发者（iss）、过期（exp/not-before）以及受众（aud 必须为本站 Client ID）。
 * 校验通过后解析出 Google 用户身份。</p>
 *
 * <p>采用 {@code withJwkSetUri} 构建解码器，不在启动时联网；首次解码时按需拉取并缓存 Google 公钥。</p>
 */
@Component
@RequiredArgsConstructor
public class GoogleIdTokenVerifier {

    private static final String ISSUER = "https://accounts.google.com";
    private static final String JWKS_URI = "https://www.googleapis.com/oauth2/v3/certs";

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
        String clientId = authProperties.getOauth().getGoogle().getClientId();
        if (!StringUtils.hasText(clientId)) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "Google 登录未配置");
        }
        try {
            Jwt jwt = decoder(clientId).decode(idToken);
            boolean emailVerified = Boolean.TRUE.equals(jwt.getClaim("email_verified"));
            return new GoogleIdentity(
                    jwt.getSubject(),
                    jwt.getClaimAsString("email"),
                    emailVerified,
                    jwt.getClaimAsString("name"),
                    jwt.getClaimAsString("picture")
            );
        } catch (JwtException ex) {
            throw new BusinessException(ErrorCode.OAUTH_TOKEN_INVALID);
        }
    }

    /**
     * 懒初始化 JWT 解码器，叠加 issuer 与 audience 校验。
     *
     * @param clientId 本站 Google Client ID，用于 audience 校验。
     * @return 配置好的 {@link NimbusJwtDecoder}。
     */
    private NimbusJwtDecoder decoder(String clientId) {
        NimbusJwtDecoder local = decoder;
        if (local == null) {
            synchronized (this) {
                local = decoder;
                if (local == null) {
                    NimbusJwtDecoder created = NimbusJwtDecoder.withJwkSetUri(JWKS_URI)
                            .jwsAlgorithm(SignatureAlgorithm.RS256)
                            .build();
                    OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                            JwtValidators.createDefaultWithIssuer(ISSUER),
                            new AudienceValidator(clientId));
                    created.setJwtValidator(validator);
                    local = created;
                    decoder = created;
                }
            }
        }
        return local;
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
