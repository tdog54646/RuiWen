package com.tongji.user.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 第三方账号关联实体。
 *
 * <p>记录本站用户与 OAuth 提供方（如 Google）的绑定关系：
 * 同一用户在同一个提供方下唯一对应一个外部账号，通过 {@code (provider, providerUserId)} 唯一约束保证。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserOauth {
    private Long id;
    private Long userId;
    /** OAuth 提供方标识：google / ... */
    private String provider;
    /** 提供方返回的用户唯一 ID（Google 为 sub）。 */
    private String providerUserId;
    private Instant createdAt;
}
