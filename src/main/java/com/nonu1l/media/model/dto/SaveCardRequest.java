package com.nonu1l.media.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 保存卡片标记请求体。
 *
 * @param rating 评分。
 * @param review 评价文本。
 * @param status 状态标记。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SaveCardRequest(
        Integer rating,
        String review,
        String status
) {}
