package com.nonu1l.media.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 搜索结果 DTO。
 * local 为本地已标记作品，works 为 Bangumi 远程搜索结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchDTO {
    /** 本地已标记作品中匹配的作品列表 */
    private List<WorkListItemDTO> local;
    /** Bangumi 远程搜索结果 */
    private List<WorkSearchResultDTO> works;
}
