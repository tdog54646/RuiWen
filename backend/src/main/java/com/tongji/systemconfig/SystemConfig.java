package com.tongji.systemconfig;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 系统配置实体（system_config 表）。
 * <p>key-value 存储，用于注册策略、密码策略、站点公告、特性开关等可热更新的配置。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemConfig {

    private String configKey;
    private String configValue;
    private String description;
    private Long updatedBy;
    private Instant updatedAt;
}
