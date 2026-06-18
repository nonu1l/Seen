package com.nonu1l.media.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

/**
 * Bangumi 搜索结果 / 基础作品信息 DTO。
 * 由 BangumiService 的 search 和 getById 返回，被 WorkListItemResponse / WorkDetailResponse 复用。
 */
@Data
@JsonInclude(NON_NULL)
public class BangumiSubjectSummaryDTO {
    private Long id;
    private String platform;
    private String nameCn;
    private String nameOrig;
    private String coverUrl;
    private String year;
    private List<String> tags;
    private String plot;
    private Double score;
    /** 数据来源标识（固定 "bangumi"） */
    private String source;
    /** 完整上映日期 yyyy-MM-dd */
    private String airDate;
    /** 总集数 */
    private Integer epsCount;
    /** Bangumi 条目类型：2=动画, 6=真人 */
    private Integer subjectType;
    /** Bangumi 排名 */
    private Integer rank;
}
