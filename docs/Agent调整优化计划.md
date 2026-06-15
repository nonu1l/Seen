# Agent 调整 / 优化计划

本文面向后续执行代码调整的 Agent。目标是清理 Spring AI 2.0 升级后的遗留结构，降低重复代码和维护成本，同时严格避免影响现有功能。

## 执行原则

- 每一轮只做一个风险等级或一个明确主题，不要把后端结构调整和前端大组件拆分混在同一提交里。
- 优先删除确认无引用的遗留代码，再做合并，再做拆分。
- 不改业务语义：AI 助手、设置保存即生效、Bangumi 搜索、Serper / DDG 切换、Token 用量记录、影视标记流程必须保持现状。
- Java 编译由用户执行；Agent 不编译 Java。
- 前端改动后执行 `npm run build`。
- 涉及删除类、方法、DTO 时，先用 `rg` 确认引用，再改。
- 涉及接口响应结构时，同步更新 `frontend/src/api/types.ts` 和调用处。

## 低风险

### 1. 删除明确弃用且无业务引用的 DTO

目标文件：

- `src/main/java/com/nonu1l/media/model/dto/ParseRequest.java`
- `src/main/java/com/nonu1l/media/model/dto/ParseResult.java`

背景：

- 文件注释已标明“已弃用，仅保留代码”。
- 当前会话入口使用 `AiChatRequest`，没有旧 parse 接口。
- `ParseResult` 仍使用旧 `com.fasterxml.jackson.annotation.JsonInclude`，属于升级后的遗留点。

执行：

- 用 `rg -n "ParseRequest|ParseResult" src/main/java frontend/src` 再确认。
- 无真实引用则删除文件。

验证：

- `rg -n "ParseRequest|ParseResult" src/main/java frontend/src` 无业务引用。

### 2. 清理注释掉的旧接口和未调用方法

目标区域：

- `src/main/java/com/nonu1l/media/controller/WorksController.java` 中注释掉的 `/rewatch` 接口。
- `src/main/java/com/nonu1l/media/service/WorkService.java` 中未调用的 `updateReviewNew`。

背景：

- `rewatch` 已整段注释，前端没有有效入口。
- `updateReviewNew` 当前无调用方，和 `updateReview` 并存会制造“到底用覆盖还是历史记录”的歧义。

执行：

- 删除注释块。
- 删除 `updateReviewNew`，除非发现隐藏调用或近期明确要恢复多刷功能。

验证：

- `rg -n "rewatch|updateReviewNew" src/main/java frontend/src` 确认只剩必要字段或无命中。

### 3. 合并 Web MVC 配置类

目标文件：

- `src/main/java/com/nonu1l/media/config/WebConfig.java`
- `src/main/java/com/nonu1l/media/config/WebMvcConfig.java`

背景：

- 两者都实现 `WebMvcConfigurer`。
- 一个负责 CORS / 静态资源，一个负责注册 `AiFeatureGuard`。
- 同属 MVC 配置，拆成两个类收益不大。

执行：

- 保留一个 `WebMvcConfig`。
- 将 `addCorsMappings`、`addResourceHandlers`、`addInterceptors` 合并到同一类。
- 删除另一个配置类。

验证：

- 前端 dev 代理 `/api` 正常。
- `/api/conversation/**` 在 AI 关闭时仍返回 403。
- 静态资源访问不受影响。

### 4. 前端状态元数据统一

目标文件：

- `frontend/src/components/StatusBadge.tsx`
- `frontend/src/components/WorkCard.tsx`
- `frontend/src/components/QuickMarkMenu.tsx`
- `frontend/src/components/AiCard.tsx`
- `frontend/src/components/WorkDetailModal.tsx`
- `frontend/src/pages/HomePage.tsx`

背景：

- `Status` 的 label、颜色、过滤顺序多处手写。
- 状态新增或改名时容易漏改。

执行：

- 新增 `frontend/src/utils/statusMeta.ts` 或 `frontend/src/constants/status.ts`。
- 导出 `STATUS_META`、`STATUS_OPTIONS`、`STATUS_FILTERS`。
- 保持原 label、颜色、排序不变。

验证：

- `npm run build`。
- 首页过滤、快捷标记、详情页标记、AI 卡片状态显示一致。

### 5. 清理前端疑似未使用资源

目标：

- `frontend/src/components/AiDialog.tsx`
- `frontend/src/components/StatusBadge.tsx`
- `frontend/package.json` 中疑似未使用的 `keen-slider`
- `frontend/src/index.css` 中疑似未用类，如 `.rewatch-pill`、`.ai-content--exit`、`.settings-segmented-4`

执行：

- 先用 `rg` 确认无引用。
- 分批删除，避免一次删太多难定位问题。
- 删除依赖时同步 lockfile。

验证：

- `npm run build`。
- 首页、AI 页、设置页视觉无明显变化。

## 中风险

### 1. 统一 Jackson 3 注解包

目标文件：

- `src/main/java/com/nonu1l/media/model/dto/*.java`

