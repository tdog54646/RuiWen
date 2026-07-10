package com.tongji.knowpost.export;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 知文导出 PDF 相关配置。
 *
 * <p>关键难点是中文字体：openhtmltopdf 不会自动使用系统字体，
 * 必须显式注册一个支持 CJK 的字体文件，否则中文会渲染为空白方块。
 * 因此提供 {@code font-search-paths}：按顺序查找第一个存在的字体文件并注册，
 * 覆盖容器（Alpine 的 font-noto-cjk）与本地开发（macOS PingFang / Arial Unicode）等场景。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.pdf")
public class PdfProperties {

    /**
     * 字体查找路径，按顺序取第一个存在的文件注册为 {@link #fontFamily}。
     * 优先选择 TrueType（含 glyf 表）字体：PDFBox 2.0 可对其子集化，PDF 体积小；
     * CFF 轮廓字体（Noto CJK）无法子集化，作为回退（整字体内嵌）。
     */
    private List<String> fontSearchPaths = List.of(
            // 容器：WenQuanYi 正黑（TrueType，可子集化，PDF 体积小）
            "/usr/share/fonts/wqy-zenhei/wqy-zenhei.ttc",
            // 容器：Noto CJK（CFF 轮廓，无法子集化，回退用）
            "/usr/share/fonts/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/noto/NotoSansCJKsc-Regular.otf",
            // 本地开发：macOS（Arial Unicode 为 TrueType，可子集化）
            "/System/Library/Fonts/Supplemental/Arial Unicode.ttf",
            "/System/Library/Fonts/PingFang.ttc"
    );

    /**
     * 注册字体时使用的 CSS font-family 名称，HTML 默认样式引用该名称。
     */
    private String fontFamily = "RuiWen";

    /**
     * 正文 Markdown 中远程图片转内嵌 data URI 的抓取超时（秒）。
     */
    private int imageFetchTimeoutSeconds = 5;
}
