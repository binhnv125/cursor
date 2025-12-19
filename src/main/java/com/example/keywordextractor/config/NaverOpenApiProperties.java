package com.example.keywordextractor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "naverOpenApi")
public record NaverOpenApiProperties(
    String baseUrl,
    String clientId,
    String clientSecret,
    String timezone) {}
