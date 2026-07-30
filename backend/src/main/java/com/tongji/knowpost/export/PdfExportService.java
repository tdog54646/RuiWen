package com.tongji.knowpost.export;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder.FontStyle;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.util.XRLog;
import com.tongji.storage.config.OssProperties;
import lombok.RequiredArgsConstructor;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将知文（Markdown 正文 + 元数据）渲染为 PDF 字节。
 *
 * <p>流程：Markdown → HTML（commonmark，支持 GFM 表格 / 删除线）→
 * 远程图片抓取为内嵌 data URI（超时即跳过，避免 openhtmltopdf 阻塞）→
 * 套用中文字体与排版样式 → openhtmltopdf 输出 PDF。</p>
 */
@Service
@RequiredArgsConstructor
public class PdfExportService {

    private static final Logger log = LoggerFactory.getLogger(PdfExportService.class);

    private static final Pattern IMG_SRC =
            Pattern.compile("<img[^>]+src\\s*=\\s*\"(https?://[^\"]+)\"");

    private final PdfProperties props;
    private final OssProperties ossProperties;

    private volatile Parser parser;
    private volatile HtmlRenderer renderer;
    private volatile HttpClient httpClient;

    /**
     * 渲染单篇知文为 PDF。
     *
     * @param title      标题
     * @param author     作者昵称（可空）
     * @param dateText   发布日期文本（可空）
     * @param tags       标签列表（可空）
     * @param description 摘要（可空）
     * @param markdown   正文 Markdown
     * @return PDF 字节
     */
    public byte[] renderPost(String title, String author, String dateText,
                             List<String> tags, String description, String markdown) {
        String bodyHtml = markdownToHtml(markdown == null ? "" : markdown);
        bodyHtml = embedRemoteImages(bodyHtml);
        String html = wrapHtml(title, author, dateText, tags, description, bodyHtml);

        File fontFile = resolveFont();
        if (fontFile == null) {
            log.warn("未找到可用的 CJK 字体，PDF 中文可能无法正常显示。可通过 app.pdf.font-search-paths 配置。");
        }
        // PDFBox 2.0 无法 subset CFF 轮廓字体（会抛 OTF fonts do not have a glyf table），
        // 因此仅对 TrueType 字体启用子集化以缩小体积；CFF 字体关闭子集化（整字体内嵌）。
        boolean subset = fontFile != null && !isCffFont(fontFile);
        try {
            return doRender(html, fontFile, subset);
        } catch (Exception e) {
            if (subset && fontFile != null) {
                // 子集化异常（如 TTC TrueType 子集化的边缘情况）时，回退整字体内嵌再试一次
                log.warn("PDF 子集化渲染失败，回退关闭子集化重试：{}", e.getMessage());
                return doRender(html, fontFile, false);
            }
            throw e;
        }
    }

    private byte[] doRender(String html, File fontFile, boolean subset) {
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        if (fontFile != null) {
            try {
                builder.useFont(fontFile, props.getFontFamily(), 400, FontStyle.NORMAL, subset);
                log.info("PDF 字体 font={} subset={}", fontFile.getName(), subset);
            } catch (Exception e) {
                log.warn("注册 PDF 字体失败 font={}, 将使用默认字体：{}", fontFile.getAbsolutePath(), e.getMessage());
            }
        }

        try {
            // 关闭 openhtmltopdf 的冗长日志
            XRLog.setLoggingEnabled(false);
        } catch (Exception ignored) {
        }

        // 用 jsoup 把 HTML 规整为合法 DOM 再交给 openhtmltopdf，避免其严格 XML 解析在遇到
        // 未闭合的 void 标签（<meta>/<img>/<br>）或正文中裸 HTML 时抛 SAXParseException。
        Document w3cDoc = new W3CDom().fromJsoup(Jsoup.parse(html));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        builder.withW3cDocument(w3cDoc, null);
        builder.toStream(out);
        try {
            builder.run();
        } catch (Exception e) {
            throw new IllegalStateException("生成 PDF 失败：" + e.getMessage(), e);
        }
        return out.toByteArray();
    }

    private String markdownToHtml(String markdown) {
        if (parser == null) {
            synchronized (this) {
                if (parser == null) {
                    List<Extension> extensions = List.of(
                            TablesExtension.create(),
                            StrikethroughExtension.create()
                    );
                    parser = Parser.builder().extensions(extensions).build();
                    renderer = HtmlRenderer.builder().extensions(extensions).build();
                }
            }
        }
        return renderer.render(parser.parse(markdown));
    }

