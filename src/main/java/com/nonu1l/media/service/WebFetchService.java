package com.nonu1l.media.service;

import com.nonu1l.media.model.dto.WebFetchResultDTO;
import com.nonu1l.media.model.dto.WebFetchLinkDTO;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Agentic Web Search 专用 URL 抓取服务。
 *
 * <p>该服务负责在 LLM 自主选择 URL 后执行安全校验、HTTP 抓取、正文清洗与截断，
 * 避免工具直接访问内网或把过大的原始页面交给模型。</p>
 */
@Service
public class WebFetchService {

    private static final Logger log = LoggerFactory.getLogger(WebFetchService.class);
    private static final int DEFAULT_MAX_CHARS = 6000;
    private static final int MAX_CHARS = 12000;
    private static final int MAX_BYTES = 1024 * 1024;
    private static final int MAX_REDIRECTS = 3;
    private static final int TIMEOUT_MS = (int) Duration.ofSeconds(10).toMillis();
    private static final String USER_AGENT = "seen-app-agentic-fetch/1.0";
    private static final int MAX_LINKS = 40;
    private static final Pattern BLOCK_TAG_PATTERN = Pattern.compile(
            "^(p|div|li|ul|ol|br|h[1-6]|tr|td|th|table|section|article|main|blockquote)$");
    private static final Pattern BEARER_TOKEN_PATTERN = Pattern.compile(
            "(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+");

    /**
     * 抓取 URL 并返回结构化结果。
     *
     * @param url 目标 URL，仅支持 HTTP(S)
     * @param maxChars 返回给模型的最大字符数，可为空
     * @return 抓取结果；失败时包含错误原因
     */
    public WebFetchResultDTO fetch(String url, Integer maxChars) {
        int limit = clampMaxChars(maxChars);
        try {
            URI uri = normalizeAndValidate(url);
            return fetchFollowingRedirects(uri, limit, 0);
        } catch (Exception e) {
            log.warn("fetchWeb blocked '{}': {}", url, e.getMessage());
            return failure(url, 0, "", e.getMessage());
        }
    }

    /**
     * 抓取 URL 并只返回正文文本，兼容旧的 fetchWeb 工具和固定搜索流水线。
     *
     * @param url 目标 URL
     * @return 清洗后的文本，失败时为空字符串
     */
    public String fetchText(String url) {
        WebFetchResultDTO result = fetch(url, DEFAULT_MAX_CHARS);
        return result.error() == null ? result.text() : "";
    }

    /**
     * 执行 HTTP 请求并手动处理跳转，确保每次跳转后的目标地址都重新经过安全校验。
     *
     * @param uri 当前访问地址
     * @param maxChars 返回给模型的最大字符数
     * @param redirects 已跟随的跳转次数
     * @return 抓取并清洗后的结构化结果
     * @throws Exception 网络访问、地址校验或正文读取失败时抛出
     */
    private WebFetchResultDTO fetchFollowingRedirects(URI uri, int maxChars, int redirects) throws Exception {
        validateResolvedAddress(uri);
        HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
        conn.setInstanceFollowRedirects(false);
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept", "text/html,application/json,text/plain;q=0.9,*/*;q=0.5");

        int status = conn.getResponseCode();
        String contentType = Optional.ofNullable(conn.getContentType()).orElse("");
        if (isRedirect(status)) {
            if (redirects >= MAX_REDIRECTS) {
                return failure(uri.toString(), status, contentType, "redirect limit exceeded");
            }
            String location = conn.getHeaderField("Location");
            if (location == null || location.isBlank()) {
                return failure(uri.toString(), status, contentType, "redirect location is empty");
            }
            URI next = normalizeAndValidate(uri.resolve(location).toString());
            return fetchFollowingRedirects(next, maxChars, redirects + 1);
        }

        byte[] bytes = readLimited(status >= 400 ? conn.getErrorStream() : conn.getInputStream());
        boolean byteTruncated = bytes.length > MAX_BYTES;
        if (byteTruncated) {
            bytes = java.util.Arrays.copyOf(bytes, MAX_BYTES);
        }
        Charset charset = charsetFrom(contentType);
        String raw = new String(bytes, charset);
        CleanedText cleaned = clean(uri.toString(), contentType, raw, maxChars);
        boolean truncated = byteTruncated || cleaned.truncated();
        log.info("fetchWeb status={} chars={} truncated={} url={}",
                status, cleaned.text().length(), truncated, uri);
        String error = status >= 400 ? "HTTP " + status : null;
        boolean ok = error == null && !cleaned.text().isBlank();
        return new WebFetchResultDTO(ok, uri.toString(), status, contentType, cleaned.title(), cleaned.text(),
                cleaned.links(), truncated,
                error, ok ? null : "页面没有返回可用正文。");
    }

    /**
     * 规范化并校验模型传入的 URL，提前拒绝非 HTTP(S)、空 host 和 localhost 目标。
     *
     * @param rawUrl 原始 URL 字符串
     * @return 可继续访问的 URI
     */
    private URI normalizeAndValidate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new IllegalArgumentException("url is blank");
        }
        URI uri = URI.create(rawUrl.trim());
        String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase(Locale.ROOT) : "";
        if (!"http".equals(scheme) && !"https".equals(scheme)) {
            throw new IllegalArgumentException("only http and https are allowed");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("host is blank");
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if ("localhost".equals(normalizedHost) || normalizedHost.endsWith(".localhost")) {
            throw new IllegalArgumentException("localhost is not allowed");
        }
        return uri;
    }

