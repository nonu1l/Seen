# AI 工具失败可见化方案

## Summary

把 AI-facing 工具统一改成“结构化结果返回”：工具失败不再只打日志或返回空列表，而是把 `ok/error/hint/data` 返回给 LLM，让自主 Agent 能判断是换关键词、换工具、重试，还是向用户说明外部搜索不可用。

核心原则：内部业务服务可以继续用现有返回类型；只在暴露给 LLM 的工具层做包装，避免大范围影响前端和普通业务接口。

## Key Changes

- 新增 AI 工具返回 DTO，避免直接把失败压成空值：
  - `WebSearchToolResultDTO`: `ok, query, provider, count, items, error, hint`
  - `FetchToolResultDTO`: `ok, url, status, contentType, title, text, truncated, error, hint`
  - `FindWorksToolResultDTO`: `ok, query, mode, cards, failReason, hint`
  - `WorkActionToolResultDTO`: `ok, action, subjectId, card, error, hint`

- 调整 LLM 暴露工具，不改变内部服务优先：
  - `searchWeb / web_search` 返回 `WebSearchToolResultDTO`，失败或 0 结果也明确返回原因。
  - `fetch_url` 返回增强后的结构化结果，保留 `status/error/text`，增加 `ok/hint`。
  - `fetchWeb` 不再只返回字符串，改为返回 `FetchToolResultDTO`；内部 `SearchPipeline` 继续使用文本抓取方法。
  - `findWorks` 返回 `FindWorksToolResultDTO`，把现有 `PipelineResult.failReason` 返回给 Agent。
  - `markWork / unmarkWork / presentWorks` 捕获可预期异常并返回 `WorkActionToolResultDTO`，避免整轮因为参数或本地记录问题直接失败。

- 调整工具实现边界：
  - `AiWebSearchTools` 保留内部 list/text 方法给 `SearchPipeline` 使用，新增或改造 AI-facing 方法返回结构化结果。
  - `AiToolRegistry` 只注册结构化工具方法，确保 LLM 看到的是 `ok/error/hint` 协议。
  - `WebSearchService` 增加轻量诊断返回能力，至少区分：搜索关闭、provider 不可用、provider 异常、0 结果。
  - `DDGSearchService` 保留重试日志，同时把“3 次后仍 0 结果”的信息传到上层诊断。

- 更新 Agent prompt：
  - 明确工具返回 `ok=false` 时不要声称已完成。
  - 搜索/抓取失败时优先换关键词或换工具。
  - 多次失败后向用户说明“搜索源不可用/未找到可靠结果”，而不是编造结果。
  - `findWorks.ok=false` 时可根据 `failReason/hint` 重新搜索或直接说明失败。

## Behavior

- `fetch_url` 超时：
  - LLM 收到 `ok=false, error="Connect timed out", hint="可以换一个公开资料源或改用 searchWeb"`。
- DDG 搜索 0 结果：
  - LLM 收到 `ok=false, count=0, error="DuckDuckGo returned 0 results after 3 attempts"`。
- `findWorks` 全流程失败：
  - LLM 收到 `ok=false, cards=[], failReason="已尝试 3 组关键词..."`。
- `unmarkWork` 本地不存在：
  - LLM 收到 `ok=false, error="本地没有可取消的记录"`，最终回复说明无法取消，而不是继续 Bangumi 搜索。

## Test Plan

- 单测：
  - `fetch_url` 超时时返回 `ok=false` 且包含 error。
  - `searchWeb` 在搜索关闭、provider 异常、0 结果时分别返回明确 error。
  - `findWorks` 空结果时返回 `failReason`，不只返回空 cards。
  - `markWork/unmarkWork` 参数缺失或本地不存在时返回结构化失败，不抛到整轮对话。

- 手动验证：
  - 让 Agent 搜索一个 DDG 0 结果关键词，最终回复能说明搜索不到，而不是沉默或编造。
  - 让 Agent 抓取超时 URL，能换源或说明访问失败。
  - 让 Agent 取消不存在的本地记录，能明确说“本地没有该记录”。
  - Token 明细中能看到工具失败结果进入下一次 LLM 输入，Agent 有机会自我修正。

## Assumptions

- 本阶段只改 AI 工具协议，不改前端 REST API。
- `SearchPipeline` 内部仍可继续使用 list/text 形式，避免一次性重构搜索流水线。
- 工具失败不自动重试无限次，只返回足够信息给 Agent 自主决策。
- `fetch_url` 继续是最完整的抓取工具；`fetchWeb` 可视为兼容工具，但返回结构也要统一。
