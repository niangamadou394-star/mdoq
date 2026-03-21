package com.medoq.backend.dto.notification;

import com.medoq.backend.entity.DeviceToken.Platform;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterTokenRequest {

    @NotBlank
    @Size(max = 512)
    private String token;

    @NotNull
    private Platform platform;

    @Size(max = 20)
    private String appVersion;
}
