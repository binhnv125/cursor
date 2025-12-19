package com.example.keywordextractor.service;

import com.example.keywordextractor.clients.NaverSearchAdClient;
import com.example.keywordextractor.domain.KeywordBid;
import com.example.keywordextractor.domain.KeywordResult;
import com.example.keywordextractor.domain.KeywordStats;
import com.example.keywordextractor.domain.KeywordType;
import com.example.keywordextractor.session.RedisSessionRepository;
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
  private final RedisSessionRepository sessions;

  public NaverEnrichmentService(NaverSearchAdClient naver, RedisSessionRepository sessions) {
    this.naver = naver;
    this.sessions = sessions;
  }

  public Mono<List<KeywordResult>> enrichAndPersist(
      String sessionId, List<String> keywords, int concurrency) {
    if (keywords == null || keywords.isEmpty()) {
      return Mono.just(List.of());
    }

    List<List<String>> chunks = chunk(keywords, 30);

    return Flux.fromIterable(chunks)
        .flatMap(
            chunk ->
                enrichChunk(sessionId, chunk)
                    .onErrorResume(e -> markChunkFailed(sessionId, chunk, e)),
            concurrency)
        .flatMapIterable(x -> x)
        .collectList();
  }

  private Mono<List<KeywordResult>> enrichChunk(String sessionId, List<String> chunk) {
    return Mono.zip(
            naver.fetchKeywordStatsBatch(chunk).onErrorReturn(Map.of()),
            naver.fetchBidBatch(chunk).onErrorReturn(Map.of()))
        .flatMap(
            tuple -> {
              Map<String, KeywordStats> statsByKeyword = tuple.getT1();
              Map<String, KeywordBid> bidsByKeyword = tuple.getT2();
              List<KeywordResult> out = new ArrayList<>();
              for (String k : chunk) {
                KeywordType type = KeywordClassifier.classify(k);
                KeywordStats stats = statsByKeyword.get(k);
                KeywordBid bid = bidsByKeyword.get(k);
                out.add(KeywordResult.done(k, type, stats, bid));
              }

              return Flux.fromIterable(out)
                  .flatMap(r -> sessions.markDone(sessionId, r).thenReturn(r), 8)
                  .collectList();
            })
        .onErrorResume(e -> markChunkFailed(sessionId, chunk, e));
  }

  private Mono<List<KeywordResult>> markChunkFailed(
      String sessionId, List<String> chunk, Throwable e) {
    return Flux.fromIterable(chunk)
        .flatMap(
            k -> {
              KeywordType type = KeywordClassifier.classify(k);
              return sessions
                  .markFailed(sessionId, KeywordResult.failed(k, type, e.getMessage()))
                  .thenReturn(k);
            },
            8)
        .then(Mono.just(Collections.emptyList()));
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
