package com.tongji.admin.config;

import com.tongji.user.domain.User;
import com.tongji.user.domain.UserRole;
import com.tongji.user.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Optional;

/**
 * 启动时引导第一个超级管理员。
 * <p>
 * 读取 {@code admin.bootstrap.identifier}（手机号或邮箱），若对应用户存在且当前不是
 * SUPER_ADMIN，则将其提升为 SUPER_ADMIN；用户不存在时仅告警，不中断启动。
 * <br>另可使用迁移脚本 {@code deploy/mysql/migration-admin.sql} 中的 SQL 手动提升。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@EnableConfigurationProperties(AdminProperties.class)
public class AdminBootstrapRunner implements ApplicationRunner {

    private final AdminProperties adminProperties;
    private final UserService userService;

    @Override
    public void run(ApplicationArguments args) {
        AdminProperties.Bootstrap bootstrap = adminProperties.getBootstrap();
        if (!bootstrap.isEnabled()) {
            return;
        }
        String identifier = bootstrap.getIdentifier();
        if (!StringUtils.hasText(identifier)) {
            log.warn("[AdminBootstrap] 已启用但未配置 admin.bootstrap.identifier，跳过");
            return;
        }
        String trimmed = identifier.trim();
        Optional<User> userOpt = userService.findByEmail(trimmed);
        if (userOpt.isEmpty()) {
            userOpt = userService.findByPhone(trimmed);
        }
        if (userOpt.isEmpty()) {
            log.warn("[AdminBootstrap] 未找到用户 [{}]，无法提升为超级管理员（请先注册该账号或改用手动 SQL）", trimmed);
            return;
        }
        User user = userOpt.get();
        if (UserRole.SUPER_ADMIN.equals(user.getRole())) {
            log.info("[AdminBootstrap] 用户 [{}] 已是超级管理员，无需提升", trimmed);
            return;
        }
        userService.updateRole(user.getId(), UserRole.SUPER_ADMIN);
        log.info("[AdminBootstrap] 已将用户 [{}] (id={}) 提升为超级管理员", trimmed, user.getId());
    }
}
