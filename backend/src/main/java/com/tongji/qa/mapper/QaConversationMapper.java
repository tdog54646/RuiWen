package com.tongji.qa.mapper;

import com.tongji.qa.model.QaConversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

@Mapper
public interface QaConversationMapper {

    void insert(QaConversation conversation);

    QaConversation findById(@Param("id") Long id);

    /** 查询并校验归属（排除软删） */
    QaConversation findOwnedById(@Param("id") Long id, @Param("userId") Long userId);

    /** 当前用户会话列表（排除软删，按最后活跃时间倒序） */
    List<QaConversation> listByUser(@Param("userId") Long userId,
                                    @Param("limit") int limit,
                                    @Param("offset") int offset);

    int updateTitle(@Param("id") Long id, @Param("userId") Long userId, @Param("title") String title);

    int softDelete(@Param("id") Long id, @Param("userId") Long userId);

    /** 消息计数 +1 并刷新最后消息时间 */
    int touchAfterMessage(@Param("id") Long id, @Param("now") Instant now);
}
