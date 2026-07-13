package com.tongji.admin.config;

import com.tongji.admin.service.AdminSettingsService;
import com.tongji.auth.config.AuthProperties;
import com.tongji.systemconfig.SystemConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 启动时把 system_config 中持久化的可覆盖配置重新应用到内存。
 * <p>当前仅密码最小长度：管理员通过后台修改后写入 system_config，重启后由本加载器恢复。</p>
 */
@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class SystemSettingsLoader implements ApplicationRunner {

    private final SystemConfigMapper systemConfigMapper;
    private final AuthProperties authProperties;

    @Override
    public void run(ApplicationArguments args) {
        String raw = systemConfigMapper.findValueByKey(AdminSettingsService.KEY_PASSWORD_MIN_LENGTH);
        if (raw == null || raw.isBlank()) {
            return;
        }
        try {
            int min = Integer.parseInt(raw.trim());
            if (min >= 6 && min <= 64) {
                authProperties.getPassword().setMinLength(min);
                log.info("[SystemSettings] 已恢复密码最小长度配置：{}", min);
            }
        } catch (NumberFormatException ex) {
            log.warn("[SystemSettings] 密码最小长度配置非法，忽略：{}", raw);
        }
    }
}
