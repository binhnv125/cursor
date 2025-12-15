package com.better.modules.keyword.service.impl;

import com.better.modules.keyword.config.NaverSearchAdProperties;
import com.better.modules.keyword.service.DatalabService;
import com.better.modules.keyword.service.model.DatalabAggregateResponse;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DatalabServiceImpl implements DatalabService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private final Clock clock = Clock.systemUTC();

    @Qualifier("naverDatalabWebClient")
    private final WebClient datalabWebClient;

    @Qualifier("naverSearchAdWebClient")
    private final WebClient searchAdWebClient;

    private final NaverSearchAdProperties searchAdProperties;

    @Override
    public Mono<DatalabAggregateResponse> fetch(String keyword, String categoryId) {
        // Datalab ShoppingInsight endpoints (POST)
        Mono<JsonNode> categoryKeywordsTrend = postDatalab(
                "/v1/datalab/shopping/category/keywords",
                datalabCategoryKeywordsRequest(keyword, categoryId)
        );

        Mono<JsonNode> keywordDeviceTrend = postDatalab(
                "/v1/datalab/shopping/category/keyword/device",
                datalabKeywordTrendRequest(keyword, categoryId)
        );

        Mono<JsonNode> keywordGenderTrend = postDatalab(
                "/v1/datalab/shopping/category/keyword/gender",
                datalabKeywordTrendRequest(keyword, categoryId)
        );

        Mono<JsonNode> keywordAgeTrend = postDatalab(
                "/v1/datalab/shopping/category/keyword/age",
                datalabKeywordAgeTrendRequest(keyword, categoryId)
        );

        // SearchAd Estimate average-position-bid (POST)
        Mono<JsonNode> avgPosBidPc = postSearchAdAveragePositionBid("PC", keyword, 1);
        Mono<JsonNode> avgPosBidMobile = postSearchAdAveragePositionBid("MOBILE", keyword, 1);

        return Mono.zip(
                        categoryKeywordsTrend,
                        keywordDeviceTrend,
                        keywordGenderTrend,
                        keywordAgeTrend,
                        avgPosBidPc,
                        avgPosBidMobile
                )
                .map(t -> DatalabAggregateResponse.builder()
                        .categoryKeywordsTrend(t.getT1())
                        .keywordDeviceTrend(t.getT2())
                        .keywordGenderTrend(t.getT3())
                        .keywordAgeTrend(t.getT4())
                        .searchAdAveragePositionBidPc(t.getT5())
                        .searchAdAveragePositionBidMobile(t.getT6())
                        .build()
                );
    }

    private Mono<JsonNode> postDatalab(String path, Map<String, Object> body) {
        return datalabWebClient
                .post()
                .uri(path)
                .bodyValue(body)
                .exchangeToMono(resp -> toJsonOrError(resp, "Datalab"));
    }

    /**
     * SearchAd header spec (commonly used):
     * - X-Timestamp: epoch millis
     * - X-API-KEY: API key
     * - X-Customer: Customer ID
     * - X-Signature: base64(HMAC-SHA256(secretKey, "{timestamp}.{method}.{uri}"))
     */
    private Mono<JsonNode> postSearchAdAveragePositionBid(String device, String keyword, int position) {
        String uri = "/estimate/average-position-bid/keyword";
        long timestamp = clock.millis();
        String signature = signSearchAd(timestamp, HttpMethod.POST.name(), uri, searchAdProperties.getSecretKey());

        Map<String, Object> body = Map.of(
                "device", device,
                "items", List.of(
                        Map.of(
                                "key", keyword,
                                "position", position
                        )
                )
        );

        return searchAdWebClient
                .post()
                .uri(uri)
                .header("X-Timestamp", String.valueOf(timestamp))
                .header("X-API-KEY", searchAdProperties.getApiKey())
                .header("X-Customer", searchAdProperties.getCustomerId())
                .header("X-Signature", signature)
                .bodyValue(body)
                .exchangeToMono(resp -> toJsonOrError(resp, "SearchAd"));
    }

    private Mono<JsonNode> toJsonOrError(ClientResponse resp, String upstream) {
        if (resp.statusCode().is2xxSuccessful()) {
            return resp.bodyToMono(JsonNode.class);
        }
        return resp.bodyToMono(String.class)
                .defaultIfEmpty("")
                .flatMap(body -> Mono.error(new IllegalStateException(
                        upstream + " API error: status=" + resp.statusCode().value() + " body=" + body
                )));
    }

    private Map<String, Object> datalabKeywordTrendRequest(String keyword, String categoryId) {
        LocalDate end = LocalDate.now(clock);
        LocalDate start = end.minusDays(30);

        return Map.of(
                "startDate", DATE.format(start),
                "endDate", DATE.format(end),
                "timeUnit", "date",
                "category", categoryId,
                "keyword", keyword,
                "device", "",
                "gender", "",
                "ages", List.of()
        );
    }

    private Map<String, Object> datalabKeywordAgeTrendRequest(String keyword, String categoryId) {
        LocalDate end = LocalDate.now(clock);
        LocalDate start = end.minusDays(30);

        return Map.of(
                "startDate", DATE.format(start),
                "endDate", DATE.format(end),
                "timeUnit", "date",
                "category", categoryId,
                "keyword", keyword,
                "device", "",
                "gender", "",
                "ages", List.of("10", "20", "30", "40", "50", "60")
        );
    }

    private Map<String, Object> datalabCategoryKeywordsRequest(String keyword, String categoryId) {
        LocalDate end = LocalDate.now(clock);
        LocalDate start = end.minusDays(30);

        // API expects keyword as array of objects: [{ name, param: [ ... ] }]
        Map<String, Object> keywordGroup = Map.of(
                "name", keyword,
                "param", List.of(keyword)
        );

        return Map.of(
                "startDate", DATE.format(start),
                "endDate", DATE.format(end),
                "timeUnit", "date",
                "category", categoryId,
                "keyword", List.of(keywordGroup),
                "device", "",
                "gender", "",
                "ages", List.of()
        );
    }

    private String signSearchAd(long timestampMillis, String method, String uri, String secretKey) {
        try {
            String message = timestampMillis + "." + method + "." + uri;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign SearchAd request", e);
        }
    }
}

