package com.better.modules.keyword.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "naver.searchad")
public class NaverSearchAdProperties {
    private String baseUrl;
    private String apiKey;
    private String secretKey;
    private String customerId;
}

