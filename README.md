# Seen

a private cinema diary — 个人的影视记录工具

## Status

当前为 **v1.x 基础版本**，仅做功能优化和缺陷修复，不再开发新功能。

v2.x 将延续开发 AI 相关能力（项目中的 LLM 代码及配置在 v1.x 中不会被调用）。

如果你需要一个简洁、快速的影视记录工具，当前版本非常合适。

## Features

- 基于 [Bangumi API](https://bangumi.github.io/api/) 做元数据匹配，覆盖大多数电影、电视剧、动画、综艺
- 标记观看状态（想看 / 在看 / 看过 / 搁置 / 抛弃）
- 10 分制评分 + 文字评价
- 请求缓存与数据预热机制，搜索和详情快速响应

## Tech Stack

| 层 | 技术 |
|---|---|
| 后端 | Java 21, Spring Boot 3.5, JPA, SQLite |
| 前端 | React 18, TypeScript, Tailwind CSS, Vite |
| 数据源 | Bangumi API |


## License

MIT
