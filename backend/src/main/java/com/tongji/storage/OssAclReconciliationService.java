package com.tongji.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPostDetailRow;
import com.tongji.storage.config.OssProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 启动后校准历史文章对象 ACL，避免旧数据仍保留公共读权限。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OssAclReconciliationService {

    private final KnowPostMapper knowPostMapper;
    private final OssStorageService ossStorageService;
    private final OssProperties properties;
    private final ObjectMapper objectMapper;

    @EventListener(ApplicationReadyEvent.class)
    public void reconcileAfterStartup() {
        if (!properties.isAclReconcileOnStartup() || !ossStorageService.isConfigured()) {
            return;
        }
        CompletableFuture.runAsync(this::reconcileAll)
                .exceptionally(error -> {
                    log.error("OSS ACL 历史数据校准失败", error);
                    return null;
                });
    }

    void reconcileAll() {
        List<Long> ids = knowPostMapper.listAllIds();
        int succeeded = 0;
        for (Long id : ids) {
            try {
                KnowPostDetailRow row = knowPostMapper.findDetailById(id);
                if (row == null) continue;
                boolean publicRead = "published".equals(row.getStatus())
                        && "public".equals(row.getVisible());
                String contentReference = row.getContentObjectKey() == null
                        ? row.getContentUrl() : row.getContentObjectKey();
                ossStorageService.setPostAssetsAccess(
                        contentReference, parseImages(row.getImgUrls()), publicRead);
                succeeded++;
            } catch (Exception error) {
                log.warn("OSS ACL 校准失败 postId={}: {}", id, error.getMessage());
            }
        }
        log.info("OSS ACL 历史数据校准完成 total={} succeeded={}", ids.size(), succeeded);
    }

    private List<String> parseImages(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ignored) {
            return List.of();
        }
    }
}
