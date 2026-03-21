package com.medoq.backend.payment;

import com.medoq.backend.util.AesEncryptionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

class AesEncryptionUtilTest {

    // 32-byte key base64-encoded
    private static final String KEY_B64 =
        Base64.getEncoder().encodeToString("medoq_test_key_32chars_paddedXXX".getBytes());

    AesEncryptionUtil util;

    @BeforeEach
    void setUp() { util = new AesEncryptionUtil(KEY_B64); }

    @Test
    void encrypt_decrypt_roundTrip() {
        String plaintext = "wave_session_abc123";
        String encrypted = util.encrypt(plaintext);
        assertThat(encrypted).isNotEqualTo(plaintext);
        assertThat(util.decrypt(encrypted)).isEqualTo(plaintext);
    }

    @RepeatedTest(5)
    void encrypt_sameInput_differentCipherText() {
        String plaintext = "orange_token_xyz";
        // Different IV each time → different ciphertext
        String enc1 = util.encrypt(plaintext);
        String enc2 = util.encrypt(plaintext);
        assertThat(enc1).isNotEqualTo(enc2);
        // But both decrypt to same value
        assertThat(util.decrypt(enc1)).isEqualTo(plaintext);
        assertThat(util.decrypt(enc2)).isEqualTo(plaintext);
    }

    @Test
    void encrypt_null_returnsNull() {
        assertThat(util.encrypt(null)).isNull();
        assertThat(util.decrypt(null)).isNull();
    }

    @Test
    void decrypt_tamperedData_throws() {
        String encrypted = util.encrypt("secret_data");
        // Tamper with the ciphertext
        String tampered = encrypted.substring(0, encrypted.length() - 4) + "XXXX";
        assertThatThrownBy(() -> util.decrypt(tampered))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    void invalidKeyLength_throws() {
        String shortKey = Base64.getEncoder().encodeToString("short_key".getBytes());
        assertThatThrownBy(() -> new AesEncryptionUtil(shortKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("32 bytes");
    }
}
