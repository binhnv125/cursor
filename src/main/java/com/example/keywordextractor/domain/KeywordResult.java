package com.example.keywordextractor.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record KeywordResult(
    String keyword,
    KeywordType type,
    KeywordStats stats,
    KeywordBid bid,
    KeywordResultStatus status,
    String error) {

  public static KeywordResult pending(String keyword, KeywordType type) {
    return new KeywordResult(keyword, type, null, null, KeywordResultStatus.PENDING, null);
  }

  public static KeywordResult done(String keyword, KeywordType type, KeywordStats stats, KeywordBid bid) {
    return new KeywordResult(keyword, type, stats, bid, KeywordResultStatus.DONE, null);
  }

  public static KeywordResult failed(String keyword, KeywordType type, String error) {
    return new KeywordResult(keyword, type, null, null, KeywordResultStatus.FAILED, error);
  }
}
