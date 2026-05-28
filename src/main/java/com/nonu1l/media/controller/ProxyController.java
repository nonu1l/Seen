package com.nonu1l.media.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Bangumi 页面代理 — 绕过 X-Frame-Options，注入暗色主题样式。
 */
@RestController
@RequestMapping("/api/proxy")
public class ProxyController {

    private static final Logger log = LoggerFactory.getLogger(ProxyController.class);

    private static final String BANGUMI_HOST = "bgm.tv";
    private static final String INJECTED_BEFORE_HEAD = """
        <base href="https://bgm.tv/">
        <style>
          html { filter: invert(0.88) hue-rotate(180deg) saturate(0.6); background: #111; }
          img, video, canvas, svg, [style*="background-image"] { filter: invert(1) hue-rotate(180deg) saturate(1) !important; }
          body { background: #0d0d0d !important; }
          #header, .header, nav, footer, .footer, #footer, [class*="nav"], [class*="menu"], [class*="banner"] { display: none !important; }
        </style>
        """;

    @GetMapping("/bangumi")
    public void proxy(@RequestParam String url, HttpServletResponse response) throws IOException {
        if (!isAllowed(url)) {
            response.sendError(400, "Only bgm.tv / bangumi.tv URLs allowed");
            return;
        }

        try {
            HttpURLConnection conn = (HttpURLConnection) URI.create(url).toURL().openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1");
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(12000);
            conn.setInstanceFollowRedirects(true);

            String contentType = conn.getContentType();
            if (contentType != null) response.setContentType(contentType);
            response.setStatus(conn.getResponseCode());

            if (contentType != null && contentType.contains("text/html")) {
                byte[] rawBytes = conn.getInputStream().readAllBytes();
                String charset = detectCharset(contentType);
                String html = new String(rawBytes, charset);
                html = injectIntoHead(html, INJECTED_BEFORE_HEAD);
                response.getOutputStream().write(html.getBytes(charset));
            } else {
                try (var in = conn.getInputStream(); OutputStream out = response.getOutputStream()) {
                    in.transferTo(out);
                }
            }
        } catch (Exception e) {
            log.warn("Proxy failed url={}: {}", url, e.getMessage());
            response.sendError(502, "Proxy error");
        }
    }

    private boolean isAllowed(String url) {
        if (url == null) return false;
        String host = URI.create(url).getHost();
        return host != null && (host.endsWith("bgm.tv") || host.endsWith("bangumi.tv"));
    }

    /** 从 Content-Type 提取 charset，默认 UTF-8 */
    private String detectCharset(String contentType) {
        if (contentType == null) return "UTF-8";
        for (String part : contentType.split(";")) {
            part = part.trim();
            if (part.toLowerCase().startsWith("charset=")) {
                return part.substring(8).trim();
            }
        }
        return "UTF-8";
    }

    /** 在 <head> 标签后方注入内容（大小写不敏感） */
    private String injectIntoHead(String html, String injection) {
        String lower = html.toLowerCase();
        int pos = lower.indexOf("<head");
        if (pos < 0) return injection + html;
        int end = lower.indexOf('>', pos);
        if (end < 0) return injection + html;
        return html.substring(0, end + 1) + injection + html.substring(end + 1);
    }
}
