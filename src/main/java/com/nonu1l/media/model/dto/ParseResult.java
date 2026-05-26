package com.nonu1l.media.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * LLM 解析结果（已弃用，仅保留代码）。
 * 原用于存储从用户自然语言中提取的结构化信息。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ParseResult {

    /** 提取的搜索关键词 */
    private String searchQuery;
    /** 类型："movie" 或 "tv" */
    private String type;
    /** 季数 */
    private Integer season;
    /** 评分（1-10） */
    private Integer rating;
    /** 评价文本 */
    private String review;

    public ParseResult() {
    }

    public String getSearchQuery() {
        return searchQuery;
    }

    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Integer getSeason() {
        return season;
    }

    public void setSeason(Integer season) {
        this.season = season;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getReview() {
        return review;
    }

    public void setReview(String review) {
        this.review = review;
    }
}
