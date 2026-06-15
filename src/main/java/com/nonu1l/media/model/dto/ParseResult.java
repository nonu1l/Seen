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

    /**
     * 默认构造方法。
     */
    public ParseResult() {
    }

    /**
     * 获取提取的搜索关键词。
     *
     * @return 搜索关键词。
     */
    public String getSearchQuery() {
        return searchQuery;
    }

    /**
     * 设置提取的搜索关键词。
     *
     * @param searchQuery 搜索关键词。
     */
    public void setSearchQuery(String searchQuery) {
        this.searchQuery = searchQuery;
    }

    /**
     * 获取作品类型。
     *
     * @return 类型（movie 或 tv）。
     */
    public String getType() {
        return type;
    }

    /**
     * 设置作品类型。
     *
     * @param type 类型值（如 movie / tv）。
     */
    public void setType(String type) {
        this.type = type;
    }

    /**
     * 获取季数。
     *
     * @return 季数。
     */
    public Integer getSeason() {
        return season;
    }

    /**
     * 设置季数。
     *
     * @param season 季数。
     */
    public void setSeason(Integer season) {
        this.season = season;
    }

    /**
     * 获取评分。
     *
     * @return 评分（1-10）。
     */
    public Integer getRating() {
        return rating;
    }

    /**
     * 设置评分。
     *
     * @param rating 评分（1-10）。
     */
    public void setRating(Integer rating) {
        this.rating = rating;
    }

    /**
     * 获取评价文本。
     *
     * @return 评价文本。
     */
    public String getReview() {
        return review;
    }

    /**
     * 设置评价文本。
     *
     * @param review 评价文本。
     */
    public void setReview(String review) {
        this.review = review;
    }
}
