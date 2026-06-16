# Agentic Web Search 升级计划

> 面向执行 Agent 的实施文档。后续 Agent 接到该任务时，应按本文顺序读取、修改、验证，并保持代码改动小而完整。

## 0. 本轮探究结论

### CodeWhale 行为观察

从 `docs/CodeWhale 搜索流程.md` 的记录和本轮 CodeWhale 只读分析可见，CodeWhale 的灵活性主要来自以下组合：

1. 先让模型判断是否需要联网，并自主生成多组搜索关键词。
2. 优先调用 `web_search`，其默认会访问 DuckDuckGo HTML 搜索端点。
3. 当 `web_search` 多次失败时，模型没有直接停止，而是基于自身知识选择公开 URL/API。
4. 随后调用 `fetch_url` 直接访问 `https://api.jikan.moe/v4/top/anime?filter=airing&limit=10`。
5. 拿到 JSON 后再由模型提炼榜单结果。

结论：要学习的是“搜索工具失败后的 Agentic 直访 URL 能力”，不是盲目增加更多搜索 provider。

### 当前项目现状

当前项目已有基础能力：

- `AiToolRegistry` 已注册 `searchWeb` 和 `fetchWeb`。
- `AiWebSearchTools` 已把工具调用转给 `WebSearchService`。
- `WebSearchService` 已支持 Serper / DuckDuckGo 路由。
- 设置页目前只有 `Serper` 和 `DuckDuckGo` 两个搜索源选项。

需要升级的点：

- LLM 面向工具名应补充 CodeWhale 风格的 `fetch_url`，并可保留 `fetchWeb` 兼容现有代码。
- `fetch_url` 需要从搜索 provider 中解耦，成为独立网页读取工具，而不是依赖当前选择的搜索源。
- 搜索源仍只允许 `Serper` / `DuckDuckGo`，但设置页新增“智能选择”。
- 当 Serper 和 DuckDuckGo 都不可用时，Agent 应允许模型通过 `fetch_url` 直接访问公开影视榜单、资料页或公开 API。

## 1. 硬性约束

执行本计划时必须遵守：

- 搜索 provider 只保留 `Serper` 和 `DuckDuckGo`。
- 本轮不要新增 Tavily、Bocha、Bing、Playwright 浏览器搜索等 provider。
- `fetch_url` 不是搜索 provider，它是通用网页/API 读取工具。
- `fetch_url` 必须暴露给 LLM，由 LLM 自主决定何时访问哪个 URL。
- `fetch_url` 必须有 SSRF 防护、超时、响应大小限制、重定向校验和内容清洗。
- 搜索不可用时允许模型直访 URL，但最终仍要尽量落到 Bangumi subjectId 或给出明确失败原因。
- 代码实现完成后需要提交代码；不需要编译 Java 代码。

## 2. 目标能力

用户问“最近热门电视剧”“近期热门动画”“2026 年值得看的新番”时，Agent 应按下面策略执行：

```text
用户输入
  -> 意图识别：开放式推荐 / 热门榜单 / 描述找片
  -> web_search：按设置选择 Serper / DuckDuckGo / 智能选择
  -> 若搜索结果足够：fetch_url 抓取候选页面
  -> 若 Serper 与 DuckDuckGo 都不可用：LLM 自主选择公开 URL 并调用 fetch_url
  -> LLM 从页面/API 内容中提炼候选作品
  -> searchBangumi / getBangumiDetail 校验并生成卡片
  -> 输出 cards 或明确失败原因
```

核心效果：

- 搜索源正常时，继续走搜索引擎。
- 搜索源不稳定时，能自动切换。
- 搜索源都不可用时，不直接失败，而是像 CodeWhale + DeepSeek 一样，由模型选择它知道的公开 URL 继续获取信息。

## 3. 搜索源配置

### 设置值

`search.provider` 支持三个值：

| 值 | 前端文案 | 行为 |
| --- | --- | --- |
| `auto` | 智能选择 | 优先 Serper；Serper 不可用、失败、超时或返回空结果时，再使用 DuckDuckGo |
| `serper` | Serper | 只使用 Serper，适合调试 API Key 与搜索质量 |
| `ddg` | DuckDuckGo | 只使用 DuckDuckGo，适合无 API Key 的轻量部署 |

推荐默认值调整为 `auto`。如果担心影响老用户，可先保持默认 `ddg`，但新 UI 必须提供“智能选择”。

### 后端变更点

修改 `SettingsService`：

- `normalizeProvider` 允许 `auto`。
- `SettingDefinition(SEARCH_PROVIDER, ...)` 默认值按最终决策设为 `auto` 或维持 `ddg`。
- `SettingsResponse.SourceSettings.searchProvider` 能返回 `auto`。

修改 `WebSearchService`：

- 把当前 `delegate()` 升级为 `searchWithFallback(query)`。
- `auto` 模式按 Serper -> DuckDuckGo 顺序执行。
- fallback 条件包括：未配置 API Key、HTTP 异常、超时、429/403、解析失败、返回空结果。
- 每次尝试记录 `provider`、耗时、结果数、错误原因。
- 不要在 `fetch_url` 中复用 provider 选择逻辑。

