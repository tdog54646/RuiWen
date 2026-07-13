package com.tongji.admin.mapper;

import com.tongji.admin.dto.AdminConversationItem;
import com.tongji.admin.dto.AdminMemoryItem;
import com.tongji.admin.dto.AdminMessageItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * AI 问答后台管理 Mapper（管理员旁路：不带 user_id 守卫，可跨用户审计会话/消息/记忆）。
 */
@Mapper
public interface AdminQaMapper {

    // ===== 会话 =====

    /** 会话列表：按标题关键字 / 用户筛选，可选是否含已删除，分页。 */
    List<AdminConversationItem> listConversations(@Param("keyword") String keyword,
                                                  @Param("userId") Long userId,
                                                  @Param("includeDeleted") boolean includeDeleted,
                                                  @Param("offset") int offset,
                                                  @Param("size") int size);

    long countConversations(@Param("keyword") String keyword,
                            @Param("userId") Long userId,
                            @Param("includeDeleted") boolean includeDeleted);

    /** 校验会话是否存在。 */
    Long findUserIdByConversation(@Param("id") long id);

    /** 管理员软删除会话（旁路）。 */
    void softDeleteConversation(@Param("id") long id);

    // ===== 消息 =====

    /** 会话内消息列表（按时间正序，分页）。 */
    List<AdminMessageItem> listMessages(@Param("conversationId") long conversationId,
                                        @Param("offset") int offset,
                                        @Param("size") int size);

    long countMessages(@Param("conversationId") long conversationId);

    /** 管理员删除单条消息（硬删除，用于违规内容清理）。 */
    void deleteMessage(@Param("id") long id);

    // ===== 用户记忆 =====

    /** 记忆列表：按用户 / 来源(auto/manual) / 关键字筛选，分页。 */
    List<AdminMemoryItem> listMemories(@Param("keyword") String keyword,
                                       @Param("userId") Long userId,
                                       @Param("source") String source,
                                       @Param("offset") int offset,
                                       @Param("size") int size);

    long countMemories(@Param("keyword") String keyword,
                       @Param("userId") Long userId,
                       @Param("source") String source);

    /** 切换记忆启用状态。 */
    void updateMemoryEnabled(@Param("id") long id, @Param("enabled") boolean enabled);

    /** 删除记忆（硬删除）。 */
    void deleteMemory(@Param("id") long id);

    // ===== 仪表盘统计 =====

    long countConversationsAll();

    long countMessagesAll();

    long countMemoriesAll();
}
