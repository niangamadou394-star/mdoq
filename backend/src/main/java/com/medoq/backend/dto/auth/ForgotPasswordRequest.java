package com.medoq.backend.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ForgotPasswordRequest(
    @NotBlank
    @Pattern(regexp = "\\+221[0-9]{9}", message = "Phone must be in Senegalese format +221XXXXXXXXX")
    String phone
) {}
