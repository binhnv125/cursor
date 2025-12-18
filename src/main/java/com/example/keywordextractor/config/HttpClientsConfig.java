package com.example.keywordextractor.config;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.handler.timeout.WriteTimeoutHandler;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
public class HttpClientsConfig {

  @Bean
  public WebClient webClient(WebClient.Builder builder) {
    // Larger JSON payloads from LLM responses are possible; keep reasonable.
    ExchangeStrategies strategies =
        ExchangeStrategies.builder()
            .codecs(c -> c.defaultCodecs().maxInMemorySize(2 * 1024 * 1024))
            .build();

    HttpClient httpClient =
        HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5_000)
            .responseTimeout(Duration.ofSeconds(15))
            .doOnConnected(
                conn ->
                    conn.addHandlerLast(new ReadTimeoutHandler(15, TimeUnit.SECONDS))
                        .addHandlerLast(new WriteTimeoutHandler(15, TimeUnit.SECONDS)));

    return builder
        .clientConnector(new ReactorClientHttpConnector(httpClient))
        .exchangeStrategies(strategies)
        .build();
  }
}
