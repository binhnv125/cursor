package com.better.modules.keyword.service;

import com.better.modules.keyword.service.model.DatalabAggregateResponse;
import reactor.core.publisher.Mono;

public interface DatalabService {
    Mono<DatalabAggregateResponse> fetch(String keyword, String categoryId);
}

