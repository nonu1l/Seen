package com.nonu1l.media.model.dto;

import lombok.Data;

import java.util.List;

/**
 * 作品详细信息 DTO。
 *
 * 包含作品基础信息、附加详情与用户标记字段。
 */
@Data
public class DetailedWork {
    private WorkSearchResult base;
    private List<String> regions;
    private Integer episodes;
    private Integer seasonsCount;
    private Integer runtime;
    private List<CastMember> cast;
    /** Bangumi infobox 中的 imdb_id，用于直接跳转 IMDb */
    private String imdbId;
}
