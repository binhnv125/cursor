package com.example.keywordextractor.clients.openapi;

import com.example.keywordextractor.config.NaverOpenApiProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class NaverDatalabClient {
  private final WebClient webClient;
  private final NaverOpenApiProperties props;

  public NaverDatalabClient(WebClient webClient, NaverOpenApiProperties props) {
    this.webClient = webClient;
    this.props = props;
  }

  public Mono<DistributionResponse> age(LocalDate startDate, LocalDate endDate, String categoryId, String keyword) {
    requireConfigured();
    return post(
        "/v1/datalab/shopping/category/keyword/age",
        new DistributionRequest(startDate.toString(), endDate.toString(), "month", categoryId, keywordBlock(keyword)));
  }

  public Mono<DistributionResponse> gender(
      LocalDate startDate, LocalDate endDate, String categoryId, String keyword) {
    requireConfigured();
    return post(
        "/v1/datalab/shopping/category/keyword/gender",
        new DistributionRequest(startDate.toString(), endDate.toString(), "month", categoryId, keywordBlock(keyword)));
  }

  public Mono<DistributionResponse> device(
      LocalDate startDate, LocalDate endDate, String categoryId, String keyword) {
    requireConfigured();
    return post(
        "/v1/datalab/shopping/category/keyword/device",
        new DistributionRequest(startDate.toString(), endDate.toString(), "week", categoryId, keywordBlock(keyword)));
  }

  public Mono<KeywordsTrendResponse> keywordsTrend(
      LocalDate startDate,
      LocalDate endDate,
      String categoryId,
      String keyword,
      String timeUnit) {
    requireConfigured();
    return post(
        "/v1/datalab/shopping/category/keywords",
        new TrendRequest(startDate.toString(), endDate.toString(), timeUnit, categoryId, keywordBlock(keyword)));
  }

  private List<KeywordBlock> keywordBlock(String keyword) {
    return List.of(new KeywordBlock(keyword, List.of(keyword)));
  }

  private Mono<DistributionResponse> post(String path, DistributionRequest body) {
    return webClient
        .post()
        .uri(props.baseUrl() + path)
        .header("X-Naver-Client-Id", props.clientId())
        .header("X-Naver-Client-Secret", props.clientSecret())
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .retrieve()
        .bodyToMono(DistributionResponse.class);
  }

  private Mono<KeywordsTrendResponse> post(String path, TrendRequest body) {
    return webClient
        .post()
        .uri(props.baseUrl() + path)
        .header("X-Naver-Client-Id", props.clientId())
        .header("X-Naver-Client-Secret", props.clientSecret())
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .retrieve()
        .bodyToMono(KeywordsTrendResponse.class);
  }

  private void requireConfigured() {
    if (props.clientId() == null
        || props.clientId().isBlank()
        || props.clientSecret() == null
        || props.clientSecret().isBlank()) {
      throw new IllegalStateException(
          "Missing NAVER_OPENAPI_CLIENT_ID / NAVER_OPENAPI_CLIENT_SECRET");
    }
  }

  public record DistributionRequest(
      @JsonProperty("startDate") String startDate,
      @JsonProperty("endDate") String endDate,
      @JsonProperty("timeUnit") String timeUnit,
      @JsonProperty("category") String category,
      @JsonProperty("keyword") List<KeywordBlock> keyword) {}

  public record TrendRequest(
      @JsonProperty("startDate") String startDate,
      @JsonProperty("endDate") String endDate,
      @JsonProperty("timeUnit") String timeUnit,
      @JsonProperty("category") String category,
      @JsonProperty("keyword") List<KeywordBlock> keyword) {}

  public record KeywordBlock(@JsonProperty("name") String name, @JsonProperty("param") List<String> param) {}

  public static class DistributionResponse {
    @JsonProperty("results")
    public List<DistributionResult> results;
  }

  public static class DistributionResult {
    @JsonProperty("title")
    public String title;

    @JsonProperty("category")
    public List<String> category;

    @JsonProperty("keyword")
    public List<List<String>> keyword;

    @JsonProperty("data")
    public List<DistributionData> data;
  }

  public static class DistributionData {
    @JsonProperty("group")
    public String group;

    @JsonProperty("ratio")
    public Double ratio;
  }

  public static class KeywordsTrendResponse {
    @JsonProperty("results")
    public List<TrendResult> results;
  }

  public static class TrendResult {
    @JsonProperty("title")
    public String title;

    @JsonProperty("category")
    public List<String> category;

    @JsonProperty("keyword")
    public List<List<String>> keyword;

    @JsonProperty("data")
    public List<TrendPoint> data;
  }

  public static class TrendPoint {
    @JsonProperty("period")
    public String period;

    @JsonProperty("ratio")
    public Double ratio;
  }
}
