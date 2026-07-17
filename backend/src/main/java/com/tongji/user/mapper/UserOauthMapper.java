package com.tongji.user.mapper;

import com.tongji.user.domain.UserOauth;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 第三方账号关联 Mapper。
 */
@Mapper
public interface UserOauthMapper {

    /**
     * 按提供方与提供方用户 ID 查询绑定关系。
     *
     * @param provider       提供方标识（如 google）。
     * @param providerUserId 提供方用户唯一 ID。
     * @return 绑定关系；不存在时返回 null。
     */
    UserOauth findByProviderAndProviderUserId(@Param("provider") String provider,
                                              @Param("providerUserId") String providerUserId);

    /**
     * 新建一条绑定关系。
     *
     * @param userOauth 绑定关系实体。
     */
    void insert(UserOauth userOauth);
}
