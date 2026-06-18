package com.nonu1l.media.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * AI 找片/推荐工具的结构化返回，空结果时保留失败说明。
 *
 * @param ok 是否找到可展示的候选作品。
 * @param query 用户查询或推荐需求。
 * @param mode search / recommend / description。
 * @param cards 候选作品列表。
 * @param failReason 流水线生成的失败说明。
 * @param hint 给 Agent 的下一步建议。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record FindWorksToolResultDTO(
        boolean ok,
        String query,
        String mode,
        List<MatchedEntryDTO> cards,
        String failReason,
        String hint
) {
}
