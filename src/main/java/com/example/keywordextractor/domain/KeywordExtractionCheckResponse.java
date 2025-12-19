package com.example.keywordextractor.domain;

public record KeywordExtractionCheckResponse(
    String sessionId,
    int totalKeywords,
    int doneCount,
    int failedCount,
    SessionStatus status,
    boolean allEnriched) {}
