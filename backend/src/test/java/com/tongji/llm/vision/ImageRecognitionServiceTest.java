package com.tongji.llm.vision;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * {@link ImageRecognitionService} 集成测试：直连 QVQ MaaS 流式接口验证 SSE 解析与图片识别。
 * <p>需提供 API Key（{@code DASHSCOPE_API_KEY} 或 {@code OPENAI_API_KEY}）才执行，否则跳过--避免 CI 无凭证时报错。
 */
class ImageRecognitionServiceTest {

    private static final String SAMPLE_IMAGE =
            "https://img.alicdn.com/imgextra/i1/O1CN01gDEY8M1W114Hi3XcN_!!6000000002727-0-tps-1024-406.jpg";

    private ImageRecognitionService service;

    @BeforeEach
    void setUp() {
        String apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("OPENAI_API_KEY");
        }
        assumeTrue(apiKey != null && !apiKey.isBlank(), "no QVQ api key in env");

        VisionProperties props = new VisionProperties();
        props.setApiKey(apiKey);
        service = new ImageRecognitionService(props, new ObjectMapper());
        service.init();
    }

    @Test
    void recognize_returnsNonEmptyDescriptionForKnownImage() {
        String result = service.recognize(List.of(SAMPLE_IMAGE), "一句话描述这张图片的内容。");
        System.out.println("QVQ recognize result: " + result);
        assertFalse(result.isBlank(), "识别结果不应为空");
        assertTrue(!result.startsWith("（图片识别"),
                "识别应成功而非降级，但实际: " + result);
    }

    @Test
    void recognize_supportsMultipleImages() {
        String result = service.recognize(List.of(SAMPLE_IMAGE, SAMPLE_IMAGE), "简述这两张图片");
        System.out.println("QVQ multi-image result: " + result);
        assertFalse(result.isBlank(), "多图识别结果不应为空");
        assertTrue(!result.startsWith("（图片识别"),
                "多图识别应成功而非降级，但实际: " + result);
    }

    @Test
    void recognize_degradesWhenUrlBlank() {
        String result = service.recognize(List.of(""), "describe");
        assertTrue(result.startsWith("（"), "空 URL 应返回降级提示: " + result);
    }
}
