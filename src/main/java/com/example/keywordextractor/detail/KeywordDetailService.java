package com.example.keywordextractor.detail;

import com.example.keywordextractor.clients.NaverSearchAdClient;
import com.example.keywordextractor.clients.openapi.NaverDatalabClient;
import com.example.keywordextractor.config.NaverOpenApiProperties;
import com.example.keywordextractor.detail.KeywordDetailResponse.NaverDatalabAge;
import com.example.keywordextractor.detail.KeywordDetailResponse.NaverDatalabDevice;
import com.example.keywordextractor.detail.KeywordDetailResponse.NaverDatalabGender;
import com.example.keywordextractor.detail.KeywordDetailResponse.NaverSearchAdRankBid;
import com.example.keywordextractor.detail.KeywordDetailResponse.NaverSearchAdRankBid.RankBid;
import com.example.keywordextractor.detail.KeywordDetailResponse.NaverTrendPoint;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class KeywordDetailService {
  private final NaverDatalabClient datalab;
  private final NaverSearchAdClient searchAd;
  private final ZoneId zone;

  public KeywordDetailService(
      NaverDatalabClient datalab, NaverSearchAdClient searchAd, NaverOpenApiProperties props) {
    this.datalab = datalab;
    this.searchAd = searchAd;
    this.zone = ZoneId.of(props.timezone() == null || props.timezone().isBlank() ? "Asia/Seoul" : props.timezone());
  }

  public Mono<KeywordDetailResponse> detail(KeywordDetailRequest req) {
    LocalDate today = LocalDate.now(zone);
    LocalDate oneYearStart = today.minusYears(1).plusDays(1);
    LocalDate oneMonthStart = today.minusMonths(1).plusDays(1);

    Mono<NaverDatalabAge> age =
        datalab
            .age(oneYearStart, today, req.categoryId(), req.keyword())
            .map(r -> toAge(req.categoryId(), r));

    Mono<NaverDatalabGender> gender =
        datalab
            .gender(oneYearStart, today, req.categoryId(), req.keyword())
            .map(r -> toGender(req.categoryId(), r));

    Mono<NaverDatalabDevice> device =
        datalab
            .device(oneMonthStart, today, req.categoryId(), req.keyword())
            .map(r -> toDevice(req.categoryId(), r));

    Mono<List<NaverTrendPoint>> monthlyTrend =
        datalab
            .keywordsTrend(oneYearStart, today, req.categoryId(), req.keyword(), "month")
            .map(r -> toTrendPoints(r));

    Mono<Map<String, Double>> weekdayRatio =
        datalab
            .keywordsTrend(oneYearStart, today, req.categoryId(), req.keyword(), "date")
            .map(r -> toWeekdayRatio(r));

    Mono<NaverSearchAdRankBid> rankBid = searchAd.fetchRankBids(req.keyword());

    return Mono.zip(age, gender, device, monthlyTrend, weekdayRatio, rankBid)
        .map(
            t ->
                new KeywordDetailResponse(
                    req.keyword(),
                    req.categoryId(),
                    new KeywordDetailResponse.DateRange(oneYearStart, today),
                    new KeywordDetailResponse.DateRange(oneYearStart, today),
                    new KeywordDetailResponse.DateRange(oneMonthStart, today),
                    new KeywordDetailResponse.DateRange(oneYearStart, today),
                    new KeywordDetailResponse.DateRange(oneYearStart, today),
                    t.getT6(),
                    t.getT1(),
                    t.getT2(),
                    t.getT3(),
                    t.getT4(),
                    t.getT5()));
  }

  private NaverDatalabAge toAge(String categoryId, NaverDatalabClient.DistributionResponse resp) {
    var data = firstDistribution(resp);
    List<NaverDatalabAge.GroupRatio> ratios = new ArrayList<>();
    for (var d : data) {
      ratios.add(new NaverDatalabAge.GroupRatio(d.group, d.ratio));
    }
    return new NaverDatalabAge(categoryId, ratios);
  }

  private NaverDatalabGender toGender(String categoryId, NaverDatalabClient.DistributionResponse resp) {
    var data = firstDistribution(resp);
    List<NaverDatalabGender.GroupRatio> ratios = new ArrayList<>();
    for (var d : data) {
      ratios.add(new NaverDatalabGender.GroupRatio(d.group, d.ratio));
    }
    return new NaverDatalabGender(categoryId, ratios);
  }

  private NaverDatalabDevice toDevice(String categoryId, NaverDatalabClient.DistributionResponse resp) {
    var data = firstDistribution(resp);
    List<NaverDatalabDevice.GroupRatio> ratios = new ArrayList<>();
    for (var d : data) {
      ratios.add(new NaverDatalabDevice.GroupRatio(d.group, d.ratio));
    }
    return new NaverDatalabDevice(categoryId, ratios);
  }

  private List<NaverDatalabClient.DistributionData> firstDistribution(NaverDatalabClient.DistributionResponse resp) {
    if (resp == null || resp.results == null || resp.results.isEmpty() || resp.results.get(0) == null) {
      return List.of();
    }
    var r = resp.results.get(0);
    return r.data == null ? List.of() : r.data;
  }

  private List<NaverTrendPoint> toTrendPoints(NaverDatalabClient.KeywordsTrendResponse resp) {
    if (resp == null || resp.results == null || resp.results.isEmpty() || resp.results.get(0) == null) {
      return List.of();
    }
    var r = resp.results.get(0);
    if (r.data == null) {
      return List.of();
    }
    List<NaverTrendPoint> out = new ArrayList<>();
    for (var p : r.data) {
      out.add(new NaverTrendPoint(p.period, p.ratio));
    }
    return out;
  }

  private Map<String, Double> toWeekdayRatio(NaverDatalabClient.KeywordsTrendResponse resp) {
    // Aggregate daily time series by weekday, then normalize to percentage
    if (resp == null || resp.results == null || resp.results.isEmpty() || resp.results.get(0) == null) {
      return Map.of();
    }
    var r = resp.results.get(0);
    if (r.data == null) {
      return Map.of();
    }

    Map<DayOfWeek, Double> sums = new LinkedHashMap<>();
    for (DayOfWeek d : DayOfWeek.values()) {
      sums.put(d, 0.0);
    }

    for (var p : r.data) {
      if (p == null || p.period == null || p.ratio == null) {
        continue;
      }
      try {
        LocalDate day = LocalDate.parse(p.period);
        sums.compute(day.getDayOfWeek(), (k, v) -> v + p.ratio);
      } catch (Exception ignored) {
      }
    }

    double total = sums.values().stream().mapToDouble(Double::doubleValue).sum();
    if (total <= 0.0) {
      return Map.of();
    }

    Map<String, Double> out = new LinkedHashMap<>();
    for (var e : sums.entrySet()) {
      String key = e.getKey().name().substring(0, 3).toUpperCase(Locale.ROOT);
      out.put(key, (e.getValue() / total) * 100.0);
    }
    return out;
  }
}
