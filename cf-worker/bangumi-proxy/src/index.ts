const BGM_API = "https://api.bgm.tv/v0";
const BGM_IMG = "https://lain.bgm.tv";

export default {
  async fetch(req: Request): Promise<Response> {
    const url = new URL(req.url);
    const path = url.pathname + url.search;

    // /api/* -> Bangumi API
    if (path.startsWith("/api")) {
      const apiPath = path.slice(4);
      return fetch(BGM_API + apiPath, {
        method: req.method,
        headers: {
          "User-Agent": "seen-app/1.0",
          "Content-Type": req.headers.get("Content-Type") ?? "application/json",
        },
        body: req.method !== "GET" ? await req.text() : undefined,
      });
    }

    // everything else -> Bangumi image CDN
    return fetch(BGM_IMG + path, {
      headers: { "User-Agent": "seen-app/1.0" },
    });
  },
};
