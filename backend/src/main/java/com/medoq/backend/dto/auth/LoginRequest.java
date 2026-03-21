package com.medoq.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record LoginRequest(

    @NotBlank(message = "Phone number is required")
    @Pattern(
        regexp = "\\+221[0-9]{9}",
        message = "Phone must be in Senegalese format +221XXXXXXXXX"
    )
    String phone,

    @NotBlank(message = "Password is required")
    String password
) {}
