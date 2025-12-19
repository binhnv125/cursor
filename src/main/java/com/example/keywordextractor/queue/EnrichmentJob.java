package com.example.keywordextractor.queue;

import java.util.List;

public record EnrichmentJob(String sessionId, List<String> keywords) {}
