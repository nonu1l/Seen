# OpenAI 格式迁移到 Anthropic 格式重构方案

## 背景

当前项目的 AI 配置不是从 `application.yml` 读取，而是由前端设置页保存到 `app_setting` 表：

- `ai.base-url`
- `ai.api-key`
- `ai.model`
- `ai.temperature`
- `ai.thinking-mode`

后端通过 `SettingsService.currentRuntimeSetting()` 读取运行时配置，再由 `AiChatClientFactory` 创建 Spring AI `OpenAiChatModel`。因此本次迁移必须继续保持“前端配置 API 地址、Key、模型”的使用方式，不能改回配置文件驱动。

本方案目标是把当前 OpenAI-compatible Chat Completions 主链路迁移为 Anthropic-compatible Messages 主链路。MiniMax / DeepSeek 是否完全兼容 Anthropic API 由实际配置测试决定；本方案不为各家模型额外写私有 API 适配逻辑。

## 当前架构理解

### 设置链路

前端设置页维护 AI 配置：

- `frontend/src/pages/SettingsPage.tsx`
- `frontend/src/api/types.ts`
- `frontend/src/api/client.ts`

保存接口进入后端：

- `SettingsController.updateAiProfile`
- `SettingsService.updateAiProviderSetting`
- `app_setting` 表持久化

运行时读取：

- `SettingsService.currentRuntimeSetting()`
- `SettingsService.runtimeFromDraft()`

当前设置页没有 provider/protocol 字段，只有 Base URL、模型、API Key、Temperature、思考模式。

### LLM Client 链路

当前统一由 `AiChatClientFactory` 创建 Spring AI OpenAI client：

```text
SettingsService
  -> AiChatClientFactory.currentClient(...)
  -> OpenAiChatOptions(baseUrl, apiKey, model, temperature, extraBody)
  -> OpenAiChatModel
  -> ChatClient
```

`thinking` 目录目前负责：

- 通过 Base URL 判断 provider。
- 给 OpenAI-compatible 请求注入各家 `thinking` 扩展字段。
- 清理 MiniMax `<think>...</think>` 正文。
- 给 token 记录提供 provider 展示名。

迁移到 Anthropic-compatible 后，这套 provider thinking 适配不再需要，应删除 `com/nonu1l/media/service/thinking`。

### Agent 主链路

AI 页面发送消息：

```text
ConversationController.sendStream
  -> ConversationService.sendMessageStream
  -> 保存 user message
  -> 预创建 assistant message
  -> 设置 TokenUsageAdvisor 上下文
  -> 设置 AiToolExecutionContext
  -> AutonomousAgentService.invoke
  -> 保存最终 assistant message
  -> 推送 assistant_saved / cards / done
```

`AutonomousAgentService` 当前调用：

```java
chatClient().prompt()
    .system(system)
    .user(userInput)
    .toolCallbacks(toolRegistry.callbacks())
    .call()
    .content();
```

工具循环由 Spring AI OpenAI ToolCalling 内部维护，项目本身没有保存工具调用中的 assistant 原始消息，也没有保存 thinking/tool_use/tool_result block。

### 工具注册与副作用

`AiToolRegistry` 当前注册工具：

- `searchBangumi`
- `searchLocal`
- `getWorkState`
- `findWorks`
- `presentWorks`
- `markWork`
- `unmarkWork`
- `readUserMemory`
- `searchWatchSources`
- `searchWeb`，仅搜索源启用时暴露
- `fetchWeb`

写库工具依赖 `AiToolContextHolder.require()` 获取：

- `sessionId`
- `requestId`
- `userMessageId`
- `assistantMessageId`
- `userInput`
- `listener`

这条上下文链路必须保留，否则卡片、撤销、快照、token 归属会失效。

### 文本型 LLM 任务

`AiTextTaskService` 封装不带工具的文本分析任务，用于标题提取、页面验证等场景。它当前仍通过 `AiChatClientFactory.currentClient(effectiveMode)` 调用 OpenAI 格式。

