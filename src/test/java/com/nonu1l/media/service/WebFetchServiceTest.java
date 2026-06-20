package com.nonu1l.media.service;

import com.nonu1l.media.model.dto.WebFetchResultDTO;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * WebFetchService 的外部站点访问验证，主要用于手动确认 fetchWeb 对特定 URL 的抓取结果。
 */
class WebFetchServiceTest {

    private static final String IMDB_URL = "https://www.imdb.com/title/tt1890725";

    /**
     * 访问 IMDb 条目页并输出抓取结果；IMDb 可能返回 403 或机器人页，因此只断言已取得 HTTP 响应。
     */
    @Test
    void fetchImdbTitleCapturesHttpResponse() {
        WebFetchService service = new WebFetchService(
                6000,
                12000,
                1024 * 1024,
                3,
                Duration.ofSeconds(10),
                "seen-app-agentic-fetch/1.0",
                40
        );

        WebFetchResultDTO result = service.fetch(IMDB_URL, 4000);

        assertNotNull(result);
        System.out.printf("""
                        ok=%s
                        status=%d
                        url=%s
                        contentType=%s
                        title=%s
                        error=%s
                        hint=%s
                        textPreview=%s
                        %n""",
                result.ok(),
                result.status(),
                result.url(),
                result.contentType(),
                result.title(),
                result.error(),
                result.hint(),
                preview(result.text()));
        assertTrue(result.status() > 0, "IMDb should return an HTTP response status");
    }

    private static String preview(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.length() <= 500 ? text : text.substring(0, 500);
    }
}
