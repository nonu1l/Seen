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
    private final AiLocalLibraryTools localLibraryTools;
    private final AiWebSearchTools webSearchTools;

    /**
     * @param bangumiTools Bangumi 查询工具
     * @param localLibraryTools 本地媒体库工具
     * @param webSearchTools Web 搜索工具
     */
    public AiToolRegistry(AiBangumiTools bangumiTools,
                          AiLocalLibraryTools localLibraryTools,
                          AiWebSearchTools webSearchTools) {
        this.bangumiTools = bangumiTools;
        this.localLibraryTools = localLibraryTools;
        this.webSearchTools = webSearchTools;
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
                    (SearchReq req) -> localLibraryTools.searchLocal(req.keyword()))
                .description("查询本地已标记的作品记录")
                .inputType(SearchReq.class).build(),
            FunctionToolCallback.builder("searchWeb",
                    (SearchReq req) -> webSearchTools.searchWeb(req.keyword()))
                .description("搜索引擎")
                .inputType(SearchReq.class).build(),
            FunctionToolCallback.builder("fetchWeb",
                    (FetchReq req) -> webSearchTools.fetchWeb(req.url()))
                .description("抓取网页纯文本")
                .inputType(FetchReq.class).build(),
        };
    }

    public record SearchReq(String keyword) {}
    public record FetchReq(String url) {}
}