    /**
     * 把 HTML 中远程 http(s) 图片转换为 data URI 内嵌进 PDF。
     * 抓取超时或失败则保留原 src——openhtmltopdf 未注册 HttpStreamFactory，会静默跳过，不会阻塞。
     */
    private String embedRemoteImages(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        Map<String, String> cache = new HashMap<>();
        Matcher matcher = IMG_SRC.matcher(html);
        StringBuilder sb = new StringBuilder();
        int fetchedImages = 0;
        long embeddedCharacters = 0;
        long maxEmbeddedCharacters = (long) props.getMaxEmbeddedImageBytesTotal() * 4 / 3 + 1024;
        while (matcher.find()) {
            String url = matcher.group(1);
            String replacement;
            if (cache.containsKey(url)) {
                replacement = cache.get(url);
            } else if (fetchedImages >= props.getMaxRemoteImages()) {
                replacement = null;
                cache.put(url, null);
            } else {
                fetchedImages++;
                replacement = fetchAsDataUri(url);
                cache.put(url, replacement);
            }
            // 同一图片被重复引用时也会重复写入 data URI，因此按每次输出累计总量。
            if (replacement != null
                    && embeddedCharacters + replacement.length() > maxEmbeddedCharacters) {
                replacement = null;
            } else if (replacement != null) {
                embeddedCharacters += replacement.length();
            }
            if (replacement == null) {
                // 下载失败或地址不安全时清空 src，避免后续渲染器再次尝试访问原始 URL。
                replacement = matcher.group(0).replace(url, "");
            } else {
                replacement = matcher.group(0).replace(url, replacement);
            }
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String fetchAsDataUri(String url) {
        try {
            HttpClient client = httpClient();
            URI current = URI.create(url);
            int redirects = 0;
            while (true) {
                validateRemoteUri(current);
                HttpRequest req = HttpRequest.newBuilder(current)
                        .timeout(Duration.ofSeconds(props.getImageFetchTimeoutSeconds()))
                        .header("Accept", "image/*")
                        .GET().build();
                HttpResponse<InputStream> resp = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
                int status = resp.statusCode();
                if (isRedirect(status)) {
                    try (InputStream ignored = resp.body()) {
                        if (redirects++ >= props.getMaxImageRedirects()) {
                            return null;
                        }
                        String location = resp.headers().firstValue("location").orElse(null);
                        if (location == null) {
                            return null;
                        }
                        current = current.resolve(location);
                        continue;
                    }
                }
                if (status / 100 != 2) {
                    try (InputStream ignored = resp.body()) {
                        return null;
                    }
                }
                String mime = resp.headers().firstValue("content-type")
                        .orElse("").split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
                if (!mime.startsWith("image/")) {
                    try (InputStream ignored = resp.body()) {
                        return null;
                    }
                }
                long declaredLength = resp.headers().firstValueAsLong("content-length").orElse(-1L);
                if (declaredLength > props.getMaxImageBytes()) {
                    try (InputStream ignored = resp.body()) {
                        return null;
                    }
                }
                byte[] body;
                try (InputStream input = resp.body()) {
                    body = readLimited(input, props.getMaxImageBytes());
                }
                String base64 = Base64.getEncoder().encodeToString(body);
                return "data:" + mime + ";base64," + base64;
            }
        } catch (Exception e) {
            log.debug("内嵌图片失败 url={}：{}", url, e.getMessage());
            return null;
        }
    }

    void validateRemoteUri(URI uri) throws Exception {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!("http".equals(scheme) || "https".equals(scheme))
                || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("不允许的图片地址");
        }
        if (!isAllowedImageHost(uri.getHost())) {
            throw new IllegalArgumentException("图片域名不在允许列表");
        }
        InetAddress[] addresses = InetAddress.getAllByName(uri.getHost());
        if (addresses.length == 0) {
            throw new IllegalArgumentException("图片域名无法解析");
        }
        for (InetAddress address : addresses) {
            if (!isPublicAddress(address)) {
                throw new IllegalArgumentException("图片地址指向非公网 IP");
            }
        }
    }

    private boolean isAllowedImageHost(String host) {
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        String endpointHost = configuredHost(ossProperties.getEndpoint());
        if (endpointHost != null && ossProperties.getBucket() != null
                && normalizedHost.equals((ossProperties.getBucket() + "." + endpointHost).toLowerCase(Locale.ROOT))) {
            return true;
        }
        String publicHost = configuredHost(ossProperties.getPublicDomain());
        if (publicHost != null && normalizedHost.equals(publicHost.toLowerCase(Locale.ROOT))) {
            return true;
        }
        if (props.getAllowedImageHosts() == null || props.getAllowedImageHosts().isEmpty()) {
            return false;
        }
        for (String configured : props.getAllowedImageHosts()) {
            if (configured == null || configured.isBlank()) continue;
            String allowed = configured.trim().toLowerCase(Locale.ROOT);
            if (allowed.startsWith("*.")) {
                String suffix = allowed.substring(2);
                if (normalizedHost.endsWith("." + suffix)) return true;
            } else if (normalizedHost.equals(allowed)) {
                return true;
            }
        }
        return false;
    }

    private static String configuredHost(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return URI.create(value.contains("://") ? value : "https://" + value).getHost();
        } catch (Exception ignored) {
            return null;
        }
    }

