# 设置功能前端 Agent 任务计划

本文件是给前端执行 agent 的任务指令。目标是实现设置入口和第一阶段设置页面，对接后端设置接口。

## 任务边界

负责范围：

- `frontend/src/App.tsx`
- `frontend/src/components/AppLayout.tsx`
- `frontend/src/pages/**`
- `frontend/src/api/**`
- `frontend/src/index.css`
- 可新增前端设置相关组件到 `frontend/src/components/**`

不要修改：

- `src/main/**`
- `pom.xml`
- `docs/**`

硬性规则：

- 不编译 Java。
- 不回滚他人的未提交改动。
- 设置保存成功后，页面提示“设置已生效”，不要出现“重启后生效”。

## 核心目标

新增 `/settings` 页面，包含两个分组：

- AI 助手
- 搜索与数据源

新增入口：

- 首页 header 右侧显示设置按钮。
- AI 页 header 右侧保留退出、重置，并显示设置按钮。
- 设置页 header 右侧显示返回按钮，不重复显示设置按钮。

## 后端接口契约

### 读取设置

```http
GET /api/settings
```

返回结构：

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

### 保存设置

```http
PUT /api/settings
```

请求结构：

```json
{
  "settings": {
    "seen.ai.enabled": true,
    "seen.search.provider": "serper"
  }
}
```

保存成功后返回最新设置。

### 测试接口

```http
POST /api/settings/test-ai
POST /api/settings/test-search
POST /api/settings/test-bangumi
```

返回结构：

```json
{
  "ok": true,
  "message": "连接正常",
  "elapsedMs": 123,
  "details": {}
}
```

## API 类型

在 `frontend/src/api/types.ts` 中新增：

- `SettingType = 'string' | 'boolean' | 'number' | 'select' | 'password'`
- `SettingItem`
- `SettingGroup`
- `SettingsResponse`
- `UpdateSettingsRequest`
- `SettingsTestResult`

在 `frontend/src/api/client.ts` 中新增：

- `getSettings`
- `updateSettings`
- `testAiSettings`
- `testSearchSettings`
- `testBangumiSettings`

## 页面设计

### 路由

在 `App.tsx` 新增：

```tsx
<Route path="/settings" element={<SettingsPage />} />
```

### Header 行为

在 `AppLayout.tsx`：

- 判断 `isSettings = location.pathname === '/settings'`
- 标题：
  - 首页：`seen.`
  - AI：`seen. assistant`
  - 设置：`seen. settings`
- 首页和 AI 页展示齿轮设置按钮。
- 设置页展示返回按钮。
- 返回按钮优先 `navigate(-1)`，如果没有历史则回首页。

按钮使用现有 `btn-icon` 风格，图标可继续用内联 SVG。

### SettingsPage

新增 `frontend/src/pages/SettingsPage.tsx`。

布局：

- 桌面端：左侧分组导航，右侧设置内容。
- 移动端：顶部横向 tabs，下面显示设置内容。
- 不做营销页，不做 hero。
- 不把页面套在大卡片里；可以用分组面板、表单行、细边框。

表单状态：

- 页面加载时请求 `api.getSettings()`。
- 表单本地维护 `values`。
- 修改后进入 dirty 状态。
- 保存按钮在 dirty 时高亮。
- 离开页面前如 dirty，使用浏览器确认。
- 保存成功后显示“设置已生效”。
- 保存失败显示错误提示。

敏感字段：

- API Key 默认显示为 `********` 或空输入提示。
- 用户未输入新值时，保存请求不要覆盖该字段。
- 提供显示 / 隐藏按钮。

### AI 助手分组

字段：

- `seen.ai.enabled`：toggle
- `spring.ai.openai.base-url`：文本输入
- `spring.ai.openai.api-key`：密码输入
- `spring.ai.openai.chat.options.model`：文本输入
- `spring.ai.openai.chat.options.temperature`：数字输入或 slider，范围 `0` 到 `2`
- `seen.ai.token-usage-enabled`：toggle

操作：

- 保存
- 测试 AI

测试 AI 调用当前表单值，不要求先保存。

### 搜索与数据源分组

字段：

- `seen.search.provider`：segmented control，`serper` / `ddg`
- `seen.search.serper-api-key`：密码输入
- `seen.bangumi-proxy`：文本输入
- `seen.detail.cast-enabled`：toggle

操作：

- 保存
- 测试搜索
- 测试 Bangumi

测试接口调用当前表单值，不要求先保存。

## 样式要求

沿用当前 Seen 风格：

- 深色背景
- 细边框
- 小字号
- 克制按钮
- 不使用大面积单色渐变
- 不使用营销 hero
- 不使用卡片套卡片
- 移动端文本不能溢出按钮或表单行

建议新增 CSS：

- `.settings-shell`
- `.settings-nav`
- `.settings-panel`
- `.settings-row`
- `.settings-input`
- `.settings-toggle`
- `.settings-segmented`
- `.settings-toast`

也可以直接使用 Tailwind class 和现有 CSS 变量。

## 验收标准

- 首页可以进入 `/settings`。
- AI 页可以进入 `/settings`。
- 设置页可以返回。
- 能读取并展示 AI 助手、搜索与数据源设置。
- 修改后保存，提示“设置已生效”。
- 测试 AI、测试搜索、测试 Bangumi 按钮能调用对应接口并展示结果。
- API Key 不会在初始页面明文展示。
- 保存空 API Key 不会覆盖已有值。
- 不出现“重启后生效”文案。
- 不编译 Java。
