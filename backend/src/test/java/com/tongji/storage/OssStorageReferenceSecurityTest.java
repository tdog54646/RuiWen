package com.tongji.storage;

import com.tongji.auth.exception.BusinessException;
import com.tongji.storage.config.OssProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OssStorageReferenceSecurityTest {

    private OssStorageService service;

    @BeforeEach
    void setUp() {
        OssProperties properties = new OssProperties();
        properties.setEndpoint("oss-cn-hangzhou.aliyuncs.com");
        properties.setBucket("ruiwen-test");
        properties.setPublicDomain("https://cdn.example.com");
        service = new OssStorageService(properties);
    }

    @Test
    void chatImageMustBelongToCurrentUsersPrefix() {
        String otherUsersImage = "https://ruiwen-test.oss-cn-hangzhou.aliyuncs.com/qa/images/8/a.png";

        assertThrows(BusinessException.class,
                () -> service.canonicalChatImageReference(otherUsersImage, 7L));
    }

    @Test
    void articleImagesAreRestrictedToCurrentPostAndNormalizedToOrigin() {
        String ownCdnImage = "https://cdn.example.com/posts/12/images/20260730/a.png?token=old";
        assertEquals(
                List.of("https://ruiwen-test.oss-cn-hangzhou.aliyuncs.com/posts/12/images/20260730/a.png"),
                service.canonicalPostImageReferences(List.of(ownCdnImage), 12L));

        assertThrows(BusinessException.class,
                () -> service.canonicalPostImageReferences(
                        List.of("posts/13/images/20260730/a.png"), 12L));
    }
}
