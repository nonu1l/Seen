package com.nonu1l.media.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.nonu1l.media.service.CastMember;
import lombok.Data;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

/**
 * 作品详情 DTO，用于详情弹窗展示。
 * 包含 Bangumi 元数据、演员表、以及用户自己的标记状态/评分/评价。
 */
@Data
@JsonInclude(NON_NULL)
public class WorkDetail {
    /* ── 作品元数据 ── */
    private Long id;
    private String platform;
    private String nameCn;
    private String nameOrig;
    private String coverUrl;
    private String year;
    private List<String> tags;
    private String plot;
    private Double score;

    /* ── 额外详情（从 Bangumi infobox + characters 接口获取） ── */
    private List<String> regions;
    private Integer episodes;
    private Integer seasonsCount;
    private Integer runtime;
    private List<CastMember> cast;

    /* ── 用户标记信息 ── */
    private String status;
    private Double myRating;
    private String myReview;
    private boolean rewatched;
    private Integer watchedCount;
}