    /**
     * 解析目标 host 并拒绝内网、本地、链路本地、云元数据和保留网段，降低 SSRF 风险。
     *
     * @param uri 已通过语法校验的目标 URI
     * @throws Exception DNS 解析失败或解析到受限地址时抛出
     */
    private void validateResolvedAddress(URI uri) throws Exception {
        InetAddress[] addresses = InetAddress.getAllByName(uri.getHost());
        for (InetAddress address : addresses) {
            if (isBlockedAddress(address)) {
                throw new IllegalArgumentException("private or local address is not allowed: " + uri.getHost());
            }
        }
    }

    /**
     * 判断 IP 是否属于模型不应访问的地址段。
     *
     * @param address 待判断的 IP 地址
     * @return 属于受限地址时返回 true
     */
    private boolean isBlockedAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        byte[] raw = address.getAddress();
        if (raw.length == 4) {
            return isBlockedIpv4(raw);
        }
        if (raw.length == 16 && isIpv4MappedIpv6(raw)) {
            return isBlockedIpv4(new byte[] { raw[12], raw[13], raw[14], raw[15] });
        }
        if (address instanceof Inet6Address) {
            byte first = raw[0];
            return (first & 0xfe) == 0xfc;
        }
        return false;
    }

    /**
     * 判断 IPv4 是否属于 CGNAT、云元数据、benchmark 或保留地址段。
     *
     * @param raw IPv4 的 4 字节表示
     * @return 属于额外受限地址段时返回 true
     */
    private boolean isBlockedIpv4(byte[] raw) {
        int a = raw[0] & 0xff;
        int b = raw[1] & 0xff;
        int c = raw[2] & 0xff;
        int d = raw[3] & 0xff;
        return (a == 100 && b >= 64 && b <= 127)
                || (a == 169 && b == 254 && c == 169 && d == 254)
                || (a == 198 && (b == 18 || b == 19))
                || a >= 240;
    }

    /**
     * 判断 IPv6 字节是否为 IPv4-mapped IPv6，避免 ::ffff:127.0.0.1 绕过 IPv4 规则。
     *
     * @param raw IPv6 的 16 字节表示
     * @return 是 IPv4-mapped IPv6 时返回 true
     */
    private boolean isIpv4MappedIpv6(byte[] raw) {
        if (raw.length != 16) {
            return false;
        }
        for (int i = 0; i < 10; i++) {
            if (raw[i] != 0) {
                return false;
            }
        }
        return (raw[10] & 0xff) == 0xff && (raw[11] & 0xff) == 0xff;
    }

    /**
     * 按上限读取响应体，额外多读 1 字节用于判断是否发生字节截断。
     *
     * @param stream 响应体输入流
     * @return 最多 MAX_BYTES + 1 字节的响应体
     * @throws Exception 读取失败时抛出
     */
    private byte[] readLimited(InputStream stream) throws Exception {
        if (stream == null) return new byte[0];
        try (stream) {
            return stream.readNBytes(MAX_BYTES + 1);
        }
    }

    /**
     * 根据内容类型清洗响应正文；HTML 保留标题、段落换行和可继续访问的链接编号。
     *
     * @param url 页面最终 URL，用于解析相对链接
     * @param contentType 响应内容类型
     * @param raw 原始响应文本
     * @param maxChars 返回给模型的最大字符数
     * @return 清洗结果，包含正文、标题、链接和截断状态
     */
    private CleanedText clean(String url, String contentType, String raw, int maxChars) {
        String normalizedContentType = contentType != null ? contentType.toLowerCase(Locale.ROOT) : "";
        String text;
        String title = "";
        List<WebFetchLinkDTO> links = List.of();
        if (normalizedContentType.contains("html") || looksLikeHtml(raw)) {
            Document doc = Jsoup.parse(raw, url);
            title = doc.title();
            doc.select("script,style,noscript,svg,canvas,header,footer,nav").remove();
            links = annotateLinks(doc);
            text = renderReadableText(doc);
        } else {
            text = normalizeTextLines(raw);
        }
        text = redactSensitiveText(text);
        boolean truncated = text.length() > maxChars;
        if (truncated) {
            text = text.substring(0, maxChars);
        }
        return new CleanedText(redactSensitiveText(title), text, links, truncated);
    }

    /**
     * 判断未知内容类型的响应是否看起来像 HTML。
     *
     * @param raw 原始响应文本
     * @return 有明显 HTML 页面特征时返回 true
     */
    private boolean looksLikeHtml(String raw) {
        String value = raw != null ? raw.stripLeading().toLowerCase(Locale.ROOT) : "";
        return value.startsWith("<!doctype html") || value.startsWith("<html") || value.contains("<body");
    }

    /**
     * 从 content-type 中解析 charset，无法识别时回退 UTF-8。
     *
     * @param contentType 响应 Content-Type
     * @return 用于解码响应体的字符集
     */
    private Charset charsetFrom(String contentType) {
        if (contentType == null) return StandardCharsets.UTF_8;
        for (String part : contentType.split(";")) {
            String trimmed = part.trim();
            if (trimmed.toLowerCase(Locale.ROOT).startsWith("charset=")) {
                try {
                    return Charset.forName(trimmed.substring("charset=".length()).trim());
                } catch (Exception ignored) {
                    return StandardCharsets.UTF_8;
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    /**
     * 判断 HTTP 状态码是否为需要手动跟随的跳转。
     *
     * @param status HTTP 状态码
     * @return 是 301/302/303/307/308 时返回 true
     */
    private boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    /**
     * 限制模型可请求的最大返回字符数，避免一次抓取塞入过多上下文。
     *
     * @param value 调用方请求的字符数
     * @return 应用默认值和上限后的字符数
     */
    private int clampMaxChars(Integer value) {
        if (value == null || value <= 0) return DEFAULT_MAX_CHARS;
        return Math.min(value, MAX_CHARS);
    }

    /**
     * 构造失败响应，保留错误事实并提示 Agent 可先搜索可访问页面。
     *
     * @param url 原始 URL
     * @param status 已取得的 HTTP 状态码，未请求时为 0
     * @param contentType 响应内容类型
     * @param error 失败原因
     * @return 结构化失败结果
     */
    private WebFetchResultDTO failure(String url, int status, String contentType, String error) {
        return new WebFetchResultDTO(false, url, status, contentType != null ? contentType : "", "", "",
                List.of(), false, redactSensitiveText(error), "可以换一个公开资料源，或先调用 searchWeb 查找可访问页面。");
    }

    /**
     * 提取并编号页面链接，同时把正文中的链接文本替换为 [id] 前缀，便于模型引用。
     *
     * @param doc 已经移除脚本和导航噪音的 HTML 文档
     * @return 最多 MAX_LINKS 条可继续抓取的链接
     */
    private List<WebFetchLinkDTO> annotateLinks(Document doc) {
        List<WebFetchLinkDTO> links = new ArrayList<>();
        for (Element anchor : doc.select("a[href]")) {
            String text = normalizeInlineText(anchor.text());
            String href = anchor.absUrl("href");
            if (href == null || href.isBlank()) {
                href = anchor.attr("href");
            }
            if (text.isBlank() || href == null || href.isBlank() || links.size() >= MAX_LINKS) {
                continue;
            }
            String normalizedHref = normalizeLink(href);
            int id = links.size() + 1;
            links.add(new WebFetchLinkDTO(id, text, normalizedHref));
            anchor.text("[" + id + "] " + text);
        }
        return links;
    }

    /**
     * 将 HTML 节点递归渲染为可读文本，块级元素转换为换行而不是压成单行。
     *
     * @param doc 已清洗的 HTML 文档
     * @return 保留段落和列表结构的正文文本
     */
    private String renderReadableText(Document doc) {
        Node root = doc.body() != null ? doc.body() : doc;
        StringBuilder sb = new StringBuilder();
        appendNodeText(root, sb);
        return normalizeTextLines(sb.toString());
    }

    /**
     * 递归收集节点文本；复杂点在于需要在块级元素边界插入换行，避免正文结构丢失。
     *
     * @param node 当前节点
     * @param sb 输出缓冲区
     */
    private void appendNodeText(Node node, StringBuilder sb) {
        if (node instanceof TextNode textNode) {
            sb.append(textNode.text());
            return;
        }
        if (node instanceof Element element) {
            String tag = element.normalName();
            boolean block = isBlockTag(tag);
            if ("br".equals(tag)) {
                sb.append('\n');
                return;
            }
            if (block) {
                sb.append('\n');
            }
            for (Node child : element.childNodes()) {
                appendNodeText(child, sb);
            }
            if (block) {
                sb.append('\n');
            }
        }
    }

    /**
     * 判断标签是否应在纯文本中形成段落边界。
     *
     * @param tag HTML 标签名
     * @return 属于块级或列表/表格标签时返回 true
     */
    private boolean isBlockTag(String tag) {
        return tag != null && BLOCK_TAG_PATTERN.matcher(tag).matches();
    }

    /**
     * 按行压缩空白并移除空行，保留段落换行供 LLM 判断上下文结构。
     *
     * @param raw 原始文本
     * @return 结构化纯文本
     */
    private String normalizeTextLines(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        List<String> lines = new ArrayList<>();
        for (String line : raw.replace('\r', '\n').split("\n")) {
            String normalized = normalizeInlineText(line);
            if (!normalized.isBlank()) {
                lines.add(normalized);
            }
        }
        return String.join("\n", lines).trim();
    }

    /**
     * 压缩单行内部空白。
     *
     * @param text 原始单行文本
     * @return 压缩后的单行文本
     */
    private String normalizeInlineText(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    /**
     * 标准化链接文本，尽量解开搜索结果或页面内常见的编码 URL。
     *
     * @param href 页面链接
     * @return 清洗后的 URL
     */
    private String normalizeLink(String href) {
        String normalized = href.trim();
        if (normalized.contains("uddg=")) {
            String decoded = extractQueryParam(normalized, "uddg");
            if (!decoded.isBlank()) {
                return decoded;
            }
        }
        return normalized;
    }

    /**
     * 从 URL 查询串中取出并 URL decode 指定参数。
     *
     * @param url 原始 URL
     * @param name 参数名
     * @return 参数值不存在时返回空字符串
     */
    private String extractQueryParam(String url, String name) {
        try {
            URI uri = URI.create(url);
            String query = uri.getRawQuery();
            if (query == null || query.isBlank()) {
                return "";
            }
            for (String pair : query.split("&")) {
                int idx = pair.indexOf('=');
                String key = idx >= 0 ? pair.substring(0, idx) : pair;
                if (name.equals(URLDecoder.decode(key, StandardCharsets.UTF_8))) {
                    String value = idx >= 0 ? pair.substring(idx + 1) : "";
                    return URLDecoder.decode(value, StandardCharsets.UTF_8);
                }
            }
        } catch (Exception ignored) {
            return "";
        }
        return "";
    }

    /**
     * 脱敏响应文本中可能出现的 Authorization Bearer token，避免错误页污染上下文。
     *
     * @param text 原始文本
     * @return 脱敏后的文本
     */
    private String redactSensitiveText(String text) {
        if (text == null || text.isBlank()) {
            return text == null ? "" : text;
        }
        return BEARER_TOKEN_PATTERN.matcher(text).replaceAll("Bearer [REDACTED]");
    }

    /**
     * HTML 或文本清洗后的内部结果。
     *
     * @param title 页面标题
     * @param text 正文文本
     * @param links 页面链接列表
     * @param truncated 正文是否因字符上限被截断
     */
    private record CleanedText(String title, String text, List<WebFetchLinkDTO> links, boolean truncated) {
    }
}
