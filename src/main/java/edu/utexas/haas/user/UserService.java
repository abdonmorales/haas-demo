package edu.utexas.haas.user;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import edu.utexas.haas.security.CredentialCipher;
import edu.utexas.haas.web.ApiException;

/** Account lifecycle. The only class that knows how user IDs and passwords are protected. */
@Service
public class UserService {

    private static final Pattern USER_ID = Pattern.compile("[A-Za-z0-9_.-]{3,32}");
    private static final int MIN_PASSWORD = 8;
    private static final int MAX_PASSWORD = 64; // BCrypt ignores bytes past 72

    private final UserRepository users;
    private final CredentialCipher cipher;
    private final PasswordEncoder passwords;
    /** Compared against when the user ID doesn't exist, so a miss takes as long as a wrong password. */
    private final String dummyHash;

    public UserService(UserRepository users, CredentialCipher cipher, PasswordEncoder passwords) {
        this.users = users;
        this.cipher = cipher;
        this.passwords = passwords;
        this.dummyHash = passwords.encode("timing-equalizer");
    }

    public User register(String userId, String password, String confirmPassword, boolean demo) {
        validateUserId(userId);
        validateNewPassword(password, confirmPassword);
        try {
            // The unique index on userIdLookup is what actually guarantees no duplicates, even under races.
            return users.insert(new User(cipher.lookupKey(userId), cipher.encrypt(userId), passwords.encode(password), demo));
        } catch (DuplicateKeyException e) {
            throw ApiException.conflict("That user ID is already taken.");
        }
    }

    public User authenticate(String userId, String password) {
        if (userId == null || password == null) {
            throw ApiException.unauthorized("Invalid user ID or password.");
        }
        var user = users.findByUserIdLookup(cipher.lookupKey(userId.trim()));
        String hash = user.map(User::getPasswordHash).orElse(dummyHash);
        if (!passwords.matches(password, hash) || user.isEmpty()) {
            throw ApiException.unauthorized("Invalid user ID or password.");
        }
        return user.get();
    }

    public void changePassword(String userId, String oldPassword, String newPassword, String confirmPassword) {
        User user = authenticate(userId, oldPassword);
        validateNewPassword(newPassword, confirmPassword);
        if (passwords.matches(newPassword, user.getPasswordHash())) {
            throw ApiException.badRequest("New password must differ from the old one.");
        }
        user.setPasswordHash(passwords.encode(newPassword));
        users.save(user);
    }

    public String displayUserId(String userPk) {
        return users.findById(userPk)
                .map(u -> cipher.decrypt(u.getUserIdCipher()))
                .orElseThrow(() -> ApiException.unauthorized("Session expired."));
    }

    public List<String> demoUserIds() {
        return users.findByDemoTrueOrderByCreatedAtAsc().stream()
                .map(u -> cipher.decrypt(u.getUserIdCipher()))
                .toList();
    }

    public String requireUserPk(String userId) {
        return users.findByUserIdLookup(cipher.lookupKey(userId))
                .map(User::getId)
                .orElseThrow(() -> ApiException.notFound("No user " + userId));
    }

    private static void validateUserId(String userId) {
        if (userId == null || !USER_ID.matcher(userId).matches()) {
            throw ApiException.badRequest("User ID must be 3-32 characters: letters, digits, '.', '_' or '-'.");
        }
    }

    private static void validateNewPassword(String password, String confirm) {
        if (password == null || password.length() < MIN_PASSWORD || password.length() > MAX_PASSWORD) {
            throw ApiException.badRequest("Password must be " + MIN_PASSWORD + "-" + MAX_PASSWORD + " characters.");
        }
        if (password.chars().anyMatch(Character::isWhitespace)) {
            throw ApiException.badRequest("Password cannot contain spaces.");
        }
        if (!password.equals(confirm)) {
            throw ApiException.badRequest("Passwords do not match.");
        }
    }
}
