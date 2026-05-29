# Seen

a private cinema diary — 个人的影视记录工具

## Features

- 基于 [Bangumi API](https://bangumi.github.io/api/) 做元数据匹配，覆盖大多数电影、电视剧、动画、综艺
- 标记观看状态（想看 / 在看 / 看过 / 搁置 / 抛弃），支持删除与恢复
- 10 分制评分 + 文字影评
- 请求缓存与数据预热机制，搜索和详情快速响应

### AI 助手

- 自然语言输入，自动识别标记、修改、推荐意图并搜索匹配条目
- 支持批量标记、修改评分/状态/影评、取消标记（可撤回）
- 基于本地已看记录的偏好分析与智能推荐
- 对话式交互，历史会话持久化，多轮上下文理解

> AI 功能当前使用 **DeepSeek V4** 系列模型开发与测试。其他 OpenAI 兼容模型（如 GLM、Qwen 等）可自行配置 `LLM_API_KEY`、`LLM_BASE_URL`、`LLM_MODEL` 环境变量尝试。

## Status

当前版本仍为 **测试版本**，在体验和功能方面仍需继续完善。如果您有不错的想法或建议，不妨在 Issues 中提出，让我们一起来完善。

### 待开发完善

1. 设置功能
2. 多片源地址
3. LLM 翻译转换
4. 手机 APP 开发

## Tech Stack

| 层 | 技术 |
|---|---|
| 后端 | Java 21, Spring Boot 3.5, Spring AI, JPA, SQLite |
| 前端 | React 18, TypeScript, Tailwind CSS, Vite |
| 数据源 | Bangumi API |
| AI | Spring AI + DeepSeek V4 |


## Bangumi API 访问说明

自 **2026 年 5 月 25 日**起，中国大陆地区无法直接访问 Bangumi API（`api.bgm.tv`）和图片 CDN（`lain.bgm.tv`）。由于 Bangumi 在动画元数据方面有着无可替代的覆盖度和中文质量，项目提供了 Cloudflare Worker 反向代理方案。

### 部署反向代理

1. 注册 [Cloudflare](https://cloudflare.com) 账号

2. 登录并部署：

```bash
cd cf-worker/bangumi-proxy
npm install
npx wrangler login
npx wrangler deploy
```

3. 部署后会得到一个 `https://xxx.workers.dev` 地址。如果 `workers.dev` 在你所在地区也受限，可绑定**自定义域名**（Dashboard → Workers & Pages → Settings → Domains & Routes → Custom Domain）

4. 配置项目：

```yaml
# application.yml
seen:
  bangumi-proxy: ${BANGUMI_PROXY:https://seen.proxy.com}
```

清空该配置则使用 Bangumi 直连。

## License

MIT
