package com.nonu1l.media.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 拦截 DeepSeek 类 LLM 的聊天完成请求，为请求体补齐
 * {@code thinking: {type: disabled}}，避免返回推理链路内容导致的兼容问题。
 */
public class DeepSeekThinkingDisableInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekThinkingDisableInterceptor.class);

    private final ObjectMapper objectMapper;

    /**
     * 使用 Jackson 对请求体进行可变更的拦截器。
     *
     * @param objectMapper JSON 反序列化与序列化工具。
     */
    public DeepSeekThinkingDisableInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 在发送 HTTP 请求前尝试增强 DeepSeek 请求体。
     *
     * @param request 当前请求。
     * @param body    原始请求内容。
     * @param execution 下游执行器。
     * @return 拦截器返回的响应体；仅在 DeepSeek chat completion 且未包含 thinking 时才修改 body。
     * @throws IOException 读取或写入请求体异常。
     */
    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        if (!isDeepSeekChatCompletion(request)) {
            return execution.execute(request, body);
        }

        // 记录每次 LLM 调用的请求体大小（累计可看到工具调用膨胀）
        int msgCount = countMessages(body);
        log.info("LLM call: body={}KB, messages≈{}", body.length / 1024, msgCount);

        byte[] patched = body;
        try {
            Map<String, Object> json = objectMapper.readValue(body, new TypeReference<LinkedHashMap<String, Object>>() {
            });
            if (!json.containsKey("thinking")) {
                json.put("thinking", Map.of("type", "disabled"));
                patched = objectMapper.writeValueAsBytes(json);
            }
        } catch (Exception e) {
            log.warn("Failed to patch DeepSeek request body, sending as-is: {}", e.getMessage());
        }
        return execution.execute(request, patched);
    }

    private int countMessages(byte[] body) {
        try {
            Map<String, Object> json = objectMapper.readValue(body, new TypeReference<LinkedHashMap<String, Object>>() {
            });
            @SuppressWarnings("unchecked")
            var messages = (java.util.List<?>) json.get("messages");
            if (messages != null && log.isDebugEnabled()) {
                log.debug("LLM messages ({})", messages.size());
                for (int i = 0; i < messages.size(); i++) {
                    var m = (Map<String, Object>) messages.get(i);
                    String role = (String) m.get("role");
                    Object content = m.get("content");
                    String preview = content != null ? content.toString() : "";
                    log.debug("  [{}] {}: {}", i, role,
                        preview.length() > 200 ? preview.substring(0, 200) + "..." : preview);
                }
            }
            return messages != null ? messages.size() : 0;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 判断当前请求是否为 DeepSeek 聊天完成接口。
     *
     * @param request 当前请求对象。
     * @return host 与 path 命中规则返回 true。
     */
    private static boolean isDeepSeekChatCompletion(HttpRequest request) {
        String host = request.getURI().getHost();
        String path = request.getURI().getPath();
        return host != null && (host.contains("deepseek.com")
                || host.contains("bigmodel.cn")
                || host.contains("localhost")
                || host.contains("192.168.67.124"))
                && path != null && (path.endsWith("/chat/completions"));
    }
}
