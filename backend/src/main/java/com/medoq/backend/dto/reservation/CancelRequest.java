package com.medoq.backend.dto.reservation;

import jakarta.validation.constraints.Size;

public record CancelRequest(
    @Size(max = 500, message = "Reason cannot exceed 500 characters")
    String reason
) {
    public CancelRequest() { this(null); }
}
