package com.nonu1l.media.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Bangumi 搜索精简结果（@Tool 返回） */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompactResult(
        Long id, String nameCn, String nameOrig,
        String category, String airDate,
        Integer epsCount, Double score, Integer rank
) {
    public static CompactResult from(CompactSubject s) {
        return new CompactResult(s.id(), s.nameCn(), null,
                s.category(), s.airDate(), s.epsCount(), s.score(), s.rank());
    }
}