### 前端变更点

修改 `frontend/src/pages/SettingsPage.tsx`：

- `SourceValues.searchProvider` 类型改为 `'auto' | 'serper' | 'ddg'`。
- “搜索源” segmented control 增加“智能选择”按钮。
- 推荐顺序：`智能选择`、`Serper`、`DuckDuckGo`。
- 测试搜索时，如果选择 `auto`，后端返回实际使用的 provider 和 fallback 过程。

同步修改 `frontend/src/api/types.ts` 中相关类型。

## 4. `fetch_url` 工具设计

### LLM 工具命名

新增 LLM 面向工具名：

- `fetch_url`：主要工具名，贴近 CodeWhale 行为。
- `fetchWeb`：可以暂时保留，兼容旧 prompt / 旧代码。

建议同时把搜索工具补充 snake_case 别名：

- `web_search`：LLM 面向名称。
- `searchWeb`：兼容现有代码。

### 入参

```java
public record FetchUrlReq(
        String url,
        String purpose,
        Integer maxChars
) {}
```

字段说明：

- `url`：模型要访问的完整 URL，仅允许 `http` / `https`。
- `purpose`：本次抓取目的，例如“获取当前动画榜单”。
- `maxChars`：可选，默认 6000，上限 12000。

### 出参

不要再只返回 `String` 或 `null`，应返回结构化结果，便于模型判断下一步：

```java
public record FetchUrlResult(
        String url,
        int status,
        String contentType,
        String title,
        String text,
        boolean truncated,
        String error
) {}
```

规则：

- 成功时 `error = null`。
- 失败时 `text = ""`，`error` 写明原因。
- JSON 响应保留关键结构文本，不要当 HTML 清洗。
- HTML 响应移除 script/style/nav/footer 等噪音，保留正文。
- 过长响应截断并设置 `truncated = true`。

### 实现位置

建议新增：

- `WebFetchService`：独立负责 URL 抓取、安全校验、文本清洗。
- `FetchUrlResult` DTO。
- `AiWebSearchTools.fetchUrl(...)`：调用 `WebFetchService`。

现有 `DDGSearchService.fetch` 和 `SerperSearchService.fetch` 可以逐步废弃或内部转调 `WebFetchService`。

## 5. 搜索都不可用时的 Agentic 直访 URL

### 触发条件

当下面任一条件成立时，进入 direct-fetch fallback：

- `auto` 模式下 Serper 和 DuckDuckGo 都失败。
- 固定 provider 返回空结果，且用户问题明显依赖实时/榜单/热门信息。
- `web_search` 工具返回结构化错误，表明搜索源不可用。

### 允许模型做什么

模型可以根据问题自主选择公开 URL，例如：

- 动画 / 新番：
  - `https://api.jikan.moe/v4/top/anime?filter=airing&limit=20`
  - `https://api.jikan.moe/v4/seasons/now`
  - `https://bangumi.tv/anime/browser/airtime`
- 电影 / 剧集：
  - IMDb chart / search 页面
  - 豆瓣电影榜单或分类页面
  - Rotten Tomatoes 榜单页面
  - TMDb 公开页面
- 综合资料：
  - Bangumi 条目 / 排行页面
  - AniList 公开页面
  - 影视平台公开热播榜页面

这些 URL 不是新增搜索源，只是 `fetch_url` 的可访问内容来源。模型负责选择，系统负责安全和预算。

### Prompt 要求

更新 `agent-system.st`，增加类似规则：

```text
当 web_search 返回空、超时或提示 Serper/DuckDuckGo 均不可用时，不要立即失败。
你可以调用 fetch_url 直接访问你知道的公开影视榜单、资料页或公开 API。
优先选择与用户意图强相关的公开页面，例如 Bangumi、豆瓣、IMDb、AniList、Jikan/MyAnimeList、TMDb、各视频平台公开榜单。
fetch_url 返回内容后，必须继续提炼候选作品，并调用 searchBangumi 校验，尽量输出 cards。
如果 fetch_url 也失败，再输出明确失败原因。
```

### 执行预算

单轮 Agentic 搜索默认预算：

| 工具 | 上限 |
| --- | --- |
| `web_search` / `searchWeb` | 3 次 |
| `fetch_url` / `fetchWeb` | 5 次 |
| `searchBangumi` | 10 次 |
| 总耗时 | 30 秒 |
| 单个页面返回文本 | 默认 6000 字符，上限 12000 字符 |

达到预算后，Agent 必须基于已有信息收敛回答，不允许无限重试。

## 6. 安全边界

`fetch_url` 必须在发请求前完成校验：

- 只允许 `http` / `https`。
- 禁止 `localhost`、`127.0.0.1`、`0.0.0.0`、`::1`。
- 禁止私有网段、链路本地地址、保留地址和内网 DNS 解析结果。
- 禁止 `file:`、`jar:`、`ftp:`、`gopher:` 等协议。
- 跟随重定向前后都必须重新校验 URL 和 IP。
- 设置 `User-Agent: seen-app-agentic-fetch/1.0`。
- 连接超时和读取超时建议 8-10 秒。
- 响应体最大读取 1MB，返回给模型前再做文本截断。
- 日志不得输出 API Key、Cookie、Authorization。
- 默认不携带用户 Cookie。

