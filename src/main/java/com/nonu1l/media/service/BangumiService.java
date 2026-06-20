package com.nonu1l.media.service;

import com.nonu1l.media.model.dto.CastMemberDTO;
import com.nonu1l.media.model.dto.BangumiSubjectDetailDTO;
import com.nonu1l.media.model.dto.BangumiSubjectSummaryDTO;
import com.nonu1l.media.model.entity.SubjectType;
import com.nonu1l.media.repository.SubjectTypeRepository;
import com.nonu1l.media.util.CachedHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 统一封装 Bangumi API 调用与结果映射。
 */
@Service
public class BangumiService {

    private static final Logger log = LoggerFactory.getLogger(BangumiService.class);

    private final ObjectMapper objectMapper;
    private final SubjectTypeRepository subjectTypeRepository;
    private final CachedHttpClient httpClient;
    private final PreCacheService preCacheService;
    private final SettingsService settingsService;
    private final long characterTtlSeconds;

    private volatile List<Integer> cachedSubjectTypes;

    /**
     * @param objectMapper JSON 解析工具
     * @param subjectTypeRepository 条目类型配置仓储
     * @param httpClient 带缓存的 HTTP 客户端
     * @param preCacheService 预缓存服务
     * @param settingsService 设置读取服务
     * @param characterTtlSeconds 角色/人物名称缓存 TTL
     */
    public BangumiService(ObjectMapper objectMapper, SubjectTypeRepository subjectTypeRepository,
                          CachedHttpClient httpClient, PreCacheService preCacheService,
                          SettingsService settingsService,
                          @Value("${app.runtime.cache.bangumi-character-ttl-seconds:1209600}") long characterTtlSeconds) {
        this.objectMapper = objectMapper;
        this.subjectTypeRepository = subjectTypeRepository;
        this.httpClient = httpClient;
        this.preCacheService = preCacheService;
        this.settingsService = settingsService;
        this.characterTtlSeconds = characterTtlSeconds;
    }

    /** 搜索 bangumi，默认返回 20 条结果。
     *
     * @param query 查询关键字
     * @return 映射后的 {@link BangumiSubjectSummaryDTO} 列表
     */
    public List<BangumiSubjectSummaryDTO> search(String query) {
        return  search(query,20);
    }


    /**
     * 搜索 bangumi。
     *
     * <p>请求参数使用 bangumi v0 搜索接口，命中结果会触发详细页预取以便后续详情接口提速。</p>
     *
     * @param query 查询关键字
     * @param limit 返回条数上限
     * @return 映射后的 {@link BangumiSubjectSummaryDTO} 列表
     */
    public List<BangumiSubjectSummaryDTO> search(String query, int limit) {
        List<BangumiSubjectSummaryDTO> results = new ArrayList<>();
        List<Integer> types = getSearchTypes();
        if (types.isEmpty()) return results;
        String base = settingsService.bangumiApiBase();

        try {
            String body = objectMapper.writeValueAsString(
                    java.util.Map.of(
                            "keyword", query,
                            "sort", "match",
                            "filter", java.util.Map.of("type", types)));

            String json = post(base + "/search/subjects?limit=" + limit, body, 300);
            if (json == null) return results;
            JsonNode data = objectMapper
                    .readTree(json)
                    .get("data");

            if (data != null && data.isArray()) {
                for (JsonNode item : data) results.add(mapSubject(item));
            }

            List<Long> ids = results.stream()
                    .map(BangumiSubjectSummaryDTO::getId)
                    .filter(Objects::nonNull)
                    .toList();
            if (!ids.isEmpty()) preCacheService.preCache(ids);

        } catch (Exception e) {
            log.error("Bangumi search failed query={}", query, e);
        }
        return results;
    }

    /**
     * 按 ID 查询条目基础信息。
     *
     * @param subjectId 条目ID
     * @return 条目基础字段映射结果，异常或未命中返回 {@code null}
     */
    public BangumiSubjectSummaryDTO getById(String subjectId) {
        String base = settingsService.bangumiApiBase();
        try {
            String json = get(base + "/subjects/" + subjectId, 1800);
            if (json == null) return null;
            return mapSubject(objectMapper.readTree(json));
        } catch (Exception e) {
            log.error("Bangumi getById failed id={}", subjectId, e);
            return null;
        }
    }

