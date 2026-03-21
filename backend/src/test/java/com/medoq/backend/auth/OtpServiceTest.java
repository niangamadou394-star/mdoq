package com.medoq.backend.auth;

import com.medoq.backend.exception.BusinessException;
import com.medoq.backend.service.OtpService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OtpServiceTest {

    @Mock StringRedisTemplate         stringRedisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    OtpService otpService;

    private static final String PHONE = "+221771234567";
    private static final String KEY   = "medoq:otp:" + PHONE;

    @BeforeEach
    void setUp() {
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        otpService = new OtpService(stringRedisTemplate);
    }

    @Test
    void generate_storesCodeInRedis() {
        String code = otpService.generate(PHONE);
        assertThat(code).matches("\\d{6}");
        verify(valueOps).set(eq(KEY), contains(code + ":0"), eq(Duration.ofMinutes(10)));
    }

    @Test
    void validate_correctCode_succeeds() {
        when(valueOps.get(KEY)).thenReturn("123456:0");
        // Should not throw
        assertThatCode(() -> otpService.validate(PHONE, "123456")).doesNotThrowAnyException();
        verify(stringRedisTemplate).delete(KEY);
    }

    @Test
    void validate_wrongCode_incrementsAttempts() {
        when(valueOps.get(KEY)).thenReturn("123456:0");
        when(stringRedisTemplate.getExpire(KEY)).thenReturn(500L);

        assertThatThrownBy(() -> otpService.validate(PHONE, "000000"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("2 attempt(s) remaining");

        verify(valueOps).set(eq(KEY), eq("123456:1"), any(Duration.class));
    }

    @Test
    void validate_expiredOtp_throws() {
        when(valueOps.get(KEY)).thenReturn(null);
        assertThatThrownBy(() -> otpService.validate(PHONE, "123456"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expired");
    }

    @Test
    void validate_maxAttempts_deletesKey() {
        when(valueOps.get(KEY)).thenReturn("123456:3");
        assertThatThrownBy(() -> otpService.validate(PHONE, "000000"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("too many wrong attempts");
        verify(stringRedisTemplate).delete(KEY);
    }
}