背景：

- 服务代码已使用 `tools.jackson.databind.*`。
- 多个 DTO 仍使用 `com.fasterxml.jackson.annotation.JsonInclude`。
- Spring Boot 4 / Jackson 3 下混用旧包会增加编译和运行期风险。

执行：

- 全局搜索：`rg -n "com\\.fasterxml\\.jackson" src/main/java`。
- 将仍需要的注解迁移到 Jackson 3 对应包。
- 如果某些 `JsonInclude` 对接口响应影响不大，可考虑直接删除注解，但必须确认前端可接受 null 字段。

风险点：

- 删除或更换 `JsonInclude` 可能改变 JSON 中 null 字段是否输出。
- 前端类型里大量字段允许 `null`，但仍需手测重点页面。

验证：

- 用户执行 Java 编译。
- 前端页面检查：作品列表、搜索结果、详情弹窗、AI 卡片。

### 2. 抽出 SettingsTestService

目标文件：

- `src/main/java/com/nonu1l/media/controller/SettingsController.java`
- 新增 `src/main/java/com/nonu1l/media/service/SettingsTestService.java`

背景：

- `SettingsController` 当前包含 AI 测试、Serper 测试、DDG 测试、Bangumi 测试、错误脱敏、URL 拼接。
- Controller 职责偏重，并且与 `AiChatClientFactory`、`SerperSearchService`、`DDGSearchService` 存在部分重复。

执行：

- Controller 只保留路由和请求转发。
- 将测试逻辑移动到 `SettingsTestService`。
- 保留现有响应结构 `SettingsTestResponse`。
- 不改前端 API。

风险点：

- AI 测试支持 draft 表单，不能只读已保存配置。
- 错误信息必须继续脱敏。
- Serper draft key 和 Bangumi draft proxy 都要继续生效。

验证：

- 设置页测试 AI。
- 设置页测试搜索，分别覆盖 Serper 与 DuckDuckGo。
- 设置页测试 Bangumi。

### 3. 归一 AI Provider 辅助逻辑

目标区域：

- `AiChatClientFactory.usesThinkingToggle`
- `SettingsController.usesThinkingToggle`
- 多处 `trimTrailingSlash`
- `SettingsService.inferProviderKind`

背景：

- Provider 判断、URL 规范化逻辑分散。
- GLM / DeepSeek 这类兼容模型路径处理后续还会扩展。

执行：

- 新增小工具类，如 `AiProviderSupport`。
- 提供：
  - `inferProviderKind(String baseUrl)`
  - `usesThinkingToggle(String providerKind)`
  - `trimTrailingSlash(String value)`
  - `chatCompletionsUrl(String baseUrl)`
- 先迁移 AI 相关调用，不要扩大到所有 URL 处理。

风险点：

- `chatCompletionsUrl` 必须保持当前拼接规则：`baseUrl + "/chat/completions"`。
- GLM 的 `baseUrl` 仍应填写 `https://open.bigmodel.cn/api/paas/v4`，不单独配置 completions path。

验证：

- DeepSeek / GLM 的设置测试。
- AI 正常对话。

### 4. 拆分 SettingsPage 的纯 UI 控件

目标文件：

- `frontend/src/pages/SettingsPage.tsx`

建议新增：

- `frontend/src/components/settings/SettingsRow.tsx`
- `frontend/src/components/settings/ToggleRow.tsx`
- `frontend/src/components/settings/SecretInput.tsx`
- `frontend/src/components/settings/TestResult.tsx`

背景：

- `SettingsPage` 同时承担状态、保存、测试和大段表单 JSX。
- API Key 与 Serper API Key 输入重复。

执行：

- 只拆纯展示组件。
- 不改保存逻辑、不改 dirty 逻辑、不改接口调用顺序。
- `SecretInput` 保持当前行为：隐藏态显示固定星号，显示态显示明文，已有 key 隐藏态只读。

验证：

- `npm run build`。
- API Key / Serper API Key 显示、隐藏、编辑、保存。
- 保存后 toast 正常。

### 5. 修复前端明显的小风险点

目标：

- `frontend/src/api/proxy.ts`
- `frontend/src/components/AppLayout.tsx`
- `frontend/src/pages/AiPage.tsx`
- `frontend/src/api/client.ts`

建议：

- `setBangumiProxy("")` 时删除 `localStorage` 中的 `bgm_proxy`。
- 将 reset 注册从容易误用的 setter 包装成 `registerReset(fn)`。
- API 错误响应优先读取 `{error}` / `{message}`，保留 fallback。

风险点：

- reset 按钮必须在 AI 页面继续工作。
- 错误处理不要在非 JSON 响应上二次抛错。

验证：

- `npm run build`。
- AI 页重置按钮。
- 清空 Bangumi 代理后封面 URL 行为。

## 高风险

### 1. 拆分 WorkService 领域职责

目标文件：

- `src/main/java/com/nonu1l/media/service/WorkService.java`

问题：

