package com.medoq.backend.dto.auth;

import jakarta.validation.constraints.*;

/**
 * Creates a PHARMACY_OWNER account + pharmacy in one shot.
 * The pharmacy is created with PENDING_APPROVAL status.
 */
public record RegisterPharmacyRequest(

    // ── Owner credentials ────────────────────────────────────────
    @NotBlank
    @Pattern(regexp = "\\+221[0-9]{9}", message = "Phone must be in Senegalese format +221XXXXXXXXX")
    String phone,

    @NotBlank @Size(min = 2, max = 100) String firstName,
    @NotBlank @Size(min = 2, max = 100) String lastName,

    @Email String email,

    @NotBlank @Size(min = 8, max = 128) String password,

    // ── Pharmacy information ──────────────────────────────────────
    @NotBlank(message = "Pharmacy name is required")
    String pharmacyName,

    @NotBlank(message = "License number is required")
    String licenseNumber,

    @NotBlank
    @Pattern(regexp = "\\+221[0-9]{9}", message = "Pharmacy phone must be in Senegalese format +221XXXXXXXXX")
    String pharmacyPhone,

    @Email String pharmacyEmail,

    @NotBlank(message = "Address is required")
    String address,

    @NotBlank(message = "City is required")
    String city,

    @NotBlank(message = "Region is required")
    String region,

    Double latitude,
    Double longitude
) {}
