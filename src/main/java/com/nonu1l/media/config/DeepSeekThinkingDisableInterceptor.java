package com.nonu1l.media.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 仅对 DeepSeek 的 /chat/completions 请求体追加 thinking={type:disabled}，
 * 关闭思考模式以避免工具调用多轮循环时被要求回传 reasoning_content（Spring AI 1.1.x 不支持）。
 */
public class DeepSeekThinkingDisableInterceptor implements ClientHttpRequestInterceptor {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekThinkingDisableInterceptor.class);

    private final ObjectMapper objectMapper;

    public DeepSeekThinkingDisableInterceptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

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

    private static boolean isDeepSeekChatCompletion(HttpRequest request) {
        String host = request.getURI().getHost();
        String path = request.getURI().getPath();
        return host != null && (host.contains("deepseek.com") || host.contains("bigmodel.cn"))
                && path != null && (path.endsWith("/chat/completions"));
    }
}
