package com.tongji.knowpost.export;

import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.URI;
import java.util.List;

import com.tongji.storage.config.OssProperties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PdfExportServiceSecurityTest {

    @Test
    void rejectsLoopbackPrivateLinkLocalAndUniqueLocalAddresses() throws Exception {
        assertFalse(PdfExportService.isPublicAddress(InetAddress.getByName("127.0.0.1")));
        assertFalse(PdfExportService.isPublicAddress(InetAddress.getByName("10.0.0.1")));
        assertFalse(PdfExportService.isPublicAddress(InetAddress.getByName("169.254.169.254")));
        assertFalse(PdfExportService.isPublicAddress(InetAddress.getByName("fc00::1")));
    }

    @Test
    void allowsPublicUnicastAddresses() throws Exception {
        assertTrue(PdfExportService.isPublicAddress(InetAddress.getByName("8.8.8.8")));
        assertTrue(PdfExportService.isPublicAddress(InetAddress.getByName("2606:4700:4700::1111")));
    }

    @Test
    void allowlistStillCannotAuthorizePrivateNetworkAndUnlistedHostsAreRejected() throws Exception {
        PdfProperties properties = new PdfProperties();
        properties.setAllowedImageHosts(List.of("127.0.0.1", "8.8.8.8"));
        PdfExportService service = new PdfExportService(properties, new OssProperties());

        assertThrows(IllegalArgumentException.class,
                () -> service.validateRemoteUri(URI.create("http://127.0.0.1/image.png")));
        assertThrows(IllegalArgumentException.class,
                () -> service.validateRemoteUri(URI.create("https://1.1.1.1/image.png")));
        service.validateRemoteUri(URI.create("https://8.8.8.8/image.png"));
    }

    @Test
    void rendersMinimalDocumentAsPdf() {
        PdfExportService service = new PdfExportService(new PdfProperties(), new OssProperties());

        byte[] pdf = service.renderPost(
                "功能测试", "测试用户", "2026-07-30",
                List.of("P1"), "摘要", "# 正文\n\nPDF 导出回归测试");

        assertTrue(pdf.length > 4);
        assertTrue(new String(pdf, 0, 4, java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF"));
    }
}
