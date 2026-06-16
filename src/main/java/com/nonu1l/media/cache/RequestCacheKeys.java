package com.nonu1l.media.cache;

import java.util.Objects;

/**
 * HTTP 请求缓存 key 构造与解析工具。
 */
public final class RequestCacheKeys {

    private static final String SEPARATOR = "\u001F";

    private RequestCacheKeys() {
    }

    /**
     * 构建 GET 缓存 key。
     *
     * @param url 请求地址
     * @return 缓存 key
     */
    public static String get(String url) {
        return "GET" + SEPARATOR + Objects.toString(url, "");
    }

    /**
     * 构建 POST 缓存 key。
     *
     * @param url 请求地址
     * @param body 请求体
     * @return 缓存 key
     */
    public static String post(String url, String body) {
        return "POST" + SEPARATOR + Objects.toString(url, "") + SEPARATOR + Objects.toString(body, "");
    }

    /**
     * 解析缓存 key。
     *
     * @param key 缓存 key
     * @return 解析后的请求 key
     */
    public static ParsedKey parse(String key) {
        if (key == null || key.isBlank()) {
            return new ParsedKey("UNKNOWN", "", "");
        }
        String[] parts = key.split(SEPARATOR, 3);
        if (parts.length >= 2) {
            return new ParsedKey(parts[0], parts[1], parts.length == 3 ? parts[2] : "");
        }
        if (key.startsWith("GET:")) {
            return new ParsedKey("GET", key.substring(4), "");
        }
        if (key.startsWith("POST:")) {
            return new ParsedKey("POST", key.substring(5), "");
        }
        return new ParsedKey("UNKNOWN", key, "");
    }

    /**
     * 解析后的缓存 key。
     *
     * @param method HTTP 方法
     * @param url 请求地址
     * @param body 请求体
     */
    public record ParsedKey(String method, String url, String body) {
    }
}
