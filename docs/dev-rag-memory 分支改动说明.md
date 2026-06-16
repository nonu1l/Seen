# AI 长期记忆架构

## 概览

当前分支完成了 AI 长期偏好记忆的第一阶段：只沉淀用户影视偏好画像，不实现 embedding、向量检索或轻量 RAG。

长期记忆是一份可删除、可重建的派生画像。`work` 和 `record` 仍然是事实来源；画像只用于帮助 Agent 理解用户长期喜欢什么、讨厌什么、近期偏好如何变化，以及推荐时应遵守哪些软约束。

本分支的主要改动：

- 新增长期记忆实体、仓储、后台接口和服务。
- 从本地作品与最新记录中提取证据，调用当前 AI 配置生成 JSON 画像。
- 在推荐、搜索、分析 Agent 节点注入长期偏好上下文。
- 在记录变更后延迟触发重建，避免每次标记都同步调用 LLM。
- 在设置页 `AI 助手` 分组中加入 `长期记忆` 小节，支持开关、查看画像和手动重建。
- 增加长期记忆聚合逻辑的单元测试。

## 总体架构

```
用户标记 / 评分 / 影评 / 取消标记
        │
        ▼
┌──────────────┐
│  WorkService │
└──────┬───────┘
       │ recordChanged(workId)
       ▼
┌───────────────────────────┐
│ AiPreferenceMemoryService │
│  - 延迟防抖重建            │
│  - 聚合 work + record      │
│  - 生成 evidence           │
│  - 调用 ChatClient         │
└──────┬────────────────────┘
       │
       ├─ 写入 user_preference_evidence
       └─ 写入 user_preference_memory
                       │
                       ▼
              ┌────────────────┐
              │ AgentService   │
              └──────┬─────────┘
                     │
     ┌───────────────┼────────────────┐
     ▼               ▼                ▼
 recommend        search           analyze
 长画像注入       简短提醒注入      system prompt 注入
```

## 数据模型

```
┌────────────┐       最新记录        ┌─────────────┐
│    work    │ ───────────────────→ │   record    │
└─────┬──────┘                       └──────┬──────┘
      │                                     │
      └──────────────┬──────────────────────┘
                     ▼
          ┌──────────────────────┐
          │ preference evidence  │
          │ high_rating          │
          │ low_rating           │
          │ review               │
          │ status               │
          │ recent               │
          └──────────┬───────────┘
                     ▼
          ┌──────────────────────┐
          │ preference memory    │
          │ summary              │
          │ likes_json           │
          │ dislikes_json        │
          │ recent_shift_json    │
          │ recommendation_rules │
          │ source_hash          │
          └──────────────────────┘
```

新增表：

| 表 | 作用 |
|---|---|
| `user_preference_memory` | 固定单用户的当前画像快照，包含版本、摘要、偏好 JSON、sourceHash 和更新时间。 |
| `user_preference_evidence` | 每次画像重建时生成的证据列表，保留作品、记录、证据类型、权重和证据文本。 |

画像字段：

| 字段 | 含义 |
|---|---|
| `summary` | 给 Agent 使用的短画像摘要。 |
| `likes_json` | 正向偏好，例如题材、风格、标签、国家、节奏等。 |
| `dislikes_json` | 负向偏好和避雷点。 |
| `recent_shift_json` | 最近 30 条记录体现的偏好变化。 |
| `recommendation_rules_json` | 推荐时可参考的软规则。 |
| `source_hash` | 本轮参与总结的数据指纹，用于数据未变化时跳过 LLM 调用。 |

## 画像重建流程

```
rebuildMemory()
      │
      ▼
读取所有 work，按最近记录排序
      │
      ▼
批量读取每个 work 的最新 record
      │
      ▼
读取最近 30 条 record
      │
      ▼
筛选高价值证据
  ├─ 评分 >= 8
  ├─ 评分 <= 5
  ├─ 有影评
  ├─ 状态 dropped / wish
  └─ 最近 30 条记录
      │
      ▼
按权重排序，最多取 80 条 evidence
      │
      ▼
计算 sourceHash
      │
      ├─ sourceHash 未变化 → 跳过 LLM，返回旧画像
      │
      └─ sourceHash 变化
             │
             ▼
      preference-memory.st
             │
             ▼
      ChatClient 生成 JSON
             │
             ▼
      解析 summary / likes / dislikes / recentShift / recommendationRules
             │
             ▼
      保存 memory，替换 evidence
```

