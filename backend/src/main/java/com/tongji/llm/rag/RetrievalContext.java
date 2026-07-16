package com.tongji.llm.rag;

/**
 * 检索隔离上下文：携带当前用户 ID 与检索范围，用于在检索层做用户隔离过滤。
 *
 * <p>隔离规则（与 {@code HybridSearchService.buildIsolationFilter} 对应）：
 * <ul>
 *   <li>{@link Scope#ALL}：{@code visible==public} OR（{@code creatorId==userId} AND {@code visible!=public}）</li>
 *   <li>{@link Scope#PRIVATE}：{@code creatorId==userId} AND {@code visible!=public}</li>
 * </ul>
 */
public record RetrievalContext(long userId, Scope scope) {

    /** 检索范围。 */
    public enum Scope { ALL, PRIVATE }

    public static RetrievalContext of(long userId, Scope scope) {
        return new RetrievalContext(userId, scope == null ? Scope.ALL : scope);
    }

    /**
     * 从请求字符串解析 scope，非法/空值降级为 {@link Scope#ALL}。
     */
    public static Scope parseScope(String raw) {
        if (raw == null) return Scope.ALL;
        return "private".equalsIgnoreCase(raw.trim()) ? Scope.PRIVATE : Scope.ALL;
    }
}
