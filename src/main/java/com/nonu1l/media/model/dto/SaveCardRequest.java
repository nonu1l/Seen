package com.nonu1l.media.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 保存卡片标记请求体。
 *
 * @param rating 评分，10 分制，支持 0.5 分小数。
 * @param review 评价文本。
 * @param status 状态标记。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SaveCardRequest(
        Double rating,
        String review,
        String status
) {}
