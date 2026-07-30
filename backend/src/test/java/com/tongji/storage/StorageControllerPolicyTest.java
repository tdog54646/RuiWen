package com.tongji.storage;

import com.tongji.auth.exception.BusinessException;
import com.tongji.auth.token.JwtService;
import com.tongji.knowpost.mapper.KnowPostMapper;
import com.tongji.knowpost.model.KnowPost;
import com.tongji.storage.api.StorageController;
import com.tongji.storage.api.dto.StoragePresignRequest;
import com.tongji.storage.config.OssProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class StorageControllerPolicyTest {

    private OssStorageService storage;
    private JwtService jwtService;
    private KnowPostMapper mapper;
    private StorageController controller;
    private Jwt jwt;

    @BeforeEach
    void setUp() {
        storage = mock(OssStorageService.class);
        jwtService = mock(JwtService.class);
        mapper = mock(KnowPostMapper.class);
        OssProperties properties = new OssProperties();
        controller = new StorageController(storage, jwtService, mapper, properties);
        jwt = mock(Jwt.class);
        when(jwtService.extractUserId(jwt)).thenReturn(7L);
        when(storage.generatePresignedPost(anyString(), anyString(), anyLong(), anyInt()))
                .thenReturn(new OssStorageService.DirectUploadGrant("https://put.example", java.util.Map.of()));
        when(storage.originUrl(anyString())).thenReturn("https://origin.example/object");
        when(storage.generatePresignedGetUrl(anyString())).thenReturn("https://read.example/object");
    }

    @Test
    void contentUploadUsesVersionedMarkdownKeyAndBindsExpectedLength() {
        when(mapper.findById(12L)).thenReturn(KnowPost.builder()
                .id(12L).creatorId(7L).status("published").build());

        var response = controller.presign(
                new StoragePresignRequest("knowpost_content", "12", "text/markdown", ".md", 123L), jwt);

        assertTrue(response.objectKey().matches("posts/12/content-[0-9a-f]{32}\\.md"));
        verify(storage).generatePresignedPost(response.objectKey(), "text/markdown", 123L, 600);
    }

    @Test
    void rejectsDeletedPostAndUnsupportedMimeOrOversizedImage() {
        when(mapper.findById(12L)).thenReturn(KnowPost.builder()
                .id(12L).creatorId(7L).status("deleted").build());
        assertThrows(BusinessException.class, () -> controller.presign(
                new StoragePresignRequest("knowpost_content", "12", "text/markdown", ".md", 10L), jwt));

        when(mapper.findById(12L)).thenReturn(KnowPost.builder()
                .id(12L).creatorId(7L).status("draft").build());
        assertThrows(BusinessException.class, () -> controller.presign(
                new StoragePresignRequest("knowpost_image", "12", "image/svg+xml", ".svg", 10L), jwt));
        assertThrows(BusinessException.class, () -> controller.presign(
                new StoragePresignRequest("knowpost_image", "12", "image/png", ".png", 11L * 1024 * 1024), jwt));
    }

    @Test
    void postPolicyBindsExactObjectSize() {
        OssProperties properties = new OssProperties();
        properties.setEndpoint("oss-cn-hangzhou.aliyuncs.com");
        properties.setBucket("ruiwen-test");
        properties.setAccessKeyId("test-id");
        properties.setAccessKeySecret("test-secret");
        OssStorageService realStorage = new OssStorageService(properties);

        var grant = realStorage.generatePresignedPost(
                "posts/12/content-" + "a".repeat(32) + ".md", "text/markdown", 123L, 600);
        String policy = new String(java.util.Base64.getDecoder().decode(grant.formFields().get("policy")),
                java.nio.charset.StandardCharsets.UTF_8);

        assertEquals("https://ruiwen-test.oss-cn-hangzhou.aliyuncs.com", grant.uploadUrl());
        assertTrue(policy.contains("content-length-range"));
        assertTrue(policy.contains("123"));
    }
}
