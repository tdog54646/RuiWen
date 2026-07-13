package com.tongji.user.mapper;

import com.tongji.user.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

@Mapper
public interface UserMapper {

    User findByPhone(@Param("phone") String phone);

    User findByEmail(@Param("email") String email);

    boolean existsByPhone(@Param("phone") String phone);

    boolean existsByEmail(@Param("email") String email);

    void insert(User user);

    User findById(@Param("id") Long id);

    void updatePassword(@Param("id") Long id, @Param("passwordHash") String passwordHash);

    void updateProfile(User user);

    boolean existsByZgIdExceptId(@Param("zgId") String zgId, @Param("excludeId") Long excludeId);

    List<User> listByIds(@Param("ids") List<Long> ids);

    // ===================== 后台管理 =====================

    /** 更新用户角色。 */
    void updateRole(@Param("id") Long id, @Param("role") String role);

    /** 更新用户状态（封禁/解封）。 */
    void updateStatus(@Param("id") Long id, @Param("status") String status);

    /** 统计指定角色的用户数。 */
    long countByRole(@Param("role") String role);

    /** 统计全部用户数。 */
    long countAll();

    /** 统计某时间点之后注册的用户数（用于今日/近 7 日新增）。 */
    long countCreatedSince(@Param("since") Instant since);

    /**
     * 后台用户列表搜索：关键字（昵称/手机号/邮箱）+ 角色 + 状态筛选，分页。
     */
    List<User> searchUsers(@Param("keyword") String keyword,
                           @Param("role") String role,
                           @Param("status") String status,
                           @Param("offset") int offset,
                           @Param("size") int size);

    /** searchUsers 对应的计数。 */
    long countSearchUsers(@Param("keyword") String keyword,
                          @Param("role") String role,
                          @Param("status") String status);
}
