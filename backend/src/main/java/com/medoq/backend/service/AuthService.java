package com.medoq.backend.service;

import com.medoq.backend.dto.auth.*;
import com.medoq.backend.entity.Pharmacy;
import com.medoq.backend.entity.PharmacyUser;
import com.medoq.backend.entity.User;
import com.medoq.backend.exception.BusinessException;
import com.medoq.backend.exception.ResourceNotFoundException;
import com.medoq.backend.repository.PharmacyRepository;
import com.medoq.backend.repository.PharmacyUserRepository;
import com.medoq.backend.repository.UserRepository;
import com.medoq.backend.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository        userRepository;
    private final PharmacyRepository    pharmacyRepository;
    private final PharmacyUserRepository pharmacyUserRepository;
    private final JwtService            jwtService;
    private final PasswordEncoder       passwordEncoder;
    private final OtpService            otpService;
    private final SmsService            smsService;
    private final AccountLockService    accountLockService;

    // ── Register ──────────────────────────────────────────────────

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByPhone(req.phone())) {
            throw new BusinessException("Phone number already registered.");
        }
        if (req.email() != null && !req.email().isBlank()
                && userRepository.existsByEmail(req.email())) {
            throw new BusinessException("Email address already registered.");
        }

        User user = User.builder()
                .phone(req.phone())
                .email(req.email())
                .firstName(req.firstName())
                .lastName(req.lastName())
                .passwordHash(passwordEncoder.encode(req.password()))
                .role(User.Role.CUSTOMER)
                .status(User.Status.ACTIVE)
                .build();

        user = userRepository.save(user);
        log.info("New customer registered: {}", user.getId());

        return buildAuthResponse(user);
    }

    // ── Login ─────────────────────────────────────────────────────

    @Transactional
    public AuthResponse login(LoginRequest req) {
        // 1 — Check account lock
        if (accountLockService.isLocked(req.phone())) {
            long remaining = accountLockService.lockRemainingSeconds(req.phone());
            throw new BusinessException(
                "Account temporarily locked. Try again in " + remaining + " seconds.");
        }

        // 2 — Find user
        User user = userRepository.findByPhone(req.phone())
                .orElseThrow(() -> {
                    accountLockService.recordFailure(req.phone());
                    return new BusinessException("Invalid phone number or password.");
                });

        // 3 — Verify password
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            accountLockService.recordFailure(req.phone());
            throw new BusinessException("Invalid phone number or password.");
        }

        // 4 — Check user status
        if (user.getStatus() == User.Status.SUSPENDED) {
            throw new BusinessException("Your account has been suspended. Please contact support.");
        }
        if (user.getStatus() == User.Status.INACTIVE) {
            throw new BusinessException("Your account is inactive. Please contact support.");
        }

        // 5 — Success
        accountLockService.resetFailures(req.phone());
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        log.info("User logged in: {}", user.getId());
        return buildAuthResponse(user);
    }

    // ── Refresh ───────────────────────────────────────────────────

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest req) {
        String token = req.refreshToken();

        if (!jwtService.isTokenValid(token)) {
            throw new BusinessException("Invalid or expired refresh token.");
        }

        UUID userId = UUID.fromString(jwtService.extractUserId(token));
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        // Verify token matches stored refresh token
        if (!token.equals(user.getRefreshToken())) {
            throw new BusinessException("Refresh token has been revoked.");
        }

        log.debug("Token refreshed for user: {}", userId);
        return buildAuthResponse(user);
    }

    // ── Logout ────────────────────────────────────────────────────

    @Transactional
    public void logout(String accessToken, UUID userId) {
        // 1 — Blacklist the access token
        jwtService.blacklist(accessToken);

        // 2 — Clear stored refresh token
        userRepository.updateRefreshToken(userId, null);

        log.info("User logged out: {}", userId);
    }

    // ── Forgot password (OTP via SMS) ─────────────────────────────

    public void forgotPassword(ForgotPasswordRequest req) {
        // Always respond with success to avoid phone enumeration
        boolean exists = userRepository.existsByPhone(req.phone());
        if (!exists) {
            log.debug("Forgot-password requested for unknown phone: {}", req.phone());
            return;
        }

        String otp = otpService.generate(req.phone());
        smsService.send(req.phone(),
            "Votre code de réinitialisation Medoq est : " + otp +
            ". Valable 10 minutes. Ne le partagez pas.");

        log.info("Password reset OTP sent to: {}", req.phone());
    }

    // ── Reset password ────────────────────────────────────────────

    @Transactional
    public void resetPassword(ResetPasswordRequest req) {
        // Validate OTP (throws BusinessException on failure)
        otpService.validate(req.phone(), req.otp());

        User user = userRepository.findByPhone(req.phone())
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        String newHash = passwordEncoder.encode(req.newPassword());
        userRepository.updatePasswordByPhone(req.phone(), newHash);

        // Invalidate any existing refresh token
        userRepository.updateRefreshToken(user.getId(), null);

        log.info("Password reset for user: {}", user.getId());
    }

    // ── Register pharmacy ─────────────────────────────────────────

    @Transactional
    public AuthResponse registerPharmacy(RegisterPharmacyRequest req) {
        // Check uniqueness
        if (userRepository.existsByPhone(req.phone())) {
            throw new BusinessException("Phone number already registered.");
        }
        if (req.email() != null && !req.email().isBlank()
                && userRepository.existsByEmail(req.email())) {
            throw new BusinessException("Email address already registered.");
        }
        if (pharmacyRepository.existsByLicenseNumber(req.licenseNumber())) {
            throw new BusinessException("License number already registered.");
        }

        // 1 — Create pharmacy owner
        User owner = User.builder()
                .phone(req.phone())
                .email(req.email())
                .firstName(req.firstName())
                .lastName(req.lastName())
                .passwordHash(passwordEncoder.encode(req.password()))
                .role(User.Role.PHARMACY_OWNER)
                .status(User.Status.ACTIVE)
                .build();
        owner = userRepository.save(owner);

        // 2 — Create pharmacy (PENDING_APPROVAL until admin validates)
        Pharmacy pharmacy = Pharmacy.builder()
                .name(req.pharmacyName())
                .licenseNumber(req.licenseNumber())
                .phone(req.pharmacyPhone())
                .email(req.pharmacyEmail())
                .address(req.address())
                .city(req.city())
                .region(req.region())
                .latitude(req.latitude() != null ? BigDecimal.valueOf(req.latitude()) : null)
                .longitude(req.longitude() != null ? BigDecimal.valueOf(req.longitude()) : null)
                .status(Pharmacy.Status.PENDING_APPROVAL)
                .owner(owner)
                .build();
        pharmacy = pharmacyRepository.save(pharmacy);

        // 3 — Link owner as staff member
        PharmacyUser pu = PharmacyUser.builder()
                .pharmacy(pharmacy)
                .user(owner)
                .role(User.Role.PHARMACY_OWNER)
                .isActive(true)
                .build();
        pharmacyUserRepository.save(pu);

        log.info("Pharmacy registered: {} by owner: {}", pharmacy.getId(), owner.getId());
        return buildAuthResponse(owner);
    }

    // ── Internal helpers ──────────────────────────────────────────

    private AuthResponse buildAuthResponse(User user) {
        String accessToken  = jwtService.generateAccessToken(
                user.getId(), user.getPhone(), user.getRole().name());
        String refreshToken = jwtService.generateRefreshToken(user.getId());

        // Persist refresh token
        userRepository.updateRefreshToken(user.getId(), refreshToken);

        return AuthResponse.of(
                accessToken,
                refreshToken,
                jwtService.getExpirationMs(),
                UserInfoDto.from(user));
    }
}