失败策略：

- 长期记忆关闭时，`rebuildMemory()` 返回当前已有画像，不生成新画像。
- 没有证据时生成空画像，不虚构偏好。
- LLM 调用失败或 JSON 解析失败时保留旧画像，只记录日志。
- 记录变更触发的是后台延迟任务，不阻塞标记、取消标记、评分或影评保存。

## Agent 接入

```
用户输入
    │
    ▼
┌──────────┐
│ classify │
└────┬─────┘
     │
     ├─ mark / unmark ─→ 不注入长期记忆，避免影响事实操作
     │
     ├─ recommend ─────→ 注入完整长期画像，再进入 SearchPipeline
     │
     ├─ search ────────→ 注入简短偏好提醒，再进入 SearchPipeline
     │
     └─ analyze ───────→ 在 system prompt 中注入长期画像
```

推荐和搜索输入会被包装为：

```text
长期偏好画像（仅作为辅助，不得覆盖当前请求）：
{memoryContext}

当前请求（最高优先级）：
{userInput}
```

设计原则：

- 当前请求永远优先于长期偏好。
- `recommend` 可以使用完整画像，帮助 SearchPipeline 生成更贴合用户的搜索语义。
- `search` 只使用 400 字以内的简短提醒，不改变用户明确搜索条件。
- `analyze` 使用画像回答“我喜欢什么类型”“最近偏好有什么变化”等问题。
- `mark` 和 `unmark` 不使用画像，避免把偏好推断带入事实写入。

## SearchPipeline 中的位置

长期记忆不改变 SearchPipeline 的内部阶段，只改变进入管道的输入上下文。

```
用户当前请求
  │
  ├─ recommend：拼入完整长期画像
  ├─ search：拼入简短偏好提醒
  └─ mark / unmark：不拼入画像
        │
        ▼
SearchPipeline
  │
  ├─ 1. generateKeywords
  ├─ 2. searchWeb
  ├─ 3. fetchWeb + clean
  ├─ 4. extractTitles
  ├─ 5. searchBangumi
  ├─ 6. 去重
  ├─ 7. validateMatches
  └─ 8. 聚合结果 / 失败原因
```

这样做的好处是：长期记忆可以影响关键词生成和结果校验时的语义理解，但不会绕过现有 Web / Bangumi 搜索、去重和校验逻辑。

## 设置页与后台接口

```
设置页 AI 助手
      │
      ▼
┌──────────────┐
│ 长期记忆小节 │
└──────┬───────┘
       │
       ├─ AI 长期记忆开关
       │     └─ 保存到 ai.memory.enabled
       │
       ├─ 画像预览
       │     ├─ 版本
       │     ├─ 更新时间
       │     └─ summary
       │
       ├─ 查看偏好细节
       │     ├─ likes
       │     ├─ dislikes
       │     ├─ recentShift
       │     └─ recommendationRules
       │
       └─ 重建长期记忆
             └─ POST /api/admin/ai-memory/rebuild
```

后台接口：

| 方法 | 路径 | 作用 |
|---|---|---|
| `GET` | `/api/admin/ai-memory` | 返回当前画像、版本和更新时间；无画像时 `exists=false`。 |
| `POST` | `/api/admin/ai-memory/rebuild` | 手动重建画像并返回最新结果；失败时保留旧画像。 |

设置语义：

- 用户只看到 `AI 长期记忆` 一个开关。
- 开启后，自动更新始终跟随长期记忆启用状态。
- 关闭后，不注入画像，不触发自动重建。
- 旧的 `ai.memory.auto-update.enabled` 可作为兼容设置存在，但当前行为不依赖它，前端也不展示它。

## 当前分支改动清单

后端：

