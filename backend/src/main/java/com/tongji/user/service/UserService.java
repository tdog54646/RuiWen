package com.tongji.user.service;

import com.tongji.user.domain.User;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 用户领域服务接口。
 */
public interface UserService {

    Optional<User> findByPhone(String phone);

    Optional<User> findByEmail(String email);

    Optional<User> findById(long id);

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);

    User createUser(User user);

    void updatePassword(User user);

    // ===================== 后台管理 =====================

    /** 更新用户角色。 */
    void updateRole(long id, String role);

    /** 更新用户状态（封禁/解封）。 */
    void updateStatus(long id, String status);

    /** 统计指定角色的用户数。 */
    long countByRole(String role);

    /** 统计全部用户数。 */
    long countAll();

    /** 统计某时间点之后注册的用户数。 */
    long countCreatedSince(Instant since);

    /**
     * 后台用户列表搜索。
     *
     * @param keyword 关键字（昵称/手机号/邮箱，可为空）。
     * @param role    角色筛选（可为空）。
     * @param status  状态筛选（可为空）。
     * @param page    页码（从 1 起）。
     * @param size    每页大小。
     * @return 用户列表。
     */
    List<User> searchUsers(String keyword, String role, String status, int page, int size);

    /** searchUsers 对应的计数。 */
    long countSearchUsers(String keyword, String role, String status);
}
