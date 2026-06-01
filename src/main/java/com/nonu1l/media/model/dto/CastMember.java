package com.nonu1l.media.model.dto;

import lombok.Data;

@Data
public class CastMember {
    private Long id;
    private String name;
    /** CV 或演员名 */
    private String character;
    private String profile;
    /** 演员/声优 ID，用于获取中文名 */
    private Long actorId;
}
