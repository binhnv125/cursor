package com.example.keywordextractor.clients;

import com.example.keywordextractor.config.NaverSearchAdProperties;
import com.example.keywordextractor.domain.KeywordBid;
import com.example.keywordextractor.domain.KeywordStats;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.example.keywordextractor.ratelimit.NaverRateLimiter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
public class NaverSearchAdClient {
  private final WebClient webClient;
  private final NaverSearchAdProperties props;
  private final NaverCredentialManager credentialManager;
  private final NaverRateLimiter rateLimiter;

  public NaverSearchAdClient(
      WebClient webClient,
      NaverSearchAdProperties props,
      NaverCredentialManager credentialManager,
      NaverRateLimiter rateLimiter) {
    this.webClient = webClient;
    this.props = props;
    this.credentialManager = credentialManager;
    this.rateLimiter = rateLimiter;
  }

  public Mono<Map<String, KeywordStats>> fetchKeywordStatsBatch(List<String> keywords) {
    if (keywords == null || keywords.isEmpty()) {
      return Mono.just(Map.of());
    }

    String hint = keywords.stream().distinct().collect(Collectors.joining(","));
    String encodedHint = URLEncoder.encode(hint, StandardCharsets.UTF_8);
    String path = "/keywordstool";
    String query = "?hintKeywords=" + encodedHint + "&showDetail=1";
    String pathWithQuery = path + query;

    return Mono.defer(
        () -> {
          return Mono.usingWhen(
              credentialManager.acquire(),
              lease ->
                  rateLimiter
                      .acquire(lease.customerId())
                      .then(
                          signedGet(lease, pathWithQuery)
                              .bodyToMono(KeywordToolResponse.class)
                              .timeout(timeout())
                              .map(
                                  resp -> {
                                    if (resp == null || resp.keywordList == null) {
                                      return Map.<String, KeywordStats>of();
                                    }
                                    Map<String, KeywordStats> out = new HashMap<>();
                                    for (KeywordToolItem it : resp.keywordList) {
                                      if (it == null
                                          || it.relKeyword == null
                                          || it.relKeyword.isBlank()) {
                                        continue;
                                      }
                                      out.put(it.relKeyword, it.toStats());
                                    }
                                    return out;
                                  })),
              lease -> Mono.fromRunnable(lease::close));
        });
  }

  public Mono<Map<String, Integer>> fetchMedianBidBatch(List<String> keywords, Device device) {
    if (keywords == null || keywords.isEmpty()) {
      return Mono.just(Map.of());
    }

    String path = "/estimate/median-bid/keyword";
    String pathWithQuery = path;

    MedianBidRequest req =
        new MedianBidRequest(device.name(), keywords.stream().distinct().map(MedianBidItem::new).toList());

    return Mono.defer(
        () -> {
          return Mono.usingWhen(
              credentialManager.acquire(),
              lease ->
                  rateLimiter
                      .acquire(lease.customerId())
                      .then(
                          signedPost(lease, pathWithQuery, req)
                              .bodyToMono(MedianBidResponse.class)
                              .timeout(timeout())
                              .map(
                                  resp -> {
                                    if (resp == null || resp.items == null) {
                                      return Map.<String, Integer>of();
                                    }
                                    Map<String, Integer> out = new HashMap<>();
                                    for (MedianBidResult r : resp.items) {
                                      if (r == null || r.keyword == null || r.keyword.isBlank()) {
                                        continue;
                                      }
                                      out.put(r.keyword, r.resolvedBid());
                                    }
                                    return out;
                                  })),
              lease -> Mono.fromRunnable(lease::close));
        });
  }

  public Mono<Map<String, KeywordBid>> fetchBidBatch(List<String> keywords) {
    return Mono.zip(
            fetchMedianBidBatch(keywords, Device.PC),
            fetchMedianBidBatch(keywords, Device.MOBILE),
            (pc, mobile) -> {
              Map<String, KeywordBid> out = new HashMap<>();
              for (String k : keywords) {
                out.put(k, new KeywordBid(pc.get(k), mobile.get(k)));
              }
              return out;
            })
        .onErrorReturn(Map.of());
  }

