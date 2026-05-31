package com.nonu1l.media.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;

/** Web 搜索结果条目（@Tool 返回） */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WebSearchItem(String title, String snippet, String url) implements Serializable {}
