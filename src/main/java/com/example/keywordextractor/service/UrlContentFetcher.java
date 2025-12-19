package com.example.keywordextractor.service;

import java.time.Duration;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
public class UrlContentFetcher {

  public Mono<String> fetchText(String url) {
    if (url == null || url.isBlank()) {
      return Mono.just("");
    }

    return Mono.fromCallable(() -> fetch(url))
        .subscribeOn(Schedulers.boundedElastic())
        .timeout(Duration.ofSeconds(8))
        .onErrorReturn("");
  }

  private String fetch(String url) throws Exception {
    Document doc = Jsoup.connect(url)
        .userAgent("Mozilla/5.0 (keyword-extractor)")
        .timeout(8_000)
        .get();

    String text = doc.body() != null ? doc.body().text() : "";
    return trim(text, 2000);
  }

  private String trim(String s, int maxChars) {
    if (s == null) return "";
    String v = s.trim();
    if (v.length() <= maxChars) return v;
    return v.substring(0, maxChars);
  }
}
