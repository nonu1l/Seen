package com.nonu1l.media.agent;

import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 读取 classpath 下的 Agent prompt，避免各 Runner 重复处理资源加载。
 */
final class AgentPromptLoader {

    private AgentPromptLoader() {
    }

    /**
     * @param path classpath prompt 路径
     * @return UTF-8 prompt 文本
     */
    static String load(String path) {
        try {
            return new String(new ClassPathResource(path).getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load prompt: " + path, e);
        }
    }
}