- 当前混合列表查询、搜索合并、详情组装、标记、记录历史、字典、角色名代理。
- 事务边界和 DTO 组装耦合较重。

建议方向：

- 先抽纯 mapper：`WorkMapper`，负责 `Work` / `Record` / `WorkSearchResult` 到 `WorkListItem` / `WorkDetail`。
- 再考虑抽 `WorkRecordService`，负责 mark / unmark / updateReview。
- 保留 `WorkService` 作为应用服务门面，避免 Controller 大改。

风险点：

- 标记逻辑涉及历史记录、AI 卡片保存、撤销。
- 不建议一次性大拆。

验证：

- 首页列表。
- 搜索远端作品并标记。
- 取消标记。
- 更新评分/评价。
- AI 卡片保存、撤销。

### 2. 拆分 ConversationService

目标文件：

- `src/main/java/com/nonu1l/media/service/ConversationService.java`

问题：

- 当前混合会话生命周期、Agent 调用、卡片持久化、撤销、回复文本生成、VO 转换、历史上下文构造。

建议方向：

- 先抽低风险纯逻辑：
  - `ReplyTextGenerator`
  - `ConversationCardMapper`
  - `ConversationHistoryBuilder`
- 再考虑卡片保存流程独立成 `ConversationCardService`。

风险点：

- AI 会话状态和卡片状态容易回归。
- `TokenUsageAdvisor` 的 session / turn 上下文不能断。

验证：

- AI 发送消息。
- 生成卡片。
- 保存卡片。
- 撤销卡片保存。
- 重置会话。

### 3. 前端 WorkDetailModal 大拆

目标文件：

- `frontend/src/components/WorkDetailModal.tsx`

问题：

- 详情加载、角色名加载、标记、评分、删除、布局渲染都在一个组件中。

建议方向：

- 先抽 hooks：
  - `useWorkDetail`
  - `useCastNames`
- 再抽展示组件：
  - `WorkMetaSection`
  - `CastStrip`
  - `MarkEditor`

风险点：

- 弹窗关闭时的 `changed` 回调必须保持。
- 快速打开/关闭弹窗不能 setState 到卸载组件。

验证：

- 打开详情。
- 加载角色/演员信息。
- 修改状态、评分、评价。
- 删除标记后列表刷新。

### 4. 前端首页数据模型重构

目标文件：

- `frontend/src/pages/HomePage.tsx`
- `frontend/src/components/WorkCard.tsx`

问题：

- 当前用 `WorkListItem | WorkSearchResult` 加 `unmarked` 布尔值区分。
- 组件内部依赖类型断言，后续容易误用。

建议方向：

- 新增 `WorkCardViewModel`，在页面层转换。
- 或拆成 `MarkedWorkCard` / `SearchResultCard`。

风险点：

- 涉及首页列表、搜索结果、快捷标记、详情打开。
- 必须避免 UI 行为变化。

验证：

- 默认列表。
- 搜索结果。
- 已标记结果与未标记结果混合显示。
- 标记后刷新。

### 5. 设置保存接口事务化

目标区域：

- `SettingsPage.saveAiConfig`
- `SettingsController`
- `SettingsService`

问题：

- 前端保存 AI 设置时先调用 `updateAiProfile`，再调用 `updateSettings`。
- 第二步失败时可能出现部分成功。

建议方向：

- 后端新增一个事务式保存 AI 设置接口，同时保存：
  - `ai.enabled`
  - `ai.token-usage.enabled`
  - `ai.base-url`
  - `ai.api-key`
  - `ai.model`
  - `ai.temperature`
- 前端改为一次请求。

风险点：

- 改接口契约，前后端都要同步。
- 要保持保存后立即生效。

验证：

- AI 开关。
- Token 用量开关。
- Base URL / Model / API Key / Temperature 保存。
- 保存失败时 UI 状态一致。

## 推荐执行顺序

1. 低风险清理：删除弃用 DTO、删除注释接口、删除未调用方法。
2. 低风险合并：合并 Web MVC 配置类。
3. 中风险一致性：统一 Jackson 注解包。
4. 中风险后端整理：抽 `SettingsTestService` 和 `AiProviderSupport`。
5. 低到中风险前端整理：状态元数据统一、清理未使用组件/依赖。
6. 中风险前端拆分：拆 `SettingsPage` 的纯 UI 控件。
7. 高风险拆分：`WorkService`、`ConversationService`、`WorkDetailModal`、首页数据模型。
8. 最后处理设置保存接口事务化。

## 每轮最小验证清单

后端相关改动后，用户侧至少验证：

- 应用可启动。
- `/settings` 可读取和保存。
- AI 测试可返回成功或友好错误。
- 搜索、详情、标记、取消标记正常。
- AI 对话和卡片保存正常。

前端相关改动后，Agent 至少执行：

- `cd frontend && npm run build`

用户侧至少验证：

- 首页列表和搜索。
- 详情弹窗。
- AI 页面。
- 设置页保存、测试、toast。

