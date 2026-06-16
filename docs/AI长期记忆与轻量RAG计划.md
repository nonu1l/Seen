# AI 长期记忆与轻量 RAG 计划

## 目标

为 Seen 增加一套面向个人影视偏好的长期记忆能力，让 AI 在推荐、分析、搜索时能理解用户长期喜欢什么、讨厌什么、近期偏好如何变化。

本计划选择“长期记忆为主，轻量 RAG 为辅”的技术路线：

- 长期记忆负责沉淀用户偏好、偏好变化和推荐约束。
- 轻量 RAG 负责从本地已标记作品中召回语义相似的作品或历史评价。
- `work`、`record` 仍然是事实来源；AI 生成的记忆属于可重建的派生数据。

## 技术选择

### 首选方案

- 存储：继续使用 SQLite + JPA。
- 记忆更新：使用 Spring Service 事件式更新，不引入消息队列。
- 记忆生成：使用现有 Spring AI `ChatClient`，按当前设置页中的模型配置调用。
- 记忆注入：在 Agent 推荐、分析、搜索节点中注入一段短的用户偏好上下文。
- 轻量召回：第二阶段再做，本地 SQLite 存 embedding，Java 侧计算 cosine 相似度。

### 不优先选择

- 不优先引入独立向量数据库。
- 不优先使用 Spring AI 官方 Chat Memory 保存长期偏好。
- 不把本地历史全部塞进 prompt。
- 不让 AI 直接修改 `work`、`record` 事实表。

原因：

- 当前是个人本地部署，SQLite 足够承载偏好记忆和少量 embedding。
- 长期偏好是“用户画像”，不是普通聊天历史，直接用 Chat Memory 容易混入临时对话。
- 本地影视记录有明确结构，先用 SQL 聚合 + LLM 总结比直接向量化更稳定。
- 独立向量库会增加 Docker 部署和备份复杂度。

## 数据设计

### 第一阶段：长期偏好记忆

新增 `user_preference_memory` 表，用于保存当前长期画像：

- `id`：固定单用户记录。
- `version`：记忆版本号，每次更新递增。
- `summary`：给 AI 使用的短文本画像。
- `likes_json`：喜欢的题材、标签、风格、国家、年代、节奏等。
- `dislikes_json`：低分、弃坑、负面短评中提炼出的避雷点。
- `recent_shift_json`：近期偏好变化。
- `recommendation_rules_json`：推荐时应遵守的规则。
- `source_hash`：参与本轮总结的数据指纹。
- `updated_at`：最后更新时间。

新增 `user_preference_evidence` 表，用于保存可解释证据：

- `id`
- `work_id`
- `record_id`
- `evidence_type`：`high_rating`、`low_rating`、`review`、`status`、`tag`
- `weight`：证据权重。
- `text`：提炼后的证据文本。
- `created_at`

原则：

- `work`、`record` 是事实表。
- `user_preference_memory` 是当前画像快照。
- `user_preference_evidence` 是画像依据。
- 画像可以删除后从 `work`、`record` 重建。

### 第二阶段：轻量 RAG 索引

新增 `local_memory_embedding` 表：

- `id`
- `source_type`：`work`、`record_review`、`preference_evidence`
- `source_id`
- `text`
- `embedding_model`
- `embedding_json`
- `content_hash`
- `updated_at`

实现策略：

- 使用 Spring AI `EmbeddingModel` 生成向量。
- 向量先以 JSON 数组存 SQLite，避免引入 native SQLite 扩展。
- 查询时加载候选向量，在 Java 中计算 cosine 相似度，返回 topK。
- 本地数据超过一万条后，再评估 `pgvector` 或 `sqlite-vec`。

## 核心流程

### 记忆生成

触发时机：

- 用户新增标记。
- 用户修改评分。
- 用户写入或修改影评。
- 用户取消标记。
- 手动点击“重建 AI 记忆”。

更新方式：

1. 从 `work` + 最新 `record` 聚合数据。
2. 优先提取高价值记录：
   - 评分 >= 8 的作品。
   - 评分 <= 5 的作品。
   - 有影评的作品。
   - 最近 30 条记录。
   - 状态为 `dropped` 或 `wish` 的作品。
3. 生成结构化统计：
   - 高分标签。
   - 低分标签。
   - 常看类型。
   - 最近偏好。
   - 用户影评关键词。
4. 调用 LLM 生成偏好画像 JSON。
5. 保存到 `user_preference_memory`。

