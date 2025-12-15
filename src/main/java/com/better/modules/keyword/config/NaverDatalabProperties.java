package com.better.modules.keyword.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "naver.datalab")
public class NaverDatalabProperties {
    private String baseUrl;
    private String clientId;
    private String clientSecret;
}

