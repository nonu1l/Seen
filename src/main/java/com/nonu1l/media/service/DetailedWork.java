package com.nonu1l.media.service;

import com.nonu1l.media.model.dto.WorkSearchResult;
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
}
