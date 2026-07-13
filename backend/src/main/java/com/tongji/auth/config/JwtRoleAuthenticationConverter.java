package com.tongji.auth.config;

import com.tongji.auth.token.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.convert.converter.Converter;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;

/**
 * JWT → 认证令牌转换器：把 access token 中的 {@code role} claim 映射为
 * {@code ROLE_<role>} 形式的 {@link GrantedAuthority}，使方法级/路径级权限校验可用。
 * <p>
 * 旧令牌（无 role claim）回退为 {@code ROLE_USER}，保证过渡期普通用户不受影响。
 * SUPER_ADMIN 同时授予 ROLE_ADMIN 与 ROLE_SUPER_ADMIN，便于用 hasAnyRole/hasRole 统一表达。
 */
@Component
@RequiredArgsConstructor
public class JwtRoleAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtService jwtService;

    @Override
    @NonNull
    public AbstractAuthenticationToken convert(@NonNull Jwt jwt) {
        String role = jwtService.extractRole(jwt);
        return new JwtAuthenticationToken(jwt, authoritiesOf(role), jwt.getSubject());
    }

    /**
     * 根据角色构造权限集合。
     * <ul>
     *   <li>SUPER_ADMIN：同时拥有 ROLE_ADMIN 与 ROLE_SUPER_ADMIN。</li>
     *   <li>其余角色：仅 ROLE_<role>。</li>
     * </ul>
     *
     * @param role 角色字符串。
     * @return 权限集合。
     */
    private Collection<GrantedAuthority> authoritiesOf(String role) {
        if ("SUPER_ADMIN".equals(role)) {
            return List.of(
                    new SimpleGrantedAuthority("ROLE_ADMIN"),
                    new SimpleGrantedAuthority("ROLE_SUPER_ADMIN"));
        }
        return List.of(new SimpleGrantedAuthority("ROLE_" + role));
    }
}
