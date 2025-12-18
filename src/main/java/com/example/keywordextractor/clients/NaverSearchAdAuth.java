package com.example.keywordextractor.clients;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class NaverSearchAdAuth {
  private NaverSearchAdAuth() {}

  public static String signature(String timestampMs, String method, String pathWithQuery, String secret) {
    try {
      String message = timestampMs + "." + method + "." + pathWithQuery;
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] raw = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(raw);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to sign Naver SearchAd request", e);
    }
  }
}
