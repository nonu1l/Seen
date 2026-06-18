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
    private String serperSearchUrl;
    private String tavilySearchUrl;

    public String getBangumiApiBase() {
        return bangumiApiBase;
    }

    public void setBangumiApiBase(String bangumiApiBase) {
        this.bangumiApiBase = bangumiApiBase;
    }

    public String getSerperSearchUrl() {
        return serperSearchUrl;
    }

    public void setSerperSearchUrl(String serperSearchUrl) {
        this.serperSearchUrl = serperSearchUrl;
    }

    public String getTavilySearchUrl() {
        return tavilySearchUrl;
    }

    public void setTavilySearchUrl(String tavilySearchUrl) {
        this.tavilySearchUrl = tavilySearchUrl;
    }
}
