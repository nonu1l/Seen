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

    // /search?q=... -> DuckDuckGo Lite（解码重定向链接为真实 URL）
    if (path.startsWith("/search")) {
      const q = url.searchParams.get("q");
      if (!q) return new Response("missing q", { status: 400 });
      const ddgResp = await fetch(`https://lite.duckduckgo.com/lite/?q=${encodeURIComponent(q)}`, {
        headers: { "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64)" },
      });
      let html = await ddgResp.text();
      html = html.replace(/href="((?:https?:)?\/\/duckduckgo\.com\/l\/\?uddg=([^"&]*)(?:[^"]*)?)"/g,
        (_: string, _full: string, encoded: string) => {
          try { return `href="${decodeURIComponent(encoded)}"`; }
          catch { return _; }
        });
      return new Response(html, {
        headers: { "Content-Type": "text/html; charset=utf-8" },
      });
    }

    // everything else -> Bangumi image CDN
    return fetch(BGM_IMG + path, {
      headers: { "User-Agent": "seen-app/1.0" },
    });
  },
};
