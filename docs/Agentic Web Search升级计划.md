# Agentic Web Search 升级计划

## 背景

当前项目已经支持 DuckDuckGo 与 Serper 搜索，但整体仍偏向“固定搜索源 + 固定流程”：生成关键词后调用搜索接口，再抓取页面、提炼标题、匹配 Bangumi。这个链路稳定、可控，但面对“近期热门电视剧”“类似某类榜单”“跨站点影视资料汇总”这类开放问题时，灵活性不如 CodeWhale 这类 Agentic Web Search。

CodeWhale 的网络搜索能力可以概括为：

- 由 LLM 自主判断是否需要联网、使用什么关键词、是否切换中文/英文搜索。
- 通过 `web_search` 获取候选网页，默认搜索源是 DuckDuckGo，也支持切换到 Bing、Tavily、Bocha 等 provider。
- 通过 `fetch_url` 抓取具体网页内容，再由 LLM 综合、筛选和验证。
- 它并不是影视专用 API，而是“LLM 编排 + 通用搜索 + 通用网页抓取”的组合。

## 技术选择

推荐在项目内实现类似 CodeWhale 的轻量 Agentic Web Search，而不是直接依赖 CodeWhale CLI。

理由：

- 项目是 Docker 自部署应用，直接调用本机 CLI 不利于部署、权限控制和跨平台运行。
- 当前已经有 `SearchProvider`、DuckDuckGo、Serper、网页抓取和 Spring AI Tool Calling 基础，适合内聚到项目自己的服务中。
- 自建搜索编排可以复用现有 Caffeine 缓存、反向代理设置、Token Usage 统计和设置页配置。

建议技术栈：

- 搜索 Provider：保留 DuckDuckGo 与 Serper，后续可增加 Tavily 或 Bocha。
- 网页抓取：使用 Spring Boot 4 的 `RestClient`，统一超时、重定向、User-Agent、响应大小限制。
- HTML 清洗：第一阶段继续使用轻量文本清洗；第二阶段可引入 Jsoup 提高正文提取质量。
- LLM 编排：使用 Spring AI 2.0 Tool Calling，让模型调用 `searchWeb`、`fetchWeb`、`searchBangumi`、`getBangumiDetail` 等工具。
- 缓存：继续使用当前 Caffeine 请求缓存，对搜索结果和网页抓取结果分别缓存。
- 安全控制：实现 URL 白名单/黑名单、内网地址拦截、最大响应大小、最大抓取次数和总耗时限制。

## 实施阶段

### 第一阶段：把搜索能力抽象成 Agent 工具

- 保留现有 `SearchProvider` 接口，统一 DuckDuckGo 与 Serper 的返回结构。
- 将 `searchWeb(query)` 与 `fetchWeb(url)` 明确作为 AI 工具暴露。
- 增加工具调用限制：单次会话最多搜索 3 次、抓取 5 个页面、总耗时不超过 30 秒。
- 搜索结果只返回标题、摘要、URL、来源，不直接把完整 HTML 交给模型。
- 网页抓取结果做正文清洗与长度截断，默认最多返回 6000 字符。

### 第二阶段：让推荐 / 搜索流程支持自主编排

- 将现有 `SearchPipeline` 拆成两种模式：
  - 快速模式：沿用当前固定 pipeline，适合明确片名、短关键词搜索。
  - Agentic 模式：由 LLM 自主决定搜索关键词、搜索语言、抓取页面和 Bangumi 匹配策略。
- 对“热门、近期、榜单、类似、推荐、哪部好看”等开放问题优先进入 Agentic 模式。
- 对“标记某部作品、查具体片名、打开详情”等明确问题继续走固定流程，减少 token 和网络开销。
- Agentic 模式最终仍必须输出 Bangumi subjectId 或明确失败原因，避免只给网页标题而无法落到项目卡片。

### 第三阶段：增加影视领域站点策略

- 在提示词中告诉模型优先考虑影视相关来源，例如 Bangumi、豆瓣、IMDb、Rotten Tomatoes、AniList、TMDb、各平台榜单页。
- 不为这些站点一开始写死专用 API，先通过搜索和抓网页完成通用能力。
- 对稳定且高价值的站点再逐步沉淀为专用工具，例如 AniList GraphQL、TMDb API、Bangumi API。
- 对中文热门影视问题，可增加搜索策略：
  - 中文关键词 + “豆瓣 / 榜单 / 热播 / 评分”
  - 英文关键词 + “best tv shows / streaming / rotten tomatoes”
  - `site:` 限定搜索作为可选策略，而不是固定规则。

### 第四阶段：设置页与观测能力

- 设置页增加搜索模式选择：
  - 标准搜索
  - 智能搜索
  - 自动选择
- 增加 Provider 设置：
  - DuckDuckGo
  - Serper
  - 后续 Tavily / Bocha
- Token Usage 页面继续记录 Agentic 搜索中的节点调用。
- 请求缓存页面展示搜索结果缓存和网页抓取缓存，方便排查搜索质量和网络问题。
- 日志记录每次 Agentic 搜索使用的关键词、抓取 URL、命中卡片数量和失败原因。

## 接口与数据流

推荐数据流：

```text
用户输入
  -> Intent 判断
  -> 固定搜索 / Agentic 搜索二选一
  -> searchWeb 多轮搜索
  -> fetchWeb 抓取候选页面
  -> LLM 提炼候选影视标题
  -> Bangumi 搜索与校验
  -> 输出可标记卡片或失败原因
```

建议新增或整理的内部能力：

- `WebSearchTool.search(query, maxResults)`：返回网页候选。
- `WebFetchTool.fetch(url)`：抓取并清洗网页正文。
- `SearchStrategyService`：判断使用固定 pipeline 还是 Agentic 搜索。
- `AgenticSearchService`：负责 LLM 工具编排、预算限制和结果收敛。
- `SearchObservation`：记录关键词、URL、耗时、来源、结果数量和错误信息。

## 风险与约束

- 搜索结果存在噪声，LLM 可能把网页文章里的未验证信息当事实，需要保留 Bangumi 匹配与结果校验。
- 抓网页必须防 SSRF，禁止访问 localhost、内网地址、file 协议和非 HTTP(S) 协议。
- Agentic 搜索会增加 token 和网络耗时，需要默认设置预算上限。
- DuckDuckGo HTML 端点可能受网络环境影响，Serper 需要 API Key，新增 provider 应作为可选项。
- 不建议让模型直接访问任意大型页面全文，应统一截断并清洗。

## 测试计划

- 搜索明确片名时仍走固定流程，响应速度不明显下降。
- 搜索“2026 年近期热门电视剧推荐”时，Agentic 模式能生成多组关键词并抓取多个来源。
- 搜索结果最终能映射到 Bangumi 卡片，而不是只返回网页列表。
- DuckDuckGo 不可用时，配置 Serper 后能正常 fallback。
- 抓取内网地址、超大页面、非 HTML 内容时能安全失败。
- 请求缓存页面能看到搜索与网页抓取缓存。
- Token Usage 能区分关键词生成、网页提炼、Bangumi 校验等节点。

## 推荐结论

短期不需要引入重型浏览器自动化，也不建议直接依赖 CodeWhale CLI。最合适的路线是：基于 Spring AI 2.0 Tool Calling、现有 SearchProvider、RestClient、Caffeine 缓存，构建项目自己的轻量 Agentic Web Search。

这样可以保留当前固定 pipeline 的稳定性，同时补上 CodeWhale 那种“会自己换关键词、换来源、抓网页再综合”的灵活性。
