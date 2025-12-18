package com.example.keywordextractor.service;

import com.example.keywordextractor.clients.NaverSearchAdClient;
import com.example.keywordextractor.domain.KeywordBid;
import com.example.keywordextractor.domain.KeywordExtractionSession;
import com.example.keywordextractor.domain.KeywordResult;
import com.example.keywordextractor.domain.KeywordStats;
import com.example.keywordextractor.domain.KeywordType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
public class NaverEnrichmentService {
  private final NaverSearchAdClient naver;

  public NaverEnrichmentService(NaverSearchAdClient naver) {
    this.naver = naver;
  }

  public Mono<List<KeywordResult>> enrichAndUpdate(
      KeywordExtractionSession session, List<String> keywords, int concurrency) {
    if (keywords == null || keywords.isEmpty()) {
      return Mono.just(List.of());
    }

    List<List<String>> chunks = chunk(keywords, 30);

    return Flux.fromIterable(chunks)
        .flatMap(chunk -> enrichChunk(session, chunk).onErrorResume(e -> markChunkFailed(session, chunk, e)), concurrency)
        .flatMapIterable(x -> x)
        .collectList();
  }

  private Mono<List<KeywordResult>> enrichChunk(KeywordExtractionSession session, List<String> chunk) {
    return Mono.zip(
            naver.fetchKeywordStatsBatch(chunk).onErrorReturn(Map.of()),
            naver.fetchBidBatch(chunk).onErrorReturn(Map.of()),
            (statsByKeyword, bidsByKeyword) -> {
              List<KeywordResult> out = new ArrayList<>();
              for (String k : chunk) {
                KeywordType type = typeOf(session, k);
                KeywordStats stats = statsByKeyword.get(k);
                KeywordBid bid = bidsByKeyword.get(k);
                KeywordResult result = KeywordResult.done(k, type, stats, bid);
                session.markDone(result);
                out.add(result);
              }
              return out;
            })
        .onErrorResume(e -> markChunkFailed(session, chunk, e));
  }

  private Mono<List<KeywordResult>> markChunkFailed(
      KeywordExtractionSession session, List<String> chunk, Throwable e) {
    for (String k : chunk) {
      KeywordType type = typeOf(session, k);
      session.markFailed(k, type, e.getMessage());
    }
    return Mono.just(Collections.emptyList());
  }

  private KeywordType typeOf(KeywordExtractionSession session, String keyword) {
    KeywordResult existing = session.resultsByKeyword().get(keyword);
    if (existing != null && existing.type() != null) {
      return existing.type();
    }
    return KeywordClassifier.classify(keyword);
  }

  private List<List<String>> chunk(List<String> all, int size) {
    if (all == null || all.isEmpty()) {
      return List.of();
    }
    List<List<String>> out = new ArrayList<>();
    for (int i = 0; i < all.size(); i += size) {
      out.add(all.subList(i, Math.min(all.size(), i + size)));
    }
    return out;
  }
}
