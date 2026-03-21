package com.medoq.backend.auth;

import com.medoq.backend.dto.auth.*;
import com.medoq.backend.entity.User;
import com.medoq.backend.exception.BusinessException;
import com.medoq.backend.repository.PharmacyRepository;
import com.medoq.backend.repository.PharmacyUserRepository;
import com.medoq.backend.repository.UserRepository;
import com.medoq.backend.security.JwtService;
import com.medoq.backend.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository         userRepository;
    @Mock PharmacyRepository     pharmacyRepository;
    @Mock PharmacyUserRepository pharmacyUserRepository;
    @Mock JwtService             jwtService;
    @Mock PasswordEncoder        passwordEncoder;
    @Mock OtpService             otpService;
    @Mock SmsService             smsService;
    @Mock AccountLockService     accountLockService;

    @InjectMocks AuthService authService;

    private static final String PHONE    = "+221771234567";
    private static final String PASSWORD = "Test@1234";

    private User buildUser() {
        return User.builder()
                .id(UUID.randomUUID())
                .phone(PHONE)
                .firstName("Amadou")
                .lastName("Diallo")
                .passwordHash("hashed")
                .role(User.Role.CUSTOMER)
                .status(User.Status.ACTIVE)
                .build();
    }

    @BeforeEach
    void stubJwt() {
        when(jwtService.generateAccessToken(any(), any(), any())).thenReturn("access-token");
        when(jwtService.generateRefreshToken(any())).thenReturn("refresh-token");
        when(jwtService.getExpirationMs()).thenReturn(86_400_000L);
        when(userRepository.updateRefreshToken(any(), any())).thenReturn(1);
    }

    // ── register ──────────────────────────────────────────────────

    @Test
    void register_success() {
        when(userRepository.existsByPhone(PHONE)).thenReturn(false);
        when(passwordEncoder.encode(PASSWORD)).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        var req = new RegisterRequest(PHONE, "Amadou", "Diallo", null, PASSWORD);
        AuthResponse resp = authService.register(req);

        assertThat(resp.accessToken()).isEqualTo("access-token");
        assertThat(resp.tokenType()).isEqualTo("Bearer");
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_duplicatePhone_throws() {
        when(userRepository.existsByPhone(PHONE)).thenReturn(true);
        var req = new RegisterRequest(PHONE, "Amadou", "Diallo", null, PASSWORD);
        assertThatThrownBy(() -> authService.register(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already registered");
    }

    // ── login ─────────────────────────────────────────────────────

    @Test
    void login_success() {
        User user = buildUser();
        when(accountLockService.isLocked(PHONE)).thenReturn(false);
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, "hashed")).thenReturn(true);
        when(userRepository.save(any(User.class))).thenReturn(user);

        AuthResponse resp = authService.login(new LoginRequest(PHONE, PASSWORD));

        assertThat(resp.user().phone()).isEqualTo(PHONE);
        verify(accountLockService).resetFailures(PHONE);
    }

    @Test
    void login_lockedAccount_throws() {
        when(accountLockService.isLocked(PHONE)).thenReturn(true);
        when(accountLockService.lockRemainingSeconds(PHONE)).thenReturn(1200L);

        assertThatThrownBy(() -> authService.login(new LoginRequest(PHONE, PASSWORD)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("locked");
    }

    @Test
    void login_wrongPassword_recordsFailure() {
        User user = buildUser();
        when(accountLockService.isLocked(PHONE)).thenReturn(false);
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches(PASSWORD, "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest(PHONE, PASSWORD)))
                .isInstanceOf(BusinessException.class);
        verify(accountLockService).recordFailure(PHONE);
    }

    // ── logout ────────────────────────────────────────────────────

    @Test
    void logout_blacklistsToken() {
        UUID userId = UUID.randomUUID();
        when(userRepository.updateRefreshToken(userId, null)).thenReturn(1);

        authService.logout("some.jwt.token", userId);

        verify(jwtService).blacklist("some.jwt.token");
        verify(userRepository).updateRefreshToken(userId, null);
    }

    // ── forgotPassword ────────────────────────────────────────────

    @Test
    void forgotPassword_unknownPhone_noOtpSent() {
        when(userRepository.existsByPhone(PHONE)).thenReturn(false);
        authService.forgotPassword(new ForgotPasswordRequest(PHONE));
        verifyNoInteractions(otpService, smsService);
    }

    @Test
    void forgotPassword_knownPhone_sendsOtp() {
        when(userRepository.existsByPhone(PHONE)).thenReturn(true);
        when(otpService.generate(PHONE)).thenReturn("123456");

        authService.forgotPassword(new ForgotPasswordRequest(PHONE));

        verify(smsService).send(eq(PHONE), contains("123456"));
    }

    // ── resetPassword ─────────────────────────────────────────────

    @Test
    void resetPassword_validOtp_updatesPassword() {
        User user = buildUser();
        when(userRepository.findByPhone(PHONE)).thenReturn(Optional.of(user));
        when(passwordEncoder.encode("NewPass@1")).thenReturn("new-hashed");
        when(userRepository.updatePasswordByPhone(PHONE, "new-hashed")).thenReturn(1);
        when(userRepository.updateRefreshToken(user.getId(), null)).thenReturn(1);

        authService.resetPassword(new ResetPasswordRequest(PHONE, "123456", "NewPass@1"));

        verify(otpService).validate(PHONE, "123456");
        verify(userRepository).updatePasswordByPhone(PHONE, "new-hashed");
        verify(userRepository).updateRefreshToken(user.getId(), null);
    }
}
