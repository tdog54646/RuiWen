package com.tongji.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 安全配置。
 * <p>
 * - 关闭 CSRF（后端纯 API，使用 JWT 无会话）；
 * - 启用 CORS，当前允许所有来源（后续需替换白名单）；
 * - 无状态会话；
 * - 公开认证相关接口与健康检查，其余接口需鉴权；
 * - 资源服务器启用 JWT 校验。
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtRoleAuthenticationConverter jwtRoleAuthenticationConverter;

    public SecurityConfig(JwtRoleAuthenticationConverter jwtRoleAuthenticationConverter) {
        this.jwtRoleAuthenticationConverter = jwtRoleAuthenticationConverter;
    }

    /**
     * 配置 Spring Security 过滤链。
     *
     * <p>主要包含：</p>
     * - 关闭 CSRF；
     * - 启用 CORS；
     * - 使用无状态会话策略；
     * - 公开认证接口与健康检查，其余接口需鉴权；
     * - 后台管理接口 {@code /api/admin/**} 仅 ADMIN/SUPER_ADMIN 可访问；
     * - 启用资源服务器的 JWT 校验，并把 role claim 映射为权限。
     *
     * @param http Spring 的 {@link HttpSecurity} 构建器。
     * @return 构建完成的 {@link SecurityFilterChain}。
     * @throws Exception 构建过滤链过程中可能抛出的异常。
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new CustomAuthenticationEntryPoint()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // 公开内容：首页 Feed 不需要登录
                        .requestMatchers("/api/knowposts/feed").permitAll()
                        // 知文详情（公开已发布内容，非公开由服务层校验）
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/knowposts/detail/*").permitAll()
                        // 知文导出 PDF（可见性由服务层校验，匿名可导出公开知文）
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/knowposts/*/export/pdf").permitAll()
                        .requestMatchers("/api/leaderboards/top").permitAll()
                        // 公开注册策略查询：注册页首屏需匿名读取当前注册模式
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/auth/registration-config").permitAll()
                        // WebSocket 端点：浏览器 WS 无法设 Authorization 头，鉴权由 JwtHandshakeInterceptor 处理
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers(
                                "/api/auth/send-code",
                                "/api/auth/register",
                                "/api/auth/login",
                                "/api/auth/google",
                                "/api/auth/token/refresh",
                                "/api/auth/logout",
                                "/api/auth/password/reset"
                        ).permitAll()
                        // 后台管理接口：仅 ADMIN / SUPER_ADMIN 可访问（SUPER_ADMIN 同时拥有 ROLE_ADMIN）
                        .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                        .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt
                        .jwtAuthenticationConverter(jwtRoleAuthenticationConverter)));
        return http.build();
    }

    /**
     * 定义并提供 CORS 配置源。
     *
     * <p>当前允许所有来源（后续建议替换为产品白名单），允许常见方法与请求头，且不携带凭证。</p>
     *
     * @return {@link CorsConfigurationSource}，用于为所有路径注册 CORS 规则。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("*")); // TODO replace with product whitelist
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        configuration.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
