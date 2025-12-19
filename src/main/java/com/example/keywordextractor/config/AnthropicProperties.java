package com.example.keywordextractor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "anthropic")
public record AnthropicProperties(String baseUrl, String apiKey, String model, int maxTokens) {}
