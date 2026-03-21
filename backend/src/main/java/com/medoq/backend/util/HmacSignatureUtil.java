package com.medoq.backend.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

/**
 * HMAC-SHA256 utility for verifying webhook signatures from Wave and Orange Money.
 *
 * Wave signature header:  X-Wave-Signature: sha256={hex_hmac}
 * Orange signature header: X-Orange-Signature: {hex_hmac}
 */
public final class HmacSignatureUtil {

    private HmacSignatureUtil() {}

    /**
     * Computes HMAC-SHA256 of {@code payload} using {@code secret} and returns
     * the lowercase hex-encoded result.
     */
    public static String computeHmac256(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception e) {
            throw new RuntimeException("HMAC computation failed", e);
        }
    }

    /**
     * Timing-safe comparison to prevent timing attacks.
     */
    public static boolean verify(String payload, String secret, String expectedSignature) {
        String computed = computeHmac256(payload, secret);
        // Strip "sha256=" prefix if present (Wave format)
        String expected = expectedSignature.startsWith("sha256=")
            ? expectedSignature.substring(7)
            : expectedSignature;
        return timingSafeEquals(computed, expected);
    }

    private static boolean timingSafeEquals(String a, String b) {
        if (a.length() != b.length()) return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
