# Seen

a private cinema diary — 个人的影视记录工具

## Features

- 基于 [Bangumi API](https://bangumi.github.io/api/) 做元数据匹配，覆盖大多数电影、电视剧、动画、综艺
- 标记观看状态（想看 / 在看 / 看过 / 搁置 / 抛弃），支持删除与恢复
- 10 分制评分 + 文字影评
- 请求缓存与数据预热机制，搜索和详情快速响应
- 动画/真人自动区分：真人作品使用演员照片，角色按主角→配角→客串排序
- IMDb 快捷链接（支持 imdb_id 精准跳转，无则搜索跳转）

### AI 助手

- 自然语言输入，自动识别标记、取消标记、推荐、搜索、分析五类意图
- 多步搜索管道：关键词生成 → 并发网页搜索 → 页面抓取清洗 → LLM 片名提取 → Bangumi 匹配 → 校验去重
- 支持批量标记、修改评分/状态/影评、取消标记（可撤回）
- 多源搜索可切换（SearXNG / DuckDuckGo / Serper.dev）
- 对话式交互，历史会话持久化，多轮上下文理解
- 完整 Token 用量追踪与监控

## Agent 架构

```
用户输入
    │
    ▼
┌──────────┐
│ classify │ ← 识别意图：mark / unmark / recommend / search / analyze
└────┬─────┘
     │
     ├─ mark ─────────→ 全工具 LLM（搜索+匹配+评分推断+状态推断）
     ├─ unmark ───────→ 全工具 LLM（searchLocal+提取 unmarkIds）
     ├─ recommend ────→ SearchPipeline 多步搜索管道
     ├─ search ───────→ SearchPipeline（同上）
     └─ analyze ──────→ 轻量 LLM 直接问答
                         │
                         ▼
                   ┌──────────┐
                   │  output  │ → 三层降级：透传replyText / 卡片生成文案 / 全工具兜底
                   └──────────┘
```

### SearchPipeline 搜索管道

```
用户输入
  │
  ├─ 1. generateKeywords → 3组搜索关键词
  ├─ 2. searchWeb → 取前10条结果
  ├─ 3. 并发 fetchWeb → 多线程抓取页面 → 清洗
  ├─ 4. extractTitles → LLM 提炼片名
  ├─ 5. 并发 searchBangumi → title→card 映射
  ├─ 6. 去重：片名 distinct + subjectId HashSet
  ├─ 7. validateMatches → LLM 校验匹配（日期宽容）
  └─ 8. 聚合 ≥3条停止 / 不够换下一组关键词 / 全空LLM生成失败原因
```

## LLM 降级兜底链

```
┌─────────────────────────────────────────────────────────────┐
│ 1. HTTP 层                                                  │
│    DeepSeekThinkingDisableInterceptor → thinking disabled   │
├─────────────────────────────────────────────────────────────┤
│ 2. JSON 解析层                                              │
│    extractJsonObject 失败 → repairUnescapedQuotes 修复引号  │
│    → 仍失败 → 原始内容当纯文本 replyText                     │
├─────────────────────────────────────────────────────────────┤
│ 3. 意图分类层                                               │
│    classifyIntent 返回非法 intent → 兜底 "analyze"          │
├─────────────────────────────────────────────────────────────┤
│ 4. 终端输出层（handleOutput）                                │
│    ① replyText 已存在 → 直接透传                            │
│    ② cards 已存在 → LLM 只生成推荐文案（不给工具）           │
│    ③ 以上皆无 → 旧 LLM 全工具流程（agent-system.st）        │
│    ④ LLM 也失败 → "抱歉，无法处理你的请求。"                  │
├─────────────────────────────────────────────────────────────┤
│ 5. 对话层                                                   │
│    AgentService 异常 → "抱歉，处理出错了，请重试。"           │
└─────────────────────────────────────────────────────────────┘
```

## 快速开始

### 1. 环境要求

- Java 21+
- Node.js 20+
- Maven 3.8+
- Docker（可选，用于 SearXNG）

### 2. 后端启动

```bash
mvn spring-boot:run
```

### 3. 前端启动

```bash
cd frontend
npm install
npm run dev
```

## Tech Stack

| 层 | 技术 |
|---|---|
| 后端 | Java 21, Spring Boot 3.5, Spring AI, LangGraph4j, JPA, SQLite |
| 前端 | React 18, TypeScript, Tailwind CSS, Vite |
| 数据源 | Bangumi API (CF Worker 反代) |
| AI | Spring AI + OpenAI 兼容模型 |
| 搜索 | SearXNG / DuckDuckGo / Serper.dev |

## Bangumi API 访问说明

自 **2026 年 5 月 25 日**起，中国大陆地区无法直接访问 Bangumi API（`api.bgm.tv`）和图片 CDN（`lain.bgm.tv`）。项目提供 Cloudflare Worker 反向代理方案。

```bash
cd cf-worker/bangumi-proxy
npm install
npx wrangler login
npx wrangler deploy
```

部署后得到 `https://xxx.workers.dev` 地址，配置到 `application.yml`：

```yaml
seen:
  bangumi-proxy: ${BANGUMI_PROXY:https://your-proxy.workers.dev}
```

## v2.5 计划

1. 设置功能（模型切换 UI、搜索源切换 UI、展示偏好）
2. 多片源地址（Bangumi 关联 B站/网盘搜索）
3. LLM 翻译转换（片名/简介多语言翻译）
4. 手机 APP 开发
5. 季节新番日历（周视图展示当季播出表）
6. 统计面板（标记数量/评分分布/类型饼图）

## License

MIT