失败策略：

- LLM 更新失败时保留旧画像。
- 失败只记录日志，不影响标记和搜索主流程。
- 没有足够记录时生成空画像，不强行猜测用户偏好。

### Agent 接入

新增 `AiPreferenceMemoryService`：

- `getMemoryContext()`：返回适合放进 prompt 的短画像。
- `rebuildMemory()`：从本地记录重建画像。
- `recordChanged(workId)`：标记变化后触发延迟更新。

接入位置：

- `recommend` 节点：必须注入长期偏好。
- `analyze` 节点：注入长期偏好，用于回答“我喜欢什么类型”。
- `search` 节点：只注入简短偏好，不改变用户明确搜索意图。
- `mark` / `unmark` 节点：不注入偏好，避免影响事实操作。

推荐时 prompt 增加类似内容：

```text
用户长期偏好：
{memoryContext}

请优先结合用户当前请求，不要因为长期偏好覆盖用户明确条件。
```

### 轻量 RAG 接入

第二阶段新增 `AiLocalMemoryRetriever`：

- 输入用户当前请求。
- 从 `local_memory_embedding` 中召回相似作品、短评和偏好证据。
- 返回最多 5 条结果。

推荐节点最终上下文：

```text
长期偏好画像：用于理解用户。
本地相似记录：用于找相似历史作品。
当前请求：最高优先级。
```

优先级：

1. 用户当前明确条件。
2. 本地负面偏好和避雷点。
3. 本地高分偏好。
4. Web / Bangumi 搜索结果。

## 设置与后台能力

第一阶段增加后台接口，不急于做复杂 UI：

- `POST /api/admin/ai-memory/rebuild`：重建长期记忆。
- `GET /api/admin/ai-memory`：查看当前画像和更新时间。

后续设置页可增加：

- AI 长期记忆开关。
- 自动更新开关。
- 手动重建按钮。
- 查看当前偏好画像。

默认值：

- 长期记忆默认开启。
- 自动更新默认开启。
- 轻量 RAG 默认关闭，等 embedding 配置稳定后再开放。

## 实施阶段

### 第一阶段：长期记忆

- 新增长期记忆实体、仓储和服务。
- 从 `work`、`record` 聚合偏好数据。
- 使用 ChatClient 生成偏好画像。
- 在推荐和分析节点注入画像。
- 提供后台重建和查看接口。

验收标准：

- 新用户没有记录时，AI 不虚构偏好。
- 用户有高分记录后，推荐能体现高分标签和影评倾向。
- 用户有低分或弃坑记录后，推荐能主动避开明显雷区。
- 标记、评分、影评修改后，画像可以被重建。

### 第二阶段：轻量 RAG

- 增加 embedding 表。
- 增加本地文本构建器，将作品元数据、评分、影评拼成索引文本。
- 增加本地向量写入和更新逻辑。
- 增加 Java cosine topK 检索。
- 在推荐节点注入相似历史记录。

验收标准：

- “推荐类似我给高分的某类作品”能召回本地相似记录。
- 修改影评后，索引能更新。
- embedding 不可用时，推荐功能仍能退回长期记忆方案。

### 第三阶段：可视化与自管理

- 设置页展示当前长期偏好。
- 支持用户手动删除或重建画像。
- 支持展示“为什么推荐这个作品”。
- 支持定期压缩旧证据，避免记忆膨胀。

## 测试计划

- 单元测试：
  - 偏好数据聚合。
  - 画像 JSON 解析。
  - 空记录、低记录数、高低评分混合场景。
  - embedding cosine 排序。

- 集成测试：
  - 标记作品后触发记忆重建。
  - 推荐节点能读取记忆上下文。
  - LLM 调用失败时不影响保存记录。
  - 关闭 AI 助手时不触发记忆生成。

- 手动验证：
  - 标记 5 部高分悬疑作品后，让 AI 推荐周末想看的片。
  - 标记几部低分异世界作品后，让 AI 推荐动画，观察是否主动避开。
  - 写入偏情绪化影评后，询问“我偏好什么类型的作品”。

## 风险与约束

- 长期记忆可能过度概括，需要保留证据并支持重建。
- 用户当前请求优先级必须高于长期偏好。
- 画像更新不应阻塞标记保存流程。
- embedding 模型不一定与聊天模型来自同一服务，第二阶段必须允许关闭。
- 本项目是单用户本地系统，暂不设计多用户隔离。

