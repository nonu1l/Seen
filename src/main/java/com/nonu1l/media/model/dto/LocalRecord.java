package com.nonu1l.media.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** 本地已标记记录（@Tool 返回） */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LocalRecord(
        Long subjectId, String nameCn,
        String status, Integer rating, String review
) {}
