package com.nonu1l.media.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.Serializable;

/**
 * Bangumi 搜索精简结果（@Tool 返回）。
 * 用于对条目数据进行压缩返回给模型使用。
 *
 * @param id Bangumi 条目 ID。
 * @param nameCn 中文名。
 * @param nameOrig 原始名/日文名。
 * @param category 条目类型或发布形态。
 * @param airDate 上映/开播日期。
 * @param epsCount 集数，可能为空。
 * @param score Bangumi 平均评分。
 * @param rank Bangumi 排名。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CompactResult(
        Long id, String nameCn, String nameOrig,
        String category, String airDate,
        Integer epsCount, Double score, Integer rank
) implements Serializable {
    /**
     * 将 {@link CompactSubject} 转换为压缩结果 DTO。
     *
     * @param s 压缩条目对象。
     * @return 对应的紧凑返回对象。
     */
    public static CompactResult from(CompactSubject s) {
        return new CompactResult(s.id(), s.nameCn(), null,
                s.category(), s.airDate(), s.epsCount(), s.score(), s.rank());
    }
}