实现时优先用 JDK URI/URL 解析和 DNS 解析，不要用字符串前缀判断代替安全校验。

## 7. Agent 执行步骤

### 第一步：设置与路由

1. 修改 `SettingsService`，支持 `auto`。
2. 修改 `SettingsTestService`，让测试搜索能展示 `auto` 的实际尝试过程。
3. 修改 `WebSearchService`，实现 Serper -> DuckDuckGo fallback。
4. 保留 Serper / DuckDuckGo 两个底层 provider，不新增第三方搜索 provider。

验收：

- 设置为 `auto` 且 Serper Key 存在时，优先调用 Serper。
- Serper Key 缺失、失败或空结果时，自动调用 DuckDuckGo。
- 设置为 `ddg` 时不调用 Serper。

### 第二步：独立 `fetch_url`

1. 新增 `WebFetchService`。
2. 把 URL 安全校验、HTTP 抓取、HTML/JSON 文本处理放入该服务。
3. 在 `AiWebSearchTools` 中新增 `fetchUrl` 方法。
4. 在 `AiToolRegistry` 中注册 `fetch_url`。
5. 旧的 `fetchWeb` 工具保留一段时间，内部复用同一服务。

验收：

- LLM 可以调用 `fetch_url("https://api.jikan.moe/v4/top/anime?filter=airing&limit=10")`。
- 内网 URL、非 HTTP(S) URL、超大响应安全失败。
- 返回结果包含状态、内容类型、文本、截断标记和错误信息。

### 第三步：Prompt 与 Agentic fallback

1. 更新 `agent-system.st`。
2. 明确热门/近期/榜单/推荐类问题优先使用 `web_search`。
3. 明确搜索不可用时可使用 `fetch_url` 直访公开 URL。
4. 明确直访 URL 后仍需 `searchBangumi` 校验并生成 cards。

验收：

- 搜索“近期热门动画”时，搜索源失败后模型会尝试 Jikan、Bangumi、豆瓣、IMDb 等公开 URL。
- 最终结果尽量是可保存卡片，而不是纯文字榜单。
- 所有工具调用在预算内停止。

### 第四步：前端设置

1. 设置页搜索源增加“智能选择”。
2. 保存时提交 `search.provider = auto`。
3. 测试搜索展示成功 provider 或 fallback 失败原因。

验收：

- 用户可在设置页选择“智能选择”。
- 刷新页面后选择状态保持。
- 保存和测试不破坏现有 Serper / DuckDuckGo 配置。

### 第五步：观测与缓存

1. 记录每次搜索尝试：provider、query、结果数、耗时、错误。
2. 记录每次 `fetch_url`：host、status、contentType、文本长度、是否截断、错误。
3. 搜索结果和 fetch 结果继续使用现有缓存体系或新增独立 Caffeine 缓存。
4. Token Usage 中区分 `agentic-web-search`、`fetch-url`、`bangumi-validate`。

验收：

- 日志能解释为什么从 Serper fallback 到 DuckDuckGo。
- 日志能解释为什么进入 direct-fetch fallback。
- 不记录敏感请求头或 API Key。

## 8. 测试清单

后续实现 Agent 至少覆盖以下场景：

- `search.provider = auto`，Serper 有 Key 且返回结果：只使用 Serper。
- `search.provider = auto`，Serper 无 Key：使用 DuckDuckGo。
- `search.provider = auto`，Serper 返回空或异常：fallback DuckDuckGo。
- Serper 与 DuckDuckGo 都失败：模型可调用 `fetch_url` 访问公开榜单/API。
- `fetch_url` 访问 `localhost`、内网 IP、`file:` 协议：必须失败。
- `fetch_url` 访问 HTML 页面：返回清洗正文。
- `fetch_url` 访问 JSON API：返回可读 JSON 摘要或原始截断文本。
- “近期热门动画”最终能提炼候选标题并调用 Bangumi 匹配。
- “搜索某个具体片名”仍能走低成本固定流程，不强制 Agentic 多轮抓取。

## 9. 推荐实施顺序

优先顺序：

1. `auto` 搜索源与 fallback。
2. 独立、安全、结构化的 `fetch_url`。
3. Prompt 中加入搜索失败后的 direct-fetch fallback。
4. 前端设置页增加“智能选择”。
5. 日志、缓存、Token Usage 观测增强。

不要先做：

- 新增更多搜索 provider。
- 引入浏览器自动化。
- 为豆瓣、IMDb、AniList 写复杂专用客户端。

当前最小可行版本是：`Serper / DuckDuckGo web_search` + `fetch_url` 直访公开 URL + Bangumi 校验。这样既保留项目当前搜索链路的稳定性，又补上 CodeWhale 那种“搜索失败后仍能自己找资料源”的 Agentic 灵活性。
