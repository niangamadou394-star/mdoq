package com.medoq.backend.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * Stores FCM device tokens for push notification delivery.
 *
 * Each user can have multiple tokens (multiple devices / reinstalls).
 * Stale tokens (where FCM returns UNREGISTERED) are deleted automatically
 * by FcmService after a failed send.
 */
@Entity
@Table(
    name = "device_tokens",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_device_token",
        columnNames = "token"
    )
)
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DeviceToken {

    public enum Platform { ANDROID, IOS }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** FCM registration token — unique, rotates on app reinstall. */
    @Column(nullable = false, length = 512)
    private String token;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Platform platform;

    /** App version — helps diagnose token staleness. */
    @Column(name = "app_version", length = 20)
    private String appVersion;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    /** Last time this token successfully delivered a message. */
    @Column(name = "last_used_at")
    private Instant lastUsedAt;
}
