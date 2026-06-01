package com.nonu1l.media.model.dto;

import lombok.Data;

import java.util.List;

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
