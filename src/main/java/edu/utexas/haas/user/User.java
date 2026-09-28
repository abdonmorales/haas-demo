package edu.utexas.haas.user;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A user account. The plaintext user ID and password are never stored:
 * {@code userIdLookup} is an HMAC for finding the document (unique index),
 * {@code userIdCipher} is the AES-GCM encrypted display value, and {@code passwordHash} is BCrypt.
 */
@Document("users")
public class User {

    @Id
    private String id;

    private String userIdLookup;

    private String userIdCipher;

    private String passwordHash;

    private boolean demo;

    private Instant createdAt = Instant.now();

    protected User() {
    }

    public User(String userIdLookup, String userIdCipher, String passwordHash, boolean demo) {
        this.userIdLookup = userIdLookup;
        this.userIdCipher = userIdCipher;
        this.passwordHash = passwordHash;
        this.demo = demo;
    }

    public String getId() {
        return id;
    }

    public String getUserIdLookup() {
        return userIdLookup;
    }

    public String getUserIdCipher() {
        return userIdCipher;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public boolean isDemo() {
        return demo;
    }
}
