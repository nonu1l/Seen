package com.nonu1l.media.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 在线观看地址搜索结果。
 *
 * @param ok 是否找到候选观看地址
 * @param query 用户原始查询
 * @param resolvedTitle 解析出的作品中文名或主标题
 * @param resolvedOriginalTitle 解析出的原名，可为空
 * @param resolvedYear 解析出的年份，可为空
 * @param resolveConfidence 标题解析置信度
 * @param searchQuery 实际执行的一轮 Web 搜索关键词
 * @param items 候选观看地址列表
 * @param error 失败原因
 * @param hint 给 Agent 的下一步建议
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WatchSourceResultDTO(
        boolean ok,
        String query,
        String resolvedTitle,
        String resolvedOriginalTitle,
        String resolvedYear,
        double resolveConfidence,
        String searchQuery,
        List<WatchSourceItemDTO> items,
        String error,
        String hint
) {
}
