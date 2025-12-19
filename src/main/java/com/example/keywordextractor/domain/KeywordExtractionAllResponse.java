package com.example.keywordextractor.domain;

import java.util.List;

public record KeywordExtractionAllResponse(
    String sessionId,
    int totalKeywords,
    int doneCount,
    int failedCount,
    SessionStatus status,
    List<KeywordResult> keywords) {}
