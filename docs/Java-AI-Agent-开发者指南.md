# Seen Java AI / Agent 开发者指南

这份文档面向有 Java Spring 基础、想学习 Java AI / Agent 开发的读者。它只保留启动配置、阅读路线和开发重点；具体实现请直接看源码。

## 可以学到什么

Seen 是一个带真实业务闭环的 Java AI Agent 项目，适合学习：

- Spring AI `ChatClient` 和 Tool Calling
- LangGraph4j Agent 流程编排
- Prompt 与结构化 JSON 输出
- 网页搜索、页面抓取、LLM 提取、Bangumi 匹配组成的搜索管道
- LLM 输出不稳定时的解析、修复和降级
- Token 用量记录与 AI 调用观测
- AI 结果如何落到真实业务数据

## 快速配置

### Bangumi 反向代理 (Cloudflare Worker)

```bash
cd cf-worker/bangumi-proxy
npm install
npx wrangler login
npx wrangler deploy
```

后端配置：

```bash
BANGUMI_PROXY=https://你的-worker.workers.dev
```

### AI Key

AI 功能需要：

- DeepSeek API Key：[platform.deepseek.com](https://platform.deepseek.com/)
- Serper API Key：[serper.dev](https://serper.dev/)

后端配置：

```bash
LLM_API_KEY=你的_DeepSeek_API_Key
LLM_BASE_URL=https://api.deepseek.com
LLM_MODEL=deepseek-v4-flash
SEARCH_PROVIDER=serper
SERPER_API_KEY=你的_Serper_API_Key
```

不调试 AI 时：

```bash
AI_ENABLED=false
```

## 本地启动

后端：

```bash
mvn spring-boot:run
```

前端：

```bash
cd frontend
npm install
npm run dev
```

默认地址：

- 后端：`http://localhost:8081`
- 前端：`http://localhost:5173`
- Token 用量追踪：`http://localhost:8081/admin/token-usage`

## 阅读路线

建议按这个顺序看：

| 顺序 | 文件 | 重点 |
|---|---|---|
| 1 | `controller/ConversationController.java` | AI 请求入口 |
| 2 | `service/ConversationService.java` | 会话、历史、Agent 调用、卡片落库 |
| 3 | `agent/SeenAgentState.java` | LangGraph4j 状态结构 |
| 4 | `agent/AgentService.java` | `classify -> 分支处理 -> output` 的 Agent 编排 |
| 5 | `service/BangumiTools.java` | LLM 可调用工具 |
| 6 | `agent/SearchPipeline.java` | 推荐 / 描述找片的多步搜索管道 |
| 7 | `resources/prompts/*.st` | 分类、工具调用、输出和校验 prompt |
| 8 | `service/IntentAnalysisService.java` | LLM JSON 提取与修复 |
| 9 | `config/TokenUsageAdvisor.java` | Token 用量追踪 |

核心流程：

```text
用户输入
  -> 意图分类
  -> 标记 / 取消标记 / 推荐 / 搜索 / 分析
  -> 工具调用或搜索管道
  -> 统一输出
  -> 会话和卡片保存
```

## 开发建议

- 先跑普通影视记录功能，确认 Bangumi 搜索和详情可用。
- 再打开 AI，只测 `analyze`，观察最简单的 LLM 调用。
- 然后测试“标记某部作品”，看工具调用和 JSON 输出。
- 最后测试推荐或描述找片，重点看 `SearchPipeline`。
- 初次修改建议从 prompt 或一个小工具函数开始，不要直接重写整个 Agent。

