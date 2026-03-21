package com.medoq.backend.dto.auth;

import com.medoq.backend.entity.User;

import java.time.Instant;
import java.util.UUID;

public record UserInfoDto(
    UUID id,
    String phone,
    String email,
    String firstName,
    String lastName,
    User.Role role,
    User.Status status,
    String avatarUrl,
    Instant createdAt
) {
    public static UserInfoDto from(User user) {
        return new UserInfoDto(
            user.getId(),
            user.getPhone(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getRole(),
            user.getStatus(),
            user.getAvatarUrl(),
            user.getCreatedAt()
        );
    }
}
