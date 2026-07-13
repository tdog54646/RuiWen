package com.tongji.admin.mapper;

import com.tongji.admin.dto.AdminKnowPostItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 知文后台管理 Mapper（管理员旁路：不带 creator_id 守卫，可操作任意知文）。
 */
@Mapper
public interface AdminKnowPostMapper {

    /** 查询某知文是否存在（用于操作前校验）。 */
    Long findCreatorIdById(@Param("id") long id);

    /** 后台知文列表：按关键字（标题）/状态/可见性/作者筛选，分页，按创建时间倒序。 */
    List<AdminKnowPostItem> listPage(@Param("keyword") String keyword,
                                     @Param("status") String status,
                                     @Param("visible") String visible,
                                     @Param("creatorId") Long creatorId,
                                     @Param("offset") int offset,
                                     @Param("size") int size);

    /** listPage 对应计数。 */
    long countPage(@Param("keyword") String keyword,
                   @Param("status") String status,
                   @Param("visible") String visible,
                   @Param("creatorId") Long creatorId);

    /** 管理员设置可见性（不校验作者）。 */
    void updateVisibility(@Param("id") long id, @Param("visible") String visible);

    /** 管理员设置置顶（不校验作者）。 */
    void updateTop(@Param("id") long id, @Param("isTop") boolean isTop);

    /** 管理员软删除（不校验作者）。 */
    void softDelete(@Param("id") long id);

    /** 统计全部知文数。 */
    long countAll();

    /** 统计已发布知文数。 */
    long countPublished();
}
