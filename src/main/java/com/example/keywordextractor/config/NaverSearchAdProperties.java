package com.example.keywordextractor.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "naverSearchAd")
public record NaverSearchAdProperties(
    String baseUrl,
    String apiKey,
    String apiSecret,
    String customerId,
    Duration timeout) {}
