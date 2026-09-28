package com.example.haas.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Encrypts user IDs at rest. Passwords are never encrypted — they are one-way BCrypt hashed
 * elsewhere. A user ID needs to be both recoverable (to show it back to the user) and searchable
 * (to log in), so it is stored twice:
 * <ul>
 *   <li>{@link #encrypt} — AES-256-GCM with a random IV: recoverable, but a different ciphertext every time.</li>
 *   <li>{@link #lookupKey} — HMAC-SHA256: deterministic, so it can be a unique indexed column.</li>
 * </ul>
 * Both keys are derived from a single master key so deployments only manage one secret.
 */
@Component
public class CredentialCipher {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec aesKey;
    private final SecretKeySpec lookupKey;
    private final SecureRandom random = new SecureRandom();

    public CredentialCipher(@Value("${haas.crypto.master-key}") String masterKeyBase64) {
        byte[] master = Base64.getDecoder().decode(masterKeyBase64);
        if (master.length != 32) {
            throw new IllegalStateException("haas.crypto.master-key must decode to exactly 32 bytes");
        }
        this.aesKey = new SecretKeySpec(hmac(master, "haas/aes-gcm/user-id"), "AES");
        this.lookupKey = new SecretKeySpec(hmac(master, "haas/hmac/user-id-lookup"), "HmacSHA256");
    }

    /** Deterministic, case-insensitive fingerprint of a user ID, safe to index and compare. */
    public String lookupKey(String userId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(lookupKey);
            byte[] digest = mac.doFinal(userId.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public String decrypt(String encoded) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(Base64.getDecoder().decode(encoded));
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Could not decrypt user ID — wrong master key?", e);
        }
    }

    private static byte[] hmac(byte[] key, String label) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(label.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
