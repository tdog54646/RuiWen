package com.tongji.auth.audit;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

@Mapper
public interface LoginLogMapper {

    void insert(LoginLog log);

    // ===================== 后台审计 =====================

    /**
     * 后台审计列表查询：按 userId/identifier/status/channel/时间范围筛选，分页。
     */
    List<LoginLog> listPage(@Param("userId") Long userId,
                            @Param("identifier") String identifier,
                            @Param("status") String status,
                            @Param("channel") String channel,
                            @Param("start") Instant start,
                            @Param("end") Instant end,
                            @Param("offset") int offset,
                            @Param("size") int size);

    /** listPage 对应的计数。 */
    long countPage(@Param("userId") Long userId,
                   @Param("identifier") String identifier,
                   @Param("status") String status,
                   @Param("channel") String channel,
                   @Param("start") Instant start,
                   @Param("end") Instant end);

    /** 统计某时间点之后的登录成功数（用于仪表盘"今日登录"）。 */
    long countSince(@Param("since") Instant since);
}
