package com.better.modules.keyword.service.model;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;

@Builder
public record DatalabAggregateResponse(
        JsonNode categoryKeywordsTrend,
        JsonNode keywordDeviceTrend,
        JsonNode keywordGenderTrend,
        JsonNode keywordAgeTrend,
        JsonNode searchAdAveragePositionBidPc,
        JsonNode searchAdAveragePositionBidMobile
) {
}