- `AiPreferenceMemoryService`：长期记忆核心服务，负责上下文读取、证据聚合、LLM 生成、sourceHash 跳过、延迟重建。
- `AiMemoryController`：提供查看和手动重建接口。
- `UserPreferenceMemory` / `UserPreferenceEvidence`：新增画像与证据实体。
- `UserPreferenceMemoryRepository` / `UserPreferenceEvidenceRepository`：新增 JPA 仓储。
- `RecordRepository`：新增批量读取最新记录和最近 30 条记录的查询。
- `WorkService`：在标记、取消标记、撤销记录、更新评分/影评成功后触发 `recordChanged(workId)`。
- `AgentService`：在 recommend、search、analyze 节点接入长期画像。
- `SettingsService` / `SettingsResponse`：新增长期记忆设置返回与默认定义。
- `preference-memory.st`：新增长期记忆 JSON 生成 prompt。
- `agent-analyze.st`：分析节点 system prompt 增加长期偏好上下文。

前端：

- `SettingsPage`：新增长期记忆小节、画像预览、手动重建、偏好细节折叠区。
- `api.client` / `api.types`：新增长期记忆接口类型与请求方法。
- `ToggleRow`：支持说明文字。
- `index.css`：新增设置页长期记忆区域的样式和移动端适配。

测试：

- `AiPreferenceMemoryServiceTest`：覆盖空画像、证据聚合和 sourceHash 稳定性。

## 已完成能力

- 固定单用户长期偏好画像。
- 从高分、低分、影评、状态和近期记录生成证据。
- 使用现有 Spring AI `ChatClient` 生成 JSON 画像。
- 画像字段固定为 `summary`、`likes`、`dislikes`、`recentShift`、`recommendationRules`。
- 数据未变化时通过 `sourceHash` 跳过 LLM 重建。
- LLM 失败时保留旧画像。
- 记录变更后后台延迟重建。
- 推荐、搜索、分析三个 Agent 路径接入画像。
- 设置页支持开关、查看和手动重建。

## 未完成与后续建议

第一阶段尚可补强：

- 增加接口级集成测试，覆盖 `GET /api/admin/ai-memory`、`POST /api/admin/ai-memory/rebuild` 和设置开关持久化。
- 增加 Agent 行为测试，确认关闭长期记忆后 `recommend`、`search`、`analyze` 不再注入画像。
- 给后台延迟重建增加更可观测的日志或状态，例如最近一次重建结果、失败原因和下一次计划时间。
- 明确画像证据的生命周期策略。目前重建时整体替换证据，后续可以增加证据压缩或保留最近 N 次版本。
- 增加画像删除能力，方便用户彻底清空派生画像；当前只支持关闭和重建。
- 管理接口目前位于 `/api/admin`，后续如开放远程部署，应评估鉴权或只允许本机访问。

第二阶段轻量 RAG 尚未实现：

- 未新增 embedding 表。
- 未接入 Spring AI `EmbeddingModel`。
- 未构建本地作品、影评、证据文本的向量索引。
- 未实现 Java 侧 cosine topK 检索。
- 未在推荐节点注入“本地相似记录”。
- 未实现推荐解释 UI。

建议第二阶段仍保持轻量路线：

```
work / record / evidence 文本
        │
        ▼
EmbeddingModel
        │
        ▼
local_memory_embedding
        │
        ▼
AiLocalMemoryRetriever
        │
        ▼
推荐节点注入 topK 本地相似记录
```

推荐优先级建议保持为：

1. 用户当前明确条件。
2. 本地负面偏好和避雷点。
3. 本地高分偏好。
4. 本地相似记录。
5. Web / Bangumi 搜索结果。

## 风险与约束

- 长期画像可能过度概括，因此必须始终保留“当前请求优先”的 prompt 约束。
- 画像是派生数据，不应成为事实来源，也不应回写 `work` 或 `record`。
- 自动重建依赖 AI 配置完整性，AI 不可用时应静默跳过，不影响记录功能。
- 当前系统按单用户本地部署设计，暂未设计多用户隔离字段。
- 轻量 RAG 会引入 embedding 成本和模型兼容问题，应默认可关闭，并保证不可用时回退到长期画像方案。
