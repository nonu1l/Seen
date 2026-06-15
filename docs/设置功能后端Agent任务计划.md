# 设置功能后端 Agent 任务计划

本文件是给后端执行 agent 的任务指令。目标是实现设置功能第一阶段的后端能力，并保证所有设置保存后立即生效。

## 任务边界

负责范围：

- `src/main/java/com/nonu1l/media/model/entity`
- `src/main/java/com/nonu1l/media/model/dto`
- `src/main/java/com/nonu1l/media/repository`
- `src/main/java/com/nonu1l/media/service`
- `src/main/java/com/nonu1l/media/controller`
- `src/main/java/com/nonu1l/media/config`
- `src/main/resources/data.sql`，仅在需要初始化字典时修改

不要修改：

- `frontend/**`
- `docs/**`
- `pom.xml`，除非实现必须新增依赖；如确实需要，先说明原因

硬性规则：

- 不编译 Java。
- 不运行 Maven 编译命令。
- 不回滚他人的未提交改动。
- 所有设置必须保存后立即影响后续请求，不允许设计为重启后生效。

## 核心目标

新增设置后端能力：

- 读取设置：`GET /api/settings`
- 保存设置：`PUT /api/settings`
- 测试 AI：`POST /api/settings/test-ai`
- 测试搜索：`POST /api/settings/test-search`
- 测试 Bangumi：`POST /api/settings/test-bangumi`

并改造运行时读取：

- AI 开关保存即生效
- token 用量记录开关保存即生效
- 搜索源保存即生效
- Serper API Key 保存即生效
- Bangumi 代理保存即生效
- 角色 / 演员展示开关保存即生效

AI 的 `base-url`、`api-key`、`model`、`temperature` 也需要保存即生效。若当前 Spring AI 注入方式不方便大改，至少要为测试接口和下一步动态 ChatClient 改造留下清晰服务边界；不要保留“重启后生效”的接口语义。

## 设置 Key

必须支持：

```text
seen.ai.enabled
seen.ai.token-usage-enabled
spring.ai.openai.base-url
spring.ai.openai.api-key
spring.ai.openai.chat.options.model
spring.ai.openai.chat.options.temperature
seen.search.provider
seen.search.serper-api-key
seen.bangumi-proxy
seen.detail.cast-enabled
```

默认值来源：

```text
数据库设置 > Spring Environment(application.yml / 环境变量) > 代码默认值
```

## 数据模型

新增实体建议：

```text
AppSetting
- id
- settingKey
- settingValue
- valueType
- sensitive
- updatedAt
```

约束：

- `settingKey` 唯一。
- API Key 类设置 `sensitive = true`。
- 返回给前端时敏感值默认脱敏。
- 敏感字段保存空字符串时不覆盖旧值。

## DTO 契约

`GET /api/settings` 返回结构建议：

```json
{
  "groups": [
    {
      "key": "ai",
      "label": "AI 助手",
      "settings": [
        {
          "key": "seen.ai.enabled",
          "label": "AI 助手",
          "value": true,
          "type": "boolean",
          "sensitive": false,
          "effectiveSource": "database"
        }
      ]
    }
  ],
  "applied": true
}
```

`PUT /api/settings` 请求结构建议：

```json
{
  "settings": {
    "seen.ai.enabled": true,
    "seen.search.provider": "serper"
  }
}
```

保存返回最新 `SettingsResponse`，并包含 `applied: true` 或同等语义字段。

测试接口返回结构建议：

```json
{
  "ok": true,
  "message": "连接正常",
  "elapsedMs": 123,
  "details": {}
}
```

失败时：

```json
{
  "ok": false,
  "message": "错误摘要，不包含 API Key",
  "elapsedMs": 123,
  "details": {}
}
```

## 服务设计

新增 `SettingsService`：

- 负责 key 白名单、默认值、类型转换、敏感值脱敏。
- 负责保存设置。
- 负责保存后刷新内存快照。
- 提供 `getBoolean(key)`、`getString(key)`、`getDouble(key)` 等读取方法。
- `@Value` 或 `Environment` 只作为默认值来源，不作为运行期唯一值。

建议内部维护不可变快照：

```text
Map<String, SettingValue> currentSettings
```

保存设置后同步刷新快照，保证后续请求立即生效。

## 需要改造的现有类

### AppConfigController

- 改为从 `SettingsService` 读取 `seen.ai.enabled`。

### AiFeatureGuard

- 改为每次请求从 `SettingsService` 读取 `seen.ai.enabled`。

### TokenUsageAdvisor

- 增加 `seen.ai.token-usage-enabled` 判断。
- 关闭时跳过 token_usage 保存。

### WebSearchService

- 不要在构造函数固定 delegate。
- 每次 `search` / `fetch` 时根据 `SettingsService` 当前 `seen.search.provider` 选择 `SerperSearchService` 或 `DDGSearchService`。
- 如果选择 `serper` 但没有 API Key，应回退 DDG 或返回清晰错误；优先保留当前行为：回退 DDG。

### SerperSearchService

- 每次请求从 `SettingsService` 读取 `seen.search.serper-api-key`。
- `isAvailable()` 使用当前设置判断。
- 日志禁止输出 API Key。

### DDGSearchService

- 每次请求从 `SettingsService` 读取 `seen.bangumi-proxy` 并生成搜索 URL。
- 不要在构造函数固定 `searchUrl`。

### BangumiService

- 每次请求前从 `SettingsService` 获取 `seen.bangumi-proxy` 并生成 base URL。
- `seen.detail.cast-enabled` 每次详情请求时读取。
- 不要在构造函数固定 base。

### PreCacheService

- 每次预缓存请求从 `SettingsService` 获取 `seen.bangumi-proxy` 并生成 base URL。
- 不要在构造函数固定 base。

## 测试接口实现细节

### test-ai

- 使用请求体中的临时 baseUrl、apiKey、model、temperature。
- 不依赖当前保存值。
- 发送极短 prompt。
- 返回 ok、耗时、错误摘要。
- 不打印或返回 API Key。

### test-search

- 使用请求体中的 provider、serperApiKey、bangumiProxy。
- 查询固定关键词 `孤独摇滚` 或请求体传入的 `query`。
- 返回命中数量和前几条标题。

### test-bangumi

- 使用请求体中的 bangumiProxy。
- 请求 `/api/subjects/1` 或等价轻量接口。
- 返回 ok、耗时、错误摘要。

## 验收标准

- `GET /api/settings` 能返回 AI 和搜索数据源两组设置。
- `PUT /api/settings` 保存后，后续请求立即使用新值。
- 关闭 AI 后，`/api/app-config` 返回 `aiEnabled=false`，AI 会话接口被拦截。
- 切换搜索源后，下一次搜索使用新 provider。
- 修改 Bangumi 代理后，下一次 Bangumi 请求使用新代理。
- API Key 不在读取接口、日志、错误信息中明文泄露。
- 不编译 Java。
