package com.nonu1l.media.model.dto;

/**
 * 后台额外配置页的轻量汇总数据。
 *
 * @param totalTokens Token 使用总量
 * @param cacheBytes 当前进程内请求缓存响应体总字节数
 */
public record AdminOverviewDTO(
        long totalTokens,
        long cacheBytes
) {
}
