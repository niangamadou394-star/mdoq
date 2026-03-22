package com.medoq.backend.dto.admin;

import com.medoq.backend.entity.User;

import java.time.Instant;
import java.util.UUID;

public record AdminUserDto(
        UUID        id,
        String      firstName,
        String      lastName,
        String      phone,
        String      email,
        User.Role   role,
        User.Status status,
        Instant     createdAt,
        Instant     updatedAt
) {
    public static AdminUserDto from(User u) {
        return new AdminUserDto(
            u.getId(), u.getFirstName(), u.getLastName(),
            u.getPhone(), u.getEmail(),
            u.getRole(), u.getStatus(),
            u.getCreatedAt(), u.getUpdatedAt()
        );
    }
}
