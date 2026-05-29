package com.nonu1l.media.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nonu1l.media.model.dto.WorkSearchResult;
import com.nonu1l.media.model.entity.SubjectType;
import com.nonu1l.media.repository.SubjectTypeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class BangumiService {

    private static final Logger log = LoggerFactory.getLogger(BangumiService.class);
    private static final long TTL_CHARACTER = 1209600; // 14 days

    private final String base;
    private final ObjectMapper objectMapper;
    private final SubjectTypeRepository subjectTypeRepository;
    private final RequestCacheUtil cache;
    private final PreCacheService preCacheService;
    private volatile List<Integer> cachedSubjectTypes;

    public BangumiService(ObjectMapper objectMapper, SubjectTypeRepository subjectTypeRepository,
                          RequestCacheUtil cache, PreCacheService preCacheService,
                          @Value("${seen.bangumi-proxy:}") String bangumiProxy) {
        this.objectMapper = objectMapper;
        this.subjectTypeRepository = subjectTypeRepository;
        this.cache = cache;
        this.preCacheService = preCacheService;
        if (!bangumiProxy.isBlank()) {
            this.base = bangumiProxy + "/api";
        } else {
            this.base = "https://api.bgm.tv/v0";
        }
    }

    public List<WorkSearchResult> search(String query) {
        List<WorkSearchResult> results = new ArrayList<>();
        List<Integer> types = getSearchTypes();
        if (types.isEmpty()) return results;

        try {
            String body = objectMapper.writeValueAsString(
                    java.util.Map.of(
                            "keyword", query,
                            "sort", "match",
                            "filter", java.util.Map.of("type", types)));

            String json = post(base + "/search/subjects?limit=20", body, 300);
            if (json == null) return results;

            JsonNode data = objectMapper
                    .readTree(json)
                    .get("data");

            if (data != null && data.isArray()) {
                for (JsonNode item : data) results.add(mapSubject(item));
            }

            // 搜索完成后异步预热详情缓存（跳过已缓存的） [正在开发LLM功能，暂时注释预缓存]
//            List<Long> ids = results.stream()
//                    .map(WorkSearchResult::getId)
//                    .filter(Objects::nonNull)
//                    .toList();
//            if (!ids.isEmpty()) preCacheService.preCache(ids);

        } catch (Exception e) {
            log.error("Bangumi search failed query={}", query, e);
        }
        return results;
    }

    public WorkSearchResult getById(String subjectId) {
        try {
            String json = get(base + "/subjects/" + subjectId, 1800);
            if (json == null) return null;
            return mapSubject(objectMapper.readTree(json));
        } catch (Exception e) {
            log.error("Bangumi getById failed id={}", subjectId, e);
            return null;
        }
    }

    public DetailedWork getDetailed(String subjectId) {
        try {
            String json = get(base + "/subjects/" + subjectId, 1800);
            if (json == null) return null;
            JsonNode item = objectMapper.readTree(json);

            DetailedWork d = new DetailedWork();
            d.setBase(mapSubject(item));

            // 从 infobox 提取地区/国家信息
            List<String> regions = extractInfobox(item, "地区", "国家");
            d.setRegions(regions);

            // type=6 为电影，提取片长（分钟）；否则为 TV，提取总集数
            int st = item.has("type") ? item.get("type").asInt() : 0;
            if (st == 6) {
                List<String> rts = extractInfobox(item, "片长");
                if (!rts.isEmpty()) {
                    try {
                        d.setRuntime(Integer.parseInt(rts.getFirst().replaceAll("[^0-9]", "")));
                    } catch (NumberFormatException ignored) {
                    }
                }
            } else {
                JsonNode eps = item.get("total_episodes");
                if (eps != null && !eps.isNull() && eps.asInt() > 0) d.setEpisodes(eps.asInt());
            }

            // 获取角色/声优信息（最多 12 条，不含中文名）
            try {
                String charJson = get(base + "/subjects/" + subjectId + "/characters", 3600);
                if (charJson != null) {
                    JsonNode chars = objectMapper.readTree(charJson);
                    if (chars.isArray()) {
                        int max = Math.min(chars.size(), 12);
                        List<CastMember> cast = new ArrayList<>();
                        for (int i = 0; i < max; i++) {
                            JsonNode c = chars.get(i);
                            CastMember m = new CastMember();
                            m.setId(c.has("id") && !c.get("id").isNull() ? c.get("id").asLong() : null);
                            m.setName(text(c, "name"));
                            JsonNode images = c.get("images");
                            //small 为小图片，保持比例 ;grid 会剪切为正方形
                            if (images != null) m.setProfile(text(images, "small"));
                            JsonNode actors = c.get("actors");
                            if (actors != null && actors.isArray() && !actors.isEmpty()) {
                                m.setCharacter("CV: " + text(actors.get(0), "name"));
                            }
                            cast.add(m);
                        }
                        d.setCast(cast);
                    }
                }
            } catch (Exception e) {
                log.warn("Characters parse failed id={}: {}", subjectId, e.getMessage());
            }
            return d;
        } catch (Exception e) {
            log.error("Bangumi getDetailed failed id={}", subjectId, e);
            return null;
        }
    }

    private List<Integer> getSearchTypes() {
        if (cachedSubjectTypes == null) {
            cachedSubjectTypes = subjectTypeRepository.findAll().stream()
                    .map(SubjectType::getCode).toList();
        }
        return cachedSubjectTypes;
    }


    private WorkSearchResult mapSubject(JsonNode item) {
        WorkSearchResult r = new WorkSearchResult();
        r.setId(Long.parseLong(String.valueOf(item.get("id"))));
        r.setPlatform(text(item, "platform"));
        r.setSource("bangumi");

        String nameCn = text(item, "name_cn");
        String nameOrig = text(item, "name");

        r.setNameCn(nameCn != null && !nameCn.isBlank() ? nameCn : nameOrig);
        r.setNameOrig(nameOrig);

        JsonNode images = item.get("images");
        if (images != null) {
            String cover = text(images, "small");
            r.setCoverUrl(cover);
        }

        String airDate = text(item, "date");
        if (airDate == null) airDate = text(item, "air_date");
        if (airDate != null && airDate.length() >= 4) {
            r.setYear(airDate.substring(0, 4));
            r.setAirDate(airDate);
        } else {
            r.setAirDate("");
        }
        r.setPlot(text(item, "summary"));

        // 总集数
        JsonNode epsNode = item.get("total_episodes");
        if (epsNode != null && !epsNode.isNull() && epsNode.asInt() > 0) {
            r.setEpsCount(epsNode.asInt());
        }
        // 条目类型
        if (item.has("type") && !item.get("type").isNull()) {
            r.setSubjectType(item.get("type").asInt());
        }

        JsonNode rating = item.get("rating");
        if (rating != null && rating.has("score")) {
            double score = rating.get("score").asDouble();
            if (score > 0) r.setScore(score);
            if (rating.has("rank") && !rating.get("rank").isNull()) r.setRank(rating.get("rank").asInt());
        }


        // meta_tags 为字符串数组，如 ["喜剧","TV","日本","漫画改"]
        JsonNode tagsNode = item.get("meta_tags");
        if (tagsNode != null && tagsNode.isArray()) {
            List<String> tagNames = new ArrayList<>();
            for (JsonNode t : tagsNode) {
                if (t.isTextual()) tagNames.add(t.asText());
            }
            r.setTags(tagNames);
        }
        return r;
    }

    private List<String> extractInfobox(JsonNode item, String... keys) {
        List<String> result = new ArrayList<>();
        JsonNode infobox = item.get("infobox");
        if (infobox == null || !infobox.isArray()) return result;
        for (JsonNode entry : infobox) {
            String k = text(entry, "key");
            if (k == null) continue;
            for (String key : keys) {
                if (k.contains(key)) {
                    JsonNode v = entry.get("value");
                    if (v != null && v.isTextual()) result.add(v.asText());
                    else if (v != null && v.isArray()) {
                        for (JsonNode vi : v) {
                            String val = text(vi, "v");
                            if (val != null) result.add(val);
                        }
                    }
                }
            }
        }
        return result;
    }

    public String getCharacterName(Long id) {
        try {
            String json = get(base + "/characters/" + id, TTL_CHARACTER);
            if (json == null) return null;
            return extractChineseName(objectMapper.readTree(json));
        } catch (Exception e) {
            log.debug("getCharacterName failed id={}: {}", id, e.getMessage());
            return null;
        }
    }

    private String extractChineseName(JsonNode item) {
        JsonNode infobox = item.get("infobox");
        if (infobox == null || !infobox.isArray()) return null;
        // 优先 "简体中文名"
        for (JsonNode entry : infobox) {
            String key = text(entry, "key");
            if ("简体中文名".equals(key)) {
                JsonNode v = entry.get("value");
                if (v != null && v.isTextual()) return v.asText();
            }
        }
        // 回退 "别名" 中的 "第二中文名"
        for (JsonNode entry : infobox) {
            String key = text(entry, "key");
            if ("别名".equals(key)) {
                JsonNode v = entry.get("value");
                if (v != null && v.isArray()) {
                    for (JsonNode alias : v) {
                        if ("第二中文名".equals(text(alias, "k"))) {
                            String cn = text(alias, "v");
                            if (cn != null) return cn;
                        }
                    }
                }
            }
        }
        return null;
    }

    private String get(String url, long ttlSeconds) {
        return cache.cacheGet(url, ttlSeconds);
    }

    private String post(String url, String body, long ttlSeconds) {
        return cache.cachePost(url, body, ttlSeconds);
    }

    private String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v != null && !v.isNull() ? v.asText() : null;
    }
}