迁移后应保持同样的链式调用能力，但底层改为 Anthropic client；文本任务不需要暴露工具。

### Token 记录

当前 `TokenUsageAdvisor` 是 Spring AI `CallAdvisor/StreamAdvisor`，从 Spring AI metadata 读取 token usage，并记录：

- sessionId
- requestId
- nodeName
- turn
- profileName
- modelName
- prompt/completion/total tokens
- native cached tokens
- inputText
- outputText

如果继续使用 Spring AI Anthropic ChatModel，需要确认 Anthropic metadata 能被 advisor 正常读取。若改成官方 SDK 或 raw HTTP，则需要新增 Anthropic token usage 记录器，不能再依赖 `TokenUsageAdvisor` 自动拦截。

## 迁移目标

### 必须保持

- 前端设置页配置 AI Base URL、API Key、模型、Temperature。
- AI 页面发送、停止、单例锁、状态流正常。
- Agent 工具名称和业务语义保持不变。
- 卡片展示、保存、撤销、取消标记撤回正常。
- 搜索源配置逻辑正常，未启用时不暴露 `searchWeb`。
- 文本型 LLM 任务正常。
- Token 记录尽量保持可用。

### 可以打破

- 不考虑旧数据库兼容，表可以重建。
- 不保留 OpenAI-compatible thinking 适配。
- 不保留 `service/thinking` 目录。
- 不需要为 MiniMax / DeepSeek 写单独私有 API 分支。
- 不需要兼容历史会话消息中的旧格式。

## 推荐技术路线

优先使用 `spring-ai-starter-model-anthropic`，因为当前项目已经基于 Spring AI：

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-anthropic</artifactId>
</dependency>
```

迁移后仍使用 Spring AI `ChatClient`，但模型从 `OpenAiChatModel` 换成 `AnthropicChatModel`。

选择该路线的原因：

- 改动比 raw HTTP / 官方 SDK 小。
- 现有 `toolCallbacks` 理论上可以继续复用。
- 现有 `TokenUsageAdvisor` 有机会继续复用。
- 前端配置 Base URL 的模式可以保留。

风险：

- 需要验证 Spring AI Anthropic 的工具循环是否完整保留 `thinking` block。
- 需要验证 MiniMax / DeepSeek Anthropic-compatible endpoint 与 Spring AI Anthropic request/response 细节是否一致。
- 若不兼容，再切换到官方 SDK 或 raw HTTP。

## 数据结构调整

### ConversationMessage

当前只有纯文本：

```text
role
content
```

Anthropic 格式下建议改为：

```text
role
content
content_blocks
```

字段建议：

- `content`：给前端直接展示的最终文本，保留为兼容普通 UI。
- `content_blocks`：JSON，保存 Anthropic content blocks。

`content_blocks` 可包含：

```json
[
  { "type": "thinking", "thinking": "..." },
  { "type": "text", "text": "..." },
  { "type": "tool_use", "id": "...", "name": "...", "input": {} },
  { "type": "tool_result", "tool_use_id": "...", "content": "..." }
]
```

如果表允许重建，可以直接改实体和 DTO，不做迁移脚本。

### ConversationMessageDTO

新增：

```java
String contentBlocks
```

或者更明确地新增：

```java
List<AiContentBlockDTO> contentBlocks
```

建议后者，前端类型更清晰。

### TokenUsage

如果 Spring AI Anthropic 能正常提供 usage，表结构可以先不变。

如果后续需要记录 Anthropic 特有字段，可增加：

- `input_tokens`
- `output_tokens`
- `cache_creation_input_tokens`
- `cache_read_input_tokens`

本阶段不强制增加，避免扩大改动。

## 后端重构计划

### 阶段 1：引入 Anthropic ChatClient

修改 `pom.xml`：

- 移除或保留 `spring-ai-starter-model-openai` 由实施时决定。
- 新增 `spring-ai-starter-model-anthropic`。

改造 `AiChatClientFactory`：

```text
SettingsService.currentRuntimeSetting()
  -> AnthropicChatOptions(baseUrl, apiKey, model, temperature, maxTokens)
  -> AnthropicChatModel
  -> ChatClient
