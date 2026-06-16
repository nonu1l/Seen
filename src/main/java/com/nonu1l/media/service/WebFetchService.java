package com.nonu1l.media.service;

import com.nonu1l.media.model.dto.FetchUrlResult;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;

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

    /**
     * 抓取 URL 并返回结构化结果。
     *
     * @param url 目标 URL，仅支持 HTTP(S)
     * @param maxChars 返回给模型的最大字符数，可为空
     * @return 抓取结果；失败时包含错误原因
     */
    public FetchUrlResult fetch(String url, Integer maxChars) {
        int limit = clampMaxChars(maxChars);
        try {
            URI uri = normalizeAndValidate(url);
            return fetchFollowingRedirects(uri, limit, 0);
        } catch (Exception e) {
            log.warn("fetch_url blocked '{}': {}", url, e.getMessage());
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
        FetchUrlResult result = fetch(url, DEFAULT_MAX_CHARS);
        return result.error() == null ? result.text() : "";
    }

    private FetchUrlResult fetchFollowingRedirects(URI uri, int maxChars, int redirects) throws Exception {
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
        log.info("fetch_url status={} chars={} truncated={} url={}",
                status, cleaned.text().length(), truncated, uri);
        return new FetchUrlResult(uri.toString(), status, contentType, cleaned.title(), cleaned.text(), truncated,
                status >= 400 ? "HTTP " + status : null);
    }

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

    private void validateResolvedAddress(URI uri) throws Exception {
        InetAddress[] addresses = InetAddress.getAllByName(uri.getHost());
        for (InetAddress address : addresses) {
            if (isBlockedAddress(address)) {
                throw new IllegalArgumentException("private or local address is not allowed: " + uri.getHost());
            }
        }
    }

    private boolean isBlockedAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress()
                || address.isLinkLocalAddress() || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }
        if (address instanceof Inet6Address) {
            byte first = address.getAddress()[0];
            return (first & 0xfe) == 0xfc;
        }
        return false;
    }

    private byte[] readLimited(InputStream stream) throws Exception {
        if (stream == null) return new byte[0];
        try (stream) {
            return stream.readNBytes(MAX_BYTES + 1);
        }
    }

    private CleanedText clean(String url, String contentType, String raw, int maxChars) {
        String normalizedContentType = contentType != null ? contentType.toLowerCase(Locale.ROOT) : "";
        String text;
        String title = "";
        if (normalizedContentType.contains("html") || looksLikeHtml(raw)) {
            Document doc = Jsoup.parse(raw, url);
            title = doc.title();
            doc.select("script,style,noscript,svg,canvas,header,footer,nav").remove();
            text = doc.body() != null ? doc.body().text() : doc.text();
        } else {
            text = raw;
        }
        text = text.replaceAll("\\s+", " ").trim();
        boolean truncated = text.length() > maxChars;
        if (truncated) {
            text = text.substring(0, maxChars);
        }
        return new CleanedText(title, text, truncated);
    }

    private boolean looksLikeHtml(String raw) {
        String value = raw != null ? raw.stripLeading().toLowerCase(Locale.ROOT) : "";
        return value.startsWith("<!doctype html") || value.startsWith("<html") || value.contains("<body");
    }

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

    private boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private int clampMaxChars(Integer value) {
        if (value == null || value <= 0) return DEFAULT_MAX_CHARS;
        return Math.min(value, MAX_CHARS);
    }

    private FetchUrlResult failure(String url, int status, String contentType, String error) {
        return new FetchUrlResult(url, status, contentType != null ? contentType : "", "", "", false, error);
    }

    private record CleanedText(String title, String text, boolean truncated) {
    }
}
