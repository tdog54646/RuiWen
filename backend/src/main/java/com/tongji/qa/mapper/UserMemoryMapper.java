package com.tongji.qa.mapper;

import com.tongji.qa.model.UserMemory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMemoryMapper {

    void insert(UserMemory memory);

    void batchInsert(@Param("list") List<UserMemory> list);

    UserMemory findById(@Param("id") Long id);

    /** 该用户全部记忆条目（含禁用），按 category 排序 */
    List<UserMemory> listByUser(@Param("userId") Long userId);

    /** 该用户已启用记忆条目（注入 prompt 用） */
    List<UserMemory> listEnabledByUser(@Param("userId") Long userId);

    int update(UserMemory memory);

    int delete(@Param("id") Long id, @Param("userId") Long userId);

    /** 删除该用户全部 AI 自动生成的条目（重新生成用，保留 manual） */
    int deleteAutoByUser(@Param("userId") Long userId);
}
