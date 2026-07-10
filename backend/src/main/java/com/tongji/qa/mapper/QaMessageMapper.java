package com.tongji.qa.mapper;

import com.tongji.qa.model.QaMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface QaMessageMapper {

    void insert(QaMessage message);

    QaMessage findById(@Param("id") Long id);

    /** 某会话最近 N 条消息（按时间倒序取，service 端反转为正序再喂给 LLM） */
    List<QaMessage> listRecentByConversation(@Param("conversationId") Long conversationId,
                                             @Param("limit") int limit);

    /** 某会话历史消息（正序，前端历史回看） */
    List<QaMessage> listByConversation(@Param("conversationId") Long conversationId,
                                       @Param("limit") int limit,
                                       @Param("offset") int offset);

    /** 某用户最近 N 条消息（跨会话，记忆总结用，倒序） */
    List<QaMessage> listRecentByUser(@Param("userId") Long userId,
                                     @Param("limit") int limit);
}