    static boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address && bytes.length == 4) {
            int a = Byte.toUnsignedInt(bytes[0]);
            int b = Byte.toUnsignedInt(bytes[1]);
            if (a == 0 || a == 10 || a == 127 || a >= 224) return false;
            if (a == 100 && b >= 64 && b <= 127) return false;
            if (a == 169 && b == 254) return false;
            if (a == 172 && b >= 16 && b <= 31) return false;
            if (a == 192 && b == 168) return false;
            return !(a == 198 && (b == 18 || b == 19));
        }
        if (address instanceof Inet6Address && bytes.length == 16) {
            int first = Byte.toUnsignedInt(bytes[0]);
            return (first & 0xfe) != 0xfc;
        }
        return false;
    }

    private static byte[] readLimited(InputStream input, int maxBytes) throws Exception {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("图片大小限制必须大于 0");
        }
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maxBytes, 64 * 1024));
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maxBytes) {
                throw new IllegalArgumentException("远程图片超过大小限制");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private HttpClient httpClient() {
        if (httpClient == null) {
            synchronized (this) {
                if (httpClient == null) {
                    httpClient = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(props.getImageFetchTimeoutSeconds()))
                            .followRedirects(HttpClient.Redirect.NEVER)
                            .build();
                }
            }
        }
        return httpClient;
    }

    private File resolveFont() {
        if (props.getFontSearchPaths() == null) {
            return null;
        }
        for (String path : props.getFontSearchPaths()) {
            if (path == null || path.isBlank()) continue;
            File f = new File(path);
            if (f.isFile()) {
                return f;
            }
        }
        return null;
    }

    /**
     * 判断字体是否为 CFF 轮廓（兼容 TTC 容器内的字体）。
     * PDFBox 2.0 无法 subset CFF 字体，需据此关闭子集化。
     * 通过读取 sfnt 表目录：有 'glyf' 表为 TrueType，有 'CFF ' 表为 CFF OpenType。
     */
    private boolean isCffFont(File font) {
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(font, "r")) {
            long fontOffset = 0;
            if (raf.length() >= 16) {
                byte[] head = new byte[4];
                raf.readFully(head);
                if (head[0] == 't' && head[1] == 't' && head[2] == 'c' && head[3] == 'f') {
                    // TTC 容器：跳过 version(4)+numFonts(4)，读取第一个字体的偏移
                    raf.skipBytes(4);
                    raf.skipBytes(4);
                    fontOffset = Integer.toUnsignedLong(raf.readInt());
                }
            }
            raf.seek(fontOffset);
            raf.skipBytes(4);                  // sfnt version
            int numTables = raf.readUnsignedShort();
            raf.skipBytes(6);                  // searchRange + entrySelector + rangeShift
            boolean hasGlyf = false;
            boolean hasCff = false;
            for (int i = 0; i < numTables; i++) {
                byte[] t = new byte[4];
                raf.readFully(t);
                raf.skipBytes(12);             // checksum + offset + length
                if (t[0] == 'g' && t[1] == 'l' && t[2] == 'y' && t[3] == 'f') hasGlyf = true;
                if (t[0] == 'C' && t[1] == 'F' && t[2] == 'F' && t[3] == ' ') hasCff = true;
            }
            return hasCff && !hasGlyf;
        } catch (Exception e) {
            log.debug("字体类型探测失败 font={}：{}", font.getAbsolutePath(), e.getMessage());
            return true; // 探测失败保守按 CFF 处理（关闭子集化，避免崩溃）
        }
    }

    private String escape(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String wrapHtml(String title, String author, String dateText,
                            List<String> tags, String description, String bodyHtml) {
        StringBuilder head = new StringBuilder();
        head.append("<h1 class=\"post-title\">").append(escape(title)).append("</h1>");

        StringBuilder meta = new StringBuilder();
        if (author != null && !author.isBlank()) {
            meta.append("<span>").append(escape(author)).append("</span>");
        }
        if (dateText != null && !dateText.isBlank()) {
            if (meta.length() > 0) meta.append("<span class=\"sep\">·</span>");
            meta.append("<span>").append(escape(dateText)).append("</span>");
        }
        if (tags != null && !tags.isEmpty()) {
            StringBuilder tagHtml = new StringBuilder();
            for (String tag : tags) {
                if (tag == null || tag.isBlank()) continue;
                tagHtml.append("<span class=\"tag\">#").append(escape(tag)).append("</span>");
            }
            if (tagHtml.length() > 0) {
                if (meta.length() > 0) meta.append("<span class=\"sep\">·</span>");
                meta.append("<span class=\"tags\">").append(tagHtml).append("</span>");
            }
        }
        if (meta.length() > 0) {
            head.append("<div class=\"meta\">").append(meta).append("</div>");
        }

        if (description != null && !description.isBlank()) {
            head.append("<div class=\"description\">").append(escape(description)).append("</div>");
        }

        return "<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"UTF-8\">"
                + "<style>" + buildCss() + "</style></head><body>"
                + head
                + "<div class=\"body\">" + bodyHtml + "</div>"
                + "</body></html>";
    }

    /** PDF 排版样式：A4 页边距、CJK 字体、标题/段落/代码/表格/图片/引用。 */
    private String buildCss() {
        return """
                @page { size: A4; margin: 2cm 1.8cm; }
                * { box-sizing: border-box; }
                body {
                    font-family: '%s', sans-serif;
                    font-size: 11pt;
                    line-height: 1.7;
                    color: #1f2937;
                }
                .post-title { font-size: 22pt; line-height: 1.3; margin: 0 0 8pt 0; color: #111827; }
                .meta { font-size: 9.5pt; color: #6b7280; margin-bottom: 6pt; }
                .meta .sep { margin: 0 6px; color: #d1d5db; }
                .meta .tags .tag { margin-right: 6px; color: #7c3aed; }
                .description {
                    font-size: 10.5pt; color: #4b5563; background: #f9fafb;
                    border-left: 3px solid #a78bfa; padding: 8px 12px; margin: 10pt 0 16pt 0;
                }
                .body h1, .body h2, .body h3, .body h4 { color: #111827; line-height: 1.35; margin-top: 18pt; }
                .body h1 { font-size: 17pt; }
                .body h2 { font-size: 15pt; border-bottom: 1px solid #e5e7eb; padding-bottom: 3px; }
                .body h3 { font-size: 13pt; }
                .body p { margin: 8pt 0; }
                .body a { color: #2563eb; text-decoration: none; }
                .body ul, .body ol { margin: 8pt 0; padding-left: 22px; }
                .body li { margin: 3pt 0; }
                .body blockquote {
                    margin: 10pt 0; padding: 6px 12px; color: #4b5563;
                    background: #f3f4f6; border-left: 3px solid #9ca3af;
                }
                .body code {
                    font-family: monospace; font-size: 9.5pt; background: #f3f4f6;
                    padding: 1px 4px; border-radius: 3px; color: #be185d;
                }
                .body pre {
                    font-family: monospace; font-size: 9pt; background: #1f2937; color: #e5e7eb;
                    padding: 10px 12px; border-radius: 6px; line-height: 1.5;
                }
                .body pre code { background: transparent; color: inherit; padding: 0; }
                .body img { max-width: 100%%; height: auto; margin: 8pt 0; }
                .body hr { border: none; border-top: 1px solid #e5e7eb; margin: 14pt 0; }
                .body table { border-collapse: collapse; width: 100%%; margin: 10pt 0; font-size: 10pt; }
                .body th, .body td { border: 1px solid #d1d5db; padding: 6px 10px; text-align: left; }
                .body th { background: #f9fafb; }
                """.formatted(props.getFontFamily());
    }
}
