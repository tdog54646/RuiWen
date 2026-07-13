package com.tongji.user.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    private Long id;
    private String phone;
    private String email;
    private String passwordHash;
    private String nickname;
    private String avatar;
    private String bio;
    private String zgId;
    private String gender;
    private LocalDate birthday;
    private String school;
    private String tagsJson;
    /** 角色：USER / ADMIN / SUPER_ADMIN，新建用户默认 USER（依赖 DB DEFAULT 与 createUser 兜底）。 */
    @Builder.Default
    private String role = UserRole.USER;
    /** 状态：ACTIVE / BANNED，新建用户默认 ACTIVE。 */
    @Builder.Default
    private String status = UserStatus.ACTIVE;
    private Instant createdAt;
    private Instant updatedAt;
}
