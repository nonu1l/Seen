package com.nonu1l.media.model.dto;

import lombok.Data;

@Data
public class MarkRequest {
    private String id;
    private String platform;
    /** wish / doing / collect / on_hold / dropped */
    private String status;
    private WorkSearchResult meta;
}