  private WebClient.ResponseSpec signedGet(NaverCredentialManager.Lease lease, String pathWithQuery) {
    return webClient
        .get()
        .uri(props.baseUrl() + pathWithQuery)
        .headers(h -> signHeaders(h, lease, "GET", pathWithQuery))
        .accept(MediaType.APPLICATION_JSON)
        .retrieve();
  }

  private WebClient.ResponseSpec signedPost(NaverCredentialManager.Lease lease, String path, Object body) {
    return webClient
        .post()
        .uri(props.baseUrl() + path)
        .headers(h -> signHeaders(h, lease, "POST", path))
        .contentType(MediaType.APPLICATION_JSON)
        .accept(MediaType.APPLICATION_JSON)
        .bodyValue(body)
        .retrieve();
  }

  private void signHeaders(
      HttpHeaders headers, NaverCredentialManager.Lease lease, String method, String pathWithQuery) {
    String ts = String.valueOf(System.currentTimeMillis());
    String sig = NaverSearchAdAuth.signature(ts, method, pathWithQuery, lease.apiSecret());

    headers.set("X-Timestamp", ts);
    headers.set("X-API-KEY", lease.apiKey());
    headers.set("X-Customer", lease.customerId());
    headers.set("X-Signature", sig);
  }

  private Duration timeout() {
    return props.timeout() == null ? Duration.ofSeconds(5) : props.timeout();
  }

  public enum Device {
    PC,
    MOBILE
  }

  // --- keywordstool ---
  public static class KeywordToolResponse {
    @JsonProperty("keywordList")
    public List<KeywordToolItem> keywordList;
  }

  public static class KeywordToolItem {
    @JsonProperty("relKeyword")
    public String relKeyword;

    @JsonProperty("monthlyPcQcCnt")
    public String monthlyPcQcCnt;

    @JsonProperty("monthlyMobileQcCnt")
    public String monthlyMobileQcCnt;

    @JsonProperty("monthlyPcClickCnt")
    public String monthlyPcClickCnt;

    @JsonProperty("monthlyMobileClickCnt")
    public String monthlyMobileClickCnt;

    @JsonProperty("monthlyPcCtr")
    public String monthlyPcCtr;

    @JsonProperty("monthlyMobileCtr")
    public String monthlyMobileCtr;

    @JsonProperty("compIdx")
    public String compIdx;

    @JsonProperty("plAvgDepth")
    public String plAvgDepth;

    KeywordStats toStats() {
      return new KeywordStats(
          parseLongish(monthlyPcQcCnt),
          parseLongish(monthlyMobileQcCnt),
          parseLongish(monthlyPcClickCnt),
          parseLongish(monthlyMobileClickCnt),
          parseDoubleish(monthlyPcCtr),
          parseDoubleish(monthlyMobileCtr),
          compIdx,
          parseDoubleish(plAvgDepth));
    }

    private Long parseLongish(String s) {
      if (s == null) {
        return null;
      }
      String v = s.trim().toUpperCase(Locale.ROOT);
      if (v.isBlank()) {
        return null;
      }
      if (v.startsWith("<")) {
        v = v.substring(1);
      }
      try {
        return Long.parseLong(v);
      } catch (Exception e) {
        return null;
      }
    }

    private Double parseDoubleish(String s) {
      if (s == null) {
        return null;
      }
      String v = s.trim();
      if (v.isBlank()) {
        return null;
      }
      try {
        return Double.parseDouble(v);
      } catch (Exception e) {
        return null;
      }
    }
  }

  // --- median bid ---
  public record MedianBidRequest(String device, List<MedianBidItem> items) {}

  public record MedianBidItem(String keyword) {}

  public static class MedianBidResponse {
    @JsonProperty("items")
    public List<MedianBidResult> items;
  }

  public static class MedianBidResult {
    @JsonProperty("keyword")
    public String keyword;

    // Field name varies by API; keep both and resolve.
    @JsonProperty("bid")
    public Integer bid;

    @JsonProperty("medianBid")
    public Integer medianBid;

    @JsonProperty("bidAmt")
    public Integer bidAmt;

    Integer resolvedBid() {
      if (bid != null) return bid;
      if (medianBid != null) return medianBid;
      return bidAmt;
    }
  }
}
