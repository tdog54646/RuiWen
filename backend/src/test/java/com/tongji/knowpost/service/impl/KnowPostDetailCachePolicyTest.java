package com.tongji.knowpost.service.impl;

import com.tongji.knowpost.model.KnowPostDetailRow;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowPostDetailCachePolicyTest {

    @Test
    void onlyPublishedPublicPostsAreCacheable() {
        assertTrue(KnowPostServiceImpl.isPubliclyCacheable(row("published", "public")));
        assertFalse(KnowPostServiceImpl.isPubliclyCacheable(row("published", "private")));
        assertFalse(KnowPostServiceImpl.isPubliclyCacheable(row("draft", "public")));
        assertFalse(KnowPostServiceImpl.isPubliclyCacheable(row("deleted", "public")));
    }

    private static KnowPostDetailRow row(String status, String visible) {
        KnowPostDetailRow row = new KnowPostDetailRow();
        row.setStatus(status);
        row.setVisible(visible);
        return row;
    }
}
