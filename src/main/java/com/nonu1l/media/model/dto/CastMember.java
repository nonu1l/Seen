package com.nonu1l.media.model.dto;

import lombok.Data;

/**
 * 演员/角色成员信息 DTO。
 *
 * 用于返回作品的演员及相关展示字段。
 */
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
