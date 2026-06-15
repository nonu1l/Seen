package com.nonu1l.media.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * 意图分析返回结果。
 *
 * @param replyText 模型回执文本。
 * @param entries 匹配到的条目列表。
 * @param unmarkIds 需要清空标记的条目 ID 集合。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record IntentAnalysisResult(
        String replyText,
        List<MatchedEntry> entries,
        List<Long> unmarkIds
) {}
