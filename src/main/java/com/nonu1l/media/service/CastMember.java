package com.nonu1l.media.service;

import lombok.Data;

@Data
public class CastMember {
    private Long id;
    private String name;
    /** CV 或角色备注 */
    private String character;
    private String profile;
}
