package com.example.keywordextractor.detail;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record KeywordDetailResponse(
    String keyword,
    String categoryId,
    DateRange ageRange,
    DateRange genderRange,
    DateRange deviceRange,
    DateRange monthlyRange,
    DateRange weekdayRange,
    NaverSearchAdRankBid rankBid,
    NaverDatalabAge age,
    NaverDatalabGender gender,
    NaverDatalabDevice device,
    List<NaverTrendPoint> monthlyTrend,
    Map<String, Double> weekdayRatio) {

  public record DateRange(LocalDate startDate, LocalDate endDate) {}

  public record NaverTrendPoint(String period, Double ratio) {}

  public record NaverSearchAdRankBid(
      String source,
      List<RankBid> pc,
      List<RankBid> mobile) {
    public record RankBid(int rank, Integer bid) {}
  }

  public record NaverDatalabAge(String category, List<GroupRatio> ratios) {
    public record GroupRatio(String ageGroup, Double ratio) {}
  }

  public record NaverDatalabGender(String category, List<GroupRatio> ratios) {
    public record GroupRatio(String gender, Double ratio) {}
  }

  public record NaverDatalabDevice(String category, List<GroupRatio> ratios) {
    public record GroupRatio(String device, Double ratio) {}
  }
}
