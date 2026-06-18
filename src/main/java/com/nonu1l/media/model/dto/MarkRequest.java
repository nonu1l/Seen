package com.nonu1l.media.model.dto;

import lombok.Data;

/**
 * 标记请求 DTO。
 *
 * 用于创建/更新用户对作品的标记状态。
 */
@Data
public class MarkRequest {
    private String id;
    private String platform;
    /** wish / doing / collect / on_hold / dropped */
    private String status;
    private BangumiSubjectSummaryDTO meta;
}
