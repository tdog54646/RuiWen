package com.tongji.admin.dto;

import java.util.List;

/**
 * 后台通用分页结果。
 *
 * @param items  当前页数据。
 * @param total  总条数。
 * @param page   当前页码（从 1 起）。
 * @param size   每页大小。
 * @param <T>    数据元素类型。
 */
public record PageResult<T>(List<T> items, long total, int page, int size) {

    /**
     * 计算总页数。
     *
     * @return 总页数。
     */
    public int totalPages() {
        return size <= 0 ? 0 : (int) Math.ceil((double) total / size);
    }
}
