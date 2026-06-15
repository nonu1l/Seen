# Seen - 个人的影视记录工具

Seen 是一个轻量、自部署的影视 / 番剧记录系统，适合用来维护自己的私人片库。

![Seen 宣传图](assets/seen-promo.png)

![Seen AI 助手宣传图](assets/seen-ai-promo.png)

## 特点功能

- **轻量自部署**：Docker + SQLite，不需要额外数据库，数据保存在自己的环境里，适合维护私人片库。
- **记录范围广**：基于反向代理的 [Bangumi API](https://bangumi.github.io/api/) 补全作品信息，番剧、电影、综艺、电视剧都能记。
- **观影状态管理**：支持想看 / 在看 / 看过 / 搁置 / 抛弃，也支持 10 分制评分、短评。
- **封面墙和详情页**：自动带出封面、年份、简介、标签、Bangumi 评分、IMDb 跳转、角色 / 演员信息。
- **AI 助手**：可以用对话批量标记整季动画、系列电影或多个作品，修改评分、状态和影评，取消标记并撤回；也可以按剧情、角色、关键词找片，搜索热门作品，或根据本地记录推荐相似作品。

---

如果你不懂开发，只是想要一个轻量、可自部署、带 AI 助手的个人影视记录工具，可以直接看 [部署指南](docs/部署指南.md)。

## Bangumi API 访问说明

自 **2026 年 5 月 25 日**起，中国大陆地区无法直接访问 Bangumi API（`api.bgm.tv`）和图片 CDN（`lain.bgm.tv`）。项目提供 Cloudflare Worker 反向代理方案，同时也通过同一个 Worker 代理 DuckDuckGo Lite 搜索。可以自己部署 Worker，也可以先使用临时提供的 [公开反向代理地址](docs/反向代理地址.md)。

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

## Tech Stack

| 层 | 技术 |
|---|---|
| 后端 | Java 21, Spring Boot 3.5, Spring AI, LangGraph4j, JPA, SQLite |
| 前端 | React 18, TypeScript, Tailwind CSS, Vite |
| 数据源 | Bangumi API (CF Worker 反代) |
| AI | Spring AI + OpenAI 兼容模型 |
| 搜索 | DuckDuckGo (CF Worker 反代) / Serper.dev |


## TODO

1. 设置功能（模型切换 UI、搜索源切换 UI、展示偏好）
2. 多片源地址（Bangumi 关联 B站/爱奇艺/腾讯视频等其他片源地址，匹配出哪里可以）
3. LLM 翻译转换（片名/简介多语言翻译）
4. 手机 APP 开发
5. 季节新番日历（周视图展示当季播出表）
6. 统计面板（标记数量/评分分布/类型饼图）
7. 探索在线更新方式

## License

MIT
