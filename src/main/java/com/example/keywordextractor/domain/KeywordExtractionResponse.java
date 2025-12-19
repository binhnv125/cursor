package com.example.keywordextractor.domain;

import java.util.List;

public record KeywordExtractionResponse(
    String sessionId,
    int totalKeywords,
    int doneCount,
    int failedCount,
    SessionStatus status,
    List<KeywordResult> keywords) {}
