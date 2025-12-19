package com.example.keywordextractor.service;

import com.example.keywordextractor.domain.KeywordType;
import java.util.List;

public final class KeywordClassifier {
  private KeywordClassifier() {}

  private static final List<String> SEASON =
      List.of("봄", "여름", "가을", "겨울", "연말", "명절", "추석", "설날", "크리스마스", "블랙프라이데이");

  private static final List<String> ADJECTIVES =
      List.of(
          "프리미엄",
          "촉촉한",
          "고급",
          "저자극",
          "순한",
          "강력",
          "가성비",
          "인기",
          "추천",
          "베스트",
          "정품",
          "빠른",
          "특가");

  private static final List<String> LOCATIONS =
      List.of(
          "서울",
          "부산",
          "대구",
          "인천",
          "광주",
          "대전",
          "울산",
          "세종",
          "경기",
          "강원",
          "충북",
          "충남",
          "전북",
          "전남",
          "경북",
          "경남",
          "제주");

  private static final List<String> BRANDS =
      List.of(
          "삼성",
          "LG",
          "애플",
          "나이키",
          "아디다스",
          "쿠팡",
          "네이버",
          "카카오",
          "아모레",
          "라네즈",
          "이니스프리");

  public static KeywordType classify(String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return KeywordType.GENERAL;
    }

    for (String s : SEASON) {
      if (keyword.contains(s)) {
        return KeywordType.SEASON;
      }
    }

    for (String s : LOCATIONS) {
      if (keyword.contains(s)) {
        return KeywordType.LOCATION;
      }
    }

    for (String s : BRANDS) {
      if (keyword.contains(s)) {
        return KeywordType.BRAND;
      }
    }

    for (String s : ADJECTIVES) {
      if (keyword.contains(s)) {
        return KeywordType.ADJECTIVE;
      }
    }

    int words = keyword.trim().split("\\s+").length;
    if (words >= 3 && words <= 6) {
      return KeywordType.LONGTAIL;
    }

    return KeywordType.GENERAL;
  }
}
