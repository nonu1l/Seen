package com.nonu1l.media.agent.tool;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Component;

/**
 * 集中注册 AI 可调用工具，避免 Agent 节点直接关心工具构造细节。
 */
@Component
public class AiToolRegistry {

    private final AiBangumiTools bangumiTools;
    private final AiWebSearchTools webSearchTools;
    private final AiAutonomousTools autonomousTools;

    /**
     * @param bangumiTools Bangumi 查询工具
     * @param webSearchTools Web 搜索工具
     * @param autonomousTools 自主 Agent 工具门面
     */
    public AiToolRegistry(AiBangumiTools bangumiTools,
                          AiWebSearchTools webSearchTools,
                          AiAutonomousTools autonomousTools) {
        this.bangumiTools = bangumiTools;
        this.webSearchTools = webSearchTools;
        this.autonomousTools = autonomousTools;
    }

    /**
     * 构建 Agent 工具回调列表：Bangumi 搜索、本地搜索、网络搜索、网页抓取。
     *
     * @return 可供 Spring AI 注册的工具回调数组
     */
    public ToolCallback[] callbacks() {
        return new ToolCallback[] {
            FunctionToolCallback.builder("searchBangumi",
                    (SearchReq req) -> bangumiTools.searchBangumi(req.keyword()))
                .description("搜索 Bangumi 影视数据库")
                .inputType(SearchReq.class).build(),
            FunctionToolCallback.builder("searchLocal",
                    (SearchReq req) -> autonomousTools.searchLocal(req.keyword()))
                .description("查询本地已标记的作品记录")
                .inputType(SearchReq.class).build(),
            FunctionToolCallback.builder("getWorkState",
                    (WorkStateReq req) -> autonomousTools.getWorkState(req.subjectId()))
                .description("按 Bangumi subjectId 查询单个本地作品当前状态")
                .inputType(WorkStateReq.class).build(),
            FunctionToolCallback.builder("findWorks",
                    (FindWorksReq req) -> autonomousTools.findWorksForAgent(req.query(), req.mode()))
                .description("根据推荐、搜索或描述找片需求查找影视作品候选")
                .inputType(FindWorksReq.class).build(),
            FunctionToolCallback.builder("presentWorks",
                    (PresentWorksReq req) -> autonomousTools.presentWorksForAgent(req.subjectIds(), req.reason()))
                .description("把候选作品保存为 AI 页面 PENDING 展示卡片，不写入用户观看记录")
                .inputType(PresentWorksReq.class).build(),
            FunctionToolCallback.builder("markWork",
                    (MarkWorkReq req) -> autonomousTools.markWorkForAgent(req.subjectId(), req.status(), req.rating(), req.review(), req.reason()))
                .description("直接标记、评分或修改影评；会保存记录并生成可撤销 SAVED 卡片")
                .inputType(MarkWorkReq.class).build(),
            FunctionToolCallback.builder("unmarkWork",
                    (UnmarkWorkReq req) -> autonomousTools.unmarkWorkForAgent(req.subjectId(), req.reason()))
                .description("取消本地已有作品标记；会删除作品记录并生成可撤回 UNMARKED 卡片")
                .inputType(UnmarkWorkReq.class).build(),
            FunctionToolCallback.builder("readUserMemory",
                    (MemoryReq req) -> autonomousTools.readUserMemory(req.query()))
                .description("按需读取用户长期偏好记忆；推荐时可参考但当前用户请求优先")
                .inputType(MemoryReq.class).build(),
            FunctionToolCallback.builder("searchWeb",
                    (SearchReq req) -> webSearchTools.searchWebForAgent(req.keyword()))
                .description("搜索引擎")
                .inputType(SearchReq.class).build(),
            FunctionToolCallback.builder("web_search",
                    (SearchReq req) -> webSearchTools.searchWebForAgent(req.keyword()))
                .description("使用当前搜索源检索网页候选结果；智能选择模式会优先 Serper，失败后切换 DuckDuckGo")
                .inputType(SearchReq.class).build(),
            FunctionToolCallback.builder("fetchWeb",
                    (FetchReq req) -> webSearchTools.fetchWebForAgent(req.url()))
                .description("抓取网页纯文本")
                .inputType(FetchReq.class).build(),
            FunctionToolCallback.builder("fetch_url",
                    (FetchUrlReq req) -> webSearchTools.fetchUrlForAgent(req.url(), req.purpose(), req.maxChars()))
                .description("直接访问公开 HTTP(S) URL 或公开 API，返回状态、内容类型和清洗文本；搜索源不可用时可用于获取榜单或资料")
                .inputType(FetchUrlReq.class).build(),
        };
    }

    public record SearchReq(String keyword) {}
    public record WorkStateReq(Long subjectId) {}
    public record FindWorksReq(String query, String mode) {}
    public record PresentWorksReq(java.util.List<Long> subjectIds, String reason) {}
    public record MarkWorkReq(Long subjectId, String status, Double rating, String review, String reason) {}
    public record UnmarkWorkReq(Long subjectId, String reason) {}
    public record MemoryReq(String query) {}
    public record FetchReq(String url) {}
    public record FetchUrlReq(String url, String purpose, Integer maxChars) {}
}