    /**
     * 查询条目详情（含地区、IMDb、集数/片长与可选角色信息）。
     *
     * <p>角色与基础信息并行请求，提高首屏响应速度。</p>
     *
     * @param subjectId 条目ID
     * @return 组合后的 {@link BangumiSubjectDetailDTO}，无数据返回 {@code null}
     */
    public BangumiSubjectDetailDTO getDetailed(String subjectId) {
        String base = settingsService.bangumiApiBase();
        boolean castEnabled = settingsService.getBoolean(SettingsService.DETAIL_CAST_ENABLED);
        try {
            // 元数据与角色信息并行请求，互不阻塞
            CompletableFuture<String> subjectFuture = CompletableFuture.supplyAsync(
                    () -> get(base + "/subjects/" + subjectId, 1800));
            CompletableFuture<String> charFuture = castEnabled
                    ? CompletableFuture.supplyAsync(() -> get(base + "/subjects/" + subjectId + "/characters", 3600))
                    : null;

            String json = subjectFuture.join();
            if (json == null) return null;
            JsonNode item = objectMapper.readTree(json);

            BangumiSubjectDetailDTO d = new BangumiSubjectDetailDTO();
            d.setBase(mapSubject(item));

            // 从 infobox 提取地区/国家信息
            List<String> regions = extractInfobox(item, "地区", "国家");
            d.setRegions(regions);

            // 提取 imdb_id
            List<String> imdbIds = extractInfobox(item, "imdb_id");
            if (!imdbIds.isEmpty()) d.setImdbId(imdbIds.getFirst());

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

            // 获取角色/声优信息（仅当配置开启）
            if (castEnabled) {
                try {
                    String charJson = charFuture.join();
                    if (charJson != null) {
                        List<CastMemberDTO> cast = parseCast(charJson, st);
                        d.setCast(cast);
                    }
                } catch (Exception e) {
                    log.warn("Characters parse failed id={}: {}", subjectId, e.getMessage());
                }
            }
            return d;
        } catch (Exception e) {
            log.error("Bangumi getDetailed failed id={}", subjectId, e);
            return null;
        }
    }

    /**
     * 解析角色信息为内部 CastMemberDTO 结构。
     *
     * @param charJson Bangumi /subjects/{id}/characters 返回内容
     * @param subjectType 条目类型：2=动画，6=真人
     * @return 最多 12 位角色的角色出演列表
     */
    private List<CastMemberDTO> parseCast(String charJson, int subjectType) {
        List<CastMemberDTO> cast = new ArrayList<>();
        try {
            JsonNode chars = objectMapper.readTree(charJson);
            if (!chars.isArray()) return cast;
            int max = Math.min(chars.size(), 12);

            // 收集 JSON 节点并按 relation 排序，再构建 CastMemberDTO
            List<JsonNode> nodes = new ArrayList<>();
            for (int i = 0; i < max; i++) nodes.add(chars.get(i));
            nodes.sort(Comparator.comparingInt(c -> relationOrder(text(c, "relation"))));

            for (JsonNode c : nodes) {
                CastMemberDTO m = new CastMemberDTO();
                m.setId(c.has("id") && !c.get("id").isNull() ? c.get("id").asLong() : null);
                m.setName(text(c, "name"));
                JsonNode actors = c.get("actors");
                String actorName = null;
                String actorProfile = null;
                JsonNode picked = null;
                if (actors != null && actors.isArray() && !actors.isEmpty()) {
                    picked = pickActorNode(actors);
                    if (picked != null) {
                        actorName = text(picked, "name");
                        JsonNode actorImages = picked.get("images");
                        if (actorImages != null) actorProfile = text(actorImages, "medium");
                    }
                }
                if (actorName != null) {
                    m.setCharacter(actorName);
                    m.setActorId(picked.has("id") && !picked.get("id").isNull() ? picked.get("id").asLong() : null);
                }

                // 真人作品使用演员照片，动画使用角色图
                if (subjectType == 6 && actorProfile != null) {
                    m.setProfile(actorProfile);
                } else {
                    JsonNode images = c.get("images");
                    if (images != null) m.setProfile(text(images, "small"));
                }
                cast.add(m);
            }
        } catch (Exception e) {
            log.warn("parseCast failed: {}", e.getMessage());
        }
        return cast;
    }

