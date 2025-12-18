package com.example.keywordextractor.service;

import java.util.regex.Pattern;

public final class KeywordSanitizer {
  private KeywordSanitizer() {}

  // Keep Hangul, latin letters, numbers, spaces.
  private static final Pattern ALLOWED = Pattern.compile("[^0-9A-Za-z가-힣\s]");
  private static final Pattern MULTI_SPACE = Pattern.compile("\\s{2,}");

  public static String sanitize(String raw) {
    if (raw == null) {
      return null;
    }
    String v = raw;

    v = v.replace('"', ' ');
    v = v.replace('\'', ' ');
    v = v.replace('`', ' ');

    v = ALLOWED.matcher(v).replaceAll(" ");
    v = MULTI_SPACE.matcher(v).replaceAll(" ");
    v = v.trim();

    if (v.isBlank()) {
      return null;
    }

    // Limit to 15 chars (including spaces).
    if (v.length() > 15) {
      return null;
    }

    return v;
  }
}
