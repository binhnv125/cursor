package com.better.modules.keyword.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class NaverWebClientConfig {

    @Bean
    @Qualifier("naverDatalabWebClient")
    public WebClient naverDatalabWebClient(NaverDatalabProperties props, WebClient.Builder builder) {
        return builder
                .baseUrl(props.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-Naver-Client-Id", props.getClientId())
                .defaultHeader("X-Naver-Client-Secret", props.getClientSecret())
                .build();
    }

    @Bean
    @Qualifier("naverSearchAdWebClient")
    public WebClient naverSearchAdWebClient(NaverSearchAdProperties props, WebClient.Builder builder) {
        return builder
                .baseUrl(props.getBaseUrl())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}

