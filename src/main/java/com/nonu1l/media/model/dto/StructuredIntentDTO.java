package com.nonu1l.media.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * LLM Phase 1 输出 — 从用户自然语言中提取的结构化意图。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record StructuredIntentDTO(
        /** 搜索关键词（去季号、去副标题） */
        String keywords,
        /** 指定季数，null=未指定，-1=最终季 */
        Integer season,
        /** 用户显式打分 1-10，支持 0.5 分小数，null=未指定 */
        Double rating,
        /** 评价文本原文 */
        String comment,
        /** 状态标记：collect/wish/doing/on_hold/dropped，null=需推断 */
        String status,
        /** 范围：single=单条目 / all=全系列，null=未指定 */
        String scope
) {}
