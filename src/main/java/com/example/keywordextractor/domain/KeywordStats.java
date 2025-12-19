package com.example.keywordextractor.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record KeywordStats(
    Long monthlyPcQcCnt,
    Long monthlyMobileQcCnt,
    Long monthlyPcClickCnt,
    Long monthlyMobileClickCnt,
    Double monthlyPcCtr,
    Double monthlyMobileCtr,
    String compIdx,
    Double plAvgDepth) {}
