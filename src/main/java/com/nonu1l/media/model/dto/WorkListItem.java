package com.nonu1l.media.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

/**
 * 列表项 DTO，表示首页中一个已标记作品。
 * 包含作品元数据 + 当前用户的标记状态和评分。
 */
@Data
@JsonInclude(NON_NULL)
public class WorkListItem {
    /* ── 作品元数据 ── */
    private Long id;
    private String platform;
    private String nameOrig;
    private String nameCn;
    private String coverUrl;
    private String year;
    private List<String> tags;
    private String plot;
    private Double score;

    /* ── 用户标记信息 ── */
    private String status;
    private Double myRating;
    private String myReview;
    // 多刷功能，暂停开发
    // private boolean rewatched;
    private Integer recordsCount;
    private String latestRecordAt;
}
