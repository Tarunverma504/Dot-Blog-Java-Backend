package com.dotblog.auth.service;

import com.dotblog.auth.config.EncryptionProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Two-way encrypt/decrypt for userId (compatible with Node Cryptr usage in resend OTP).
 * Uses AES with key derived from secret (32 bytes for AES-256, or 16 for AES-128).
 */
@Service
public class EncryptionService {

    private static final String ALGORITHM = "AES";
    private final byte[] keyBytes;

    public EncryptionService(EncryptionProperties props) {
        String secret = props.getTwoWaySecret() != null ? props.getTwoWaySecret() : "";
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        this.keyBytes = raw.length >= 32 ? java.util.Arrays.copyOf(raw, 32)
                : raw.length >= 16 ? java.util.Arrays.copyOf(raw, 16) : java.util.Arrays.copyOf(raw, 16);
    }

    public String decrypt(String encrypted) {
        if (encrypted == null || encrypted.isBlank()) return encrypted;
        try {
            SecretKeySpec spec = new SecretKeySpec(keyBytes, ALGORITHM);
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, spec);
            byte[] decoded = Base64.getDecoder().decode(encrypted);
            byte[] decrypted = cipher.doFinal(decoded);
            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /** If decryption fails (e.g. client sent plain userId), return the input as-is. */
    public String decryptOrPlain(String value) {
        if (value == null || value.isBlank()) return value;
        String decrypted = decrypt(value);
        return decrypted != null ? decrypted : value;
    }
}
