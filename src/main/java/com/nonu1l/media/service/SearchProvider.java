package com.nonu1l.media.service;

import com.nonu1l.media.model.dto.WebSearchItem;

import java.util.List;

public interface SearchProvider {
    List<WebSearchItem> search(String query);
    String fetch(String url);
}