    /** 角色重要度排序：主角=0, 配角=1, 客串=2, 其他=9 */
    private static int relationOrder(String relation) {
        if (relation == null) return 9;
        return switch (relation) {
            case "主角" -> 0;
            case "配角" -> 1;
            case "客串" -> 2;
            default -> 9;
        };
    }

    private List<Integer> getSearchTypes() {
        if (cachedSubjectTypes == null) {
            cachedSubjectTypes = subjectTypeRepository.findAll().stream()
                    .map(SubjectType::getCode).toList();
        }
        return cachedSubjectTypes;
    }


    /**
     * 将 Bangumi 搜索条目映射为统一 DTO。
     *
     * @param item Bangumi 原始条目节点
     * @return 标准化后的 {@link BangumiSubjectSummaryDTO}
     */
    private BangumiSubjectSummaryDTO mapSubject(JsonNode item) {
        BangumiSubjectSummaryDTO r = new BangumiSubjectSummaryDTO();
        r.setId(Long.parseLong(String.valueOf(item.get("id"))));
        r.setPlatform(text(item, "platform"));
        r.setSource("bangumi");

        String nameCn = text(item, "name_cn");
        String nameOrig = text(item, "name");

        r.setNameCn(nameCn != null && !nameCn.isBlank() ? nameCn : nameOrig);
        r.setNameOrig(nameOrig);

        JsonNode images = item.get("images");
        if (images != null) {
            String cover = text(images, "medium");
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

    /**
     * 获取角色中文名。
     *
     * <p>优先取 infobox 中“简体中文名”，无则回退“别名/第二中文名”。</p>
     *
     * @param id Bangumi 角色ID
     * @return 角色中文名，未命中返回 {@code null}
     */
    public String getCharacterName(Long id) {
        String base = settingsService.bangumiApiBase();
        try {
            String json = get(base + "/characters/" + id,
                    Math.max(1, characterTtlSeconds));
            if (json == null) return null;
            return extractChineseName(objectMapper.readTree(json));
        } catch (Exception e) {
            log.debug("getCharacterName failed id={}: {}", id, e.getMessage());
            return null;
        }
    }

    /**
     * 获取人物中文名。
     *
     * <p>优先取 infobox 中“简体中文名”，无则尝试别名字段回退。</p>
     *
     * @param id Bangumi 人物ID
     * @return 人物中文名，未命中返回 {@code null}
     */
    public String getPersonName(Long id) {
        String base = settingsService.bangumiApiBase();
        try {
            String json = get(base + "/persons/" + id,
                    Math.max(1, characterTtlSeconds));
            if (json == null) return null;
            return extractChineseName(objectMapper.readTree(json));
        } catch (Exception e) {
            log.debug("getPersonName failed id={}: {}", id, e.getMessage());
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
        return httpClient.get(url, ttlSeconds);
    }

    private String post(String url, String body, long ttlSeconds) {
        return httpClient.post(url, body, ttlSeconds);
    }

    private String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v != null && !v.isNull() ? v.asText() : null;
    }

    /**
     * 从演员列表中选出真人演员（名字不含 CJK 字符），排除日语吹替声优。
     * 若所有演员均为 CJK 名（动画作品），回退取第一个。
     * @return 选中的演员 JsonNode，含 images/name 等信息
     */
    private JsonNode pickActorNode(JsonNode actors) {
        for (JsonNode a : actors) {
            String name = text(a, "name");
            if (name != null && !containsCjk(name)) return a;
        }
        // 全是 CJK 名 → 动画，取第一个声优
        return actors.get(0);
    }

    private boolean containsCjk(String s) {
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.HIRAGANA
                || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.KATAKANA) {
                return true;
            }
        }
        return false;
    }
}
