package com.nonu1l.media.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 本地已标记记录（@Tool 返回），评分使用 10 分制小数。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LocalRecordDTO(
        Long subjectId, String nameCn,
        String status, Double rating, String review
) {}
