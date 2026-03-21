package com.medoq.backend.dto.search;

import java.util.List;

public record SearchResponse(
    String query,
    int    total,
    List<MedicationSearchResultDto> results
) {}
