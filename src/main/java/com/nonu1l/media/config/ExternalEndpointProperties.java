package com.nonu1l.media.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 外部服务 endpoint 配置。
 */
@Component
@ConfigurationProperties(prefix = "app.endpoints")
public class ExternalEndpointProperties {

    private String bangumiApiBase;
    private String duckduckgoLiteSearchUrl;
    private String serperSearchUrl;

    public String getBangumiApiBase() {
        return bangumiApiBase;
    }

    public void setBangumiApiBase(String bangumiApiBase) {
        this.bangumiApiBase = bangumiApiBase;
    }

    public String getDuckduckgoLiteSearchUrl() {
        return duckduckgoLiteSearchUrl;
    }

    public void setDuckduckgoLiteSearchUrl(String duckduckgoLiteSearchUrl) {
        this.duckduckgoLiteSearchUrl = duckduckgoLiteSearchUrl;
    }

    public String getSerperSearchUrl() {
        return serperSearchUrl;
    }

    public void setSerperSearchUrl(String serperSearchUrl) {
        this.serperSearchUrl = serperSearchUrl;
    }
}
