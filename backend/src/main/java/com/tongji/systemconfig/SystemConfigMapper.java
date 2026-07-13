package com.tongji.systemconfig;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SystemConfigMapper {

    /** 按 key 查询整行配置。 */
    SystemConfig findByKey(@Param("key") String key);

    /** 按 key 查询配置值（JSON 字符串）。 */
    String findValueByKey(@Param("key") String key);

    /**
     * 插入或更新（key 冲突时更新 value/description/updated_by）。
     *
     * @param config 配置实体。
     * @return 受影响行数。
     */
    int upsert(SystemConfig config);

    /** 更新已存在配置的值与操作人。 */
    void updateValue(@Param("key") String key,
                     @Param("value") String value,
                     @Param("updatedBy") Long updatedBy);
}