```

注意点：

- Base URL 仍来自前端设置页。
- 不再通过 `ThinkingStrategyRegistry.extraBody()` 注入 provider 私有参数。
- cache key 去掉 `ThinkingMode` 和 `extraBody`。
- `cleanAssistantContent()` 不再依赖 provider 策略，默认返回原文或从 block 中提取 text。

### 阶段 2：删除 thinking 目录与相关引用

删除：

- `com/nonu1l/media/service/thinking/*`
- `ThinkingDisableInterceptor`

清理引用：

- `AiConfig`
- `AiChatClientFactory`
- `SettingsService`
- `SettingsTestService`
- `AiTextTaskService`
- `WatchSourceSearchService`
- `TokenUsageAdvisor`

设置页 `thinkingMode` 可以保留，但语义改为 Anthropic thinking：

- `default`：按调用处决定。
- `enabled`：强制开启 Anthropic thinking。
- `disabled`：强制关闭 Anthropic thinking。

如果第一阶段只追求稳定，也可以先移除 thinking 开关；但用户明确需要思考模式配置，建议保留。

### 阶段 3：改造设置页 AI 测试

当前 `SettingsTestService.testAiProfile()` 手写：

```text
POST {baseUrl}/chat/completions
```

迁移后改为：

```text
POST {baseUrl}/messages
```

或者更稳妥：复用 `AiChatClientFactory` 创建临时 Anthropic client 发 `ping`。

推荐复用 client，避免测试接口和真实调用格式漂移。

### 阶段 4：Agent 工具链路验证

`AutonomousAgentService` 可以先保持 Spring AI ChatClient 调用形态：

```java
chatClient().prompt()
    .system(system)
    .user(userInput)
    .toolCallbacks(toolRegistry.callbacks())
    .call()
    .chatResponse();
```

但不要只取 `.content()`，应读取完整 response：

- 提取最终 text 作为 `content`。
- 尽量提取 Anthropic thinking/tool metadata。
- 如果 Spring AI 暴露完整 assistant message，则保存到 `content_blocks`。

如果 Spring AI 不暴露完整 block，则至少保持当前功能正常；后续再切换 raw Anthropic SDK。

### 阶段 5：文本型任务迁移

`AiTextTaskService` 保留接口不变：

```java
task()
  .node(...)
  .system(...)
  .user(...)
  .thinking(...)
  .maxAttempts(...)
  .call(...)
```

底层调用 Anthropic ChatClient。

纯文本任务的返回仍取最终 text，不把 thinking 展示到普通结果中。

### 阶段 6：前端消息展示

当前前端只渲染 Markdown：

```tsx
<MarkdownMessage content={msg.content} />
```

迁移后新增 `contentBlocks` 类型：

```ts
type AiContentBlock =
  | { type: 'text'; text: string }
  | { type: 'thinking'; thinking: string }
  | { type: 'redacted_thinking'; data?: string }
  | { type: 'tool_use'; id: string; name: string; input: unknown }
  | { type: 'tool_result'; tool_use_id: string; content: unknown };
```

渲染策略：

- 默认展示 text block 合并后的 Markdown。
- thinking block 用折叠面板展示。
- tool_use / tool_result 默认折叠，仅调试时可见。
- 如果没有 `contentBlocks`，回退到 `content`。

### 阶段 7：数据库重建

因为不考虑旧数据兼容，可以：

- 删除本地 SQLite 数据库后启动。
- 或调整 Hibernate ddl 策略后重建。

需要确保以下表能重新生成：

- `conversation_message`
- `conversation_card`
- `record`
- `ai_work_snapshot`
- `token_usage`
- `app_setting`

## Anthropic 配置方式

前端仍填写：

```text
API Base URL: https://api.minimaxi.com/anthropic
模型名称: MiniMax-M3
API Key: ...
```

或：

```text
API Base URL: https://api.deepseek.com/anthropic
模型名称: ...
API Key: ...
```

后端不判断这是 MiniMax 还是 DeepSeek，只按 Anthropic-compatible Messages 格式调用。

## 需要重点验证的问题

### 1. Spring AI Anthropic 是否保留 thinking block

验证方式：

- 设置页开启思考模式。
- 触发一次需要工具调用的 AI 对话。
- 打印第二次模型请求体。
- 检查历史 assistant message 是否包含 `thinking` block。

如果不包含，说明 Spring AI Anthropic 不足以承载 thinking + tool loop，需要改为官方 SDK 或 raw HTTP。

### 2. 工具调用是否还能正确触发

至少验证：

- `searchBangumi`
- `findWorks`
- `presentWorks`
- `markWork`
- `unmarkWork`
- `searchWatchSources`
- `fetchWeb`

### 3. 工具副作用归属是否正常

验证：

- `requestId` 写入 message/card/record。
- `assistantMessageId` 能绑定卡片。
- `stop` 后不继续最终落库。
- `undo` 能按 snapshot 恢复。

### 4. Token 记录是否正常

验证：

- 每轮 Agent 调用产生 token_usage。
- tool loop 中多次模型调用能记录到同一 `requestId`。
- provider/model 名称正常。
- 如果 Anthropic cache token 字段不能读到，先允许为空。

### 5. 设置页测试是否真实

测试 AI 连接必须走 Anthropic `/messages` 或 ChatClient，不再请求 `/chat/completions`。

## 不做的事情

- 不做 OpenAI-compatible fallback。
- 不做 MiniMax/DeepSeek 私有字段适配。
- 不保留 `service/thinking` provider 策略。
- 不兼容旧 conversation message 数据。
- 不把 `application.yml` 作为 AI 模型配置来源。

## 实施顺序建议

1. 新增 Anthropic 依赖，改造 `AiChatClientFactory`。
2. 改造 `SettingsTestService.testAiProfile()`。
3. 删除 `service/thinking` 和 `ThinkingDisableInterceptor`。
4. 修复编译引用。
5. 让 `AiTextTaskService` 和 `AutonomousAgentService` 先正常返回文本。
6. 增加 `contentBlocks` 实体/DTO/前端展示。
7. 验证 AI 页面：普通对话、工具调用、卡片、停止。
8. 验证设置页：保存配置、测试 AI、搜索源开关。
9. 验证 token usage。
10. 再决定是否需要从 Spring AI Anthropic 下沉到官方 SDK/raw HTTP。

## 验证清单

后端：

- 设置页保存 Anthropic Base URL 后，`SettingsService.currentRuntimeSetting()` 读取正确。
- `AiChatClientFactory` 不再创建 `OpenAiChatModel`。
- `SettingsTestService` 不再请求 `/chat/completions`。
- `service/thinking` 无引用。
- `AutonomousAgentService` 能触发工具。
- `AiTextTaskService` 能完成文本任务。
- `TokenUsageAdvisor` 能记录 Anthropic 调用。

前端：

- 设置页 AI 配置保存正常。
- 思考模式选项仍能保存。
- AI 页面消息正常显示。
- thinking block 折叠显示，不污染正文。
- 卡片保存/撤销正常。
- 停止按钮正常。

推荐命令：

```powershell
npm run build
D:\Environment\maven\apache-maven-3.6.3\bin\mvn.cmd -DskipTests compile
```

浏览器验证：

- 打开 `http://localhost:5173/`
- 设置页配置 Anthropic-compatible Base URL。
- 测试 AI 连接。
- AI 页面发送搜索、标记、取消标记、找片源请求。
- 刷新页面后确认历史消息和卡片仍正常。

