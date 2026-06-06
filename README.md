# Seen - 个人的影视记录工具

Seen 是一个轻量、自部署的影视 / 番剧记录系统。它使用 SQLite 保存数据，使用 Bangumi 作为作品数据源，可以快速记录自己看了哪些厕纸、番剧、电影、综艺和电视剧，也可以评分、写评价、标记观看状态。

如果你只是想要一个可以自己部署的影视记录工具，直接看 [普通用户部署指南](docs/普通用户部署指南.md)。

如果你有 Java Spring 基础，想学习 Java AI / Agent 开发，可以看 [Java AI / Agent 开发者指南](docs/Java-AI-Agent-开发者指南.md)。

## 这个项目可以做什么

### 面向普通使用者

如果你需要一个轻量、自部署的影视 / 番剧系统，那么 Seen 更适合你。它使用 SQLite + Docker 部署方式，不需要单独维护复杂数据库；使用 Bangumi 数据源，覆盖大多数番剧，也能记录绝大多数电影、综艺、电视剧。

Bangumi 在中国大陆可能无法直接访问，所以项目提供 Cloudflare Worker 反向代理方案。AI 助手是可选功能：不开 AI 时，它就是一个普通的影视记录系统；开启 AI 后，可以用自然语言搜索、推荐、批量标记作品。

部署方式见：[普通用户部署指南](docs/普通用户部署指南.md)。

### 面向 Java AI / Agent 初学者

如果你已经熟悉 Java Spring，想找一个真实一点的 Java AI Agent 项目练手，Seen 可以帮你学习：

- Spring AI `ChatClient` 的使用方式
- Tool Calling：把 Java 方法注册成模型可调用工具
- LangGraph4j：用图结构编排 Agent 流程
- Prompt 工程：分类、工具调用、JSON 输出、推荐文案
- RAG-like 搜索链路：网页搜索、页面抓取、LLM 提取、业务数据库匹配
- LLM 输出降级：JSON 修复、意图兜底、输出兜底
- Token 用量追踪：记录每轮 Agent 节点的 token 消耗
- AI 结果如何落到真实业务数据，而不是只停留在聊天文本

开发说明见：[Java AI / Agent 开发者指南](docs/Java-AI-Agent-开发者指南.md)。

## Features

- 基于 [Bangumi API](https://bangumi.github.io/api/) 做元数据匹配，覆盖大多数电影、电视剧、动画、综艺
- 标记观看状态（想看 / 在看 / 看过 / 搁置 / 抛弃），支持删除与恢复
- 10 分制评分 + 文字影评
- 请求缓存与数据预热机制，搜索和详情快速响应
- 动画 / 真人自动区分：真人作品使用演员照片，角色按主角 -> 配角 -> 客串排序
- IMDb 快捷链接：有 `imdb_id` 时精准跳转，无 `imdb_id` 时搜索跳转

### AI 助手

- 自然语言输入，自动识别标记、取消标记、推荐、搜索、分析五类意图
- 多步搜索管道：关键词生成 → 并发网页搜索 → 页面抓取清洗 → LLM 片名提取 → Bangumi 匹配 → 校验去重
- 支持批量标记、修改评分/状态/影评、取消标记（可撤回）
- 多源搜索可切换（DuckDuckGo / Serper.dev）
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

### Docker 运行

不使用 AI 助手：

```bash
docker pull youmiepie/seen:latest

docker run -d \
  --name seen \
  -p 8081:8081 \
  -v seen-data:/app/data \
  -e BANGUMI_PROXY=https://你的-worker.workers.dev \
  -e AI_ENABLED=false \
  youmiepie/seen:latest
```

使用 AI 助手：

```bash
docker pull youmiepie/seen:latest

docker run -d \
  --name seen \
  -p 8081:8081 \
  -v seen-data:/app/data \
  -e BANGUMI_PROXY=https://你的-worker.workers.dev \
  -e AI_ENABLED=true \
  -e LLM_API_KEY=你的_DeepSeek_API_Key \
  -e LLM_BASE_URL=https://api.deepseek.com \
  -e LLM_MODEL=deepseek-v4-flash \
  -e SEARCH_PROVIDER=serper \
  -e SERPER_API_KEY=你的_Serper_API_Key \
  youmiepie/seen:latest
```

启动后打开：

```text
http://localhost:8081
```

完整部署步骤见：[普通用户部署指南](docs/普通用户部署指南.md)。

### 本地开发环境要求

- Java 21+
- Node.js 20+
- Maven 3.8+
- Docker（可选，用于运行发布镜像）

### 后端启动

```bash
mvn spring-boot:run
```

### 前端启动

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
| 搜索 | DuckDuckGo / Serper.dev |
| 部署 | Docker, GitHub Actions |

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

`xxx.workers.dev` 域名在部分网络环境下可能会受到 DNS 解析污染。更稳的做法是把自己的域名关联到 Cloudflare，再绑定到 Worker 进行解析。如果你觉得麻烦，可以先临时使用作者提供的公开地址：[反向代理地址](docs/反向代理地址.md)。


## v2.5 计划

1. 设置功能（模型切换 UI、搜索源切换 UI、展示偏好）
2. 多片源地址（Bangumi 关联 B站/网盘搜索）
3. LLM 翻译转换（片名/简介多语言翻译）
4. 手机 APP 开发
5. 季节新番日历（周视图展示当季播出表）
6. 统计面板（标记数量/评分分布/类型饼图）

## License

MIT
