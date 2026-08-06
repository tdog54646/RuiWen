package com.tongji.llm.rag.search;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentSimilarityTest {

    @Test
    void detectsTemplateVariantsButKeepsDifferentTopics() {
        String first = "Spring Boot 通过自动配置简化应用搭建，EnableAutoConfiguration 是核心注解。"
                + "事务方法内部调用会绕过 Spring AOP 代理。";
        String variant = "学习笔记：Spring Boot 通过自动配置简化应用搭建，"
                + "EnableAutoConfiguration 是核心注解。事务方法内部调用会绕过 Spring AOP 代理。";
        String unrelated = "二分查找要求数据具有单调性，关键是设计边界收缩规则。";

        assertTrue(ContentSimilarity.isNearDuplicate(first, variant, 0.45));
        assertFalse(ContentSimilarity.isNearDuplicate(first, unrelated, 0.45));
    }
}
