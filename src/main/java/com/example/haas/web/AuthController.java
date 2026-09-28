package com.example.haas.web;

import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.example.haas.user.User;
import com.example.haas.user.UserService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService users;
    private final boolean userChooser;

    public AuthController(UserService users, @Value("${haas.demo.user-chooser:false}") boolean userChooser) {
        this.users = users;
        this.userChooser = userChooser;
    }

    /** Feeds the login page's "User Choose" dropdown with the simulated demo accounts. */
    @GetMapping("/demo-users")
    public List<String> demoUsers() {
        return userChooser ? users.demoUserIds() : List.of();
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> register(@RequestBody Requests.Register body) {
        users.register(body.userId(), body.password(), body.confirmPassword(), false);
        return Map.of("message", "Account created — you can sign in now.");
    }

    @PostMapping("/login")
    public Map<String, String> login(@RequestBody Requests.Login body, HttpServletRequest request) {
        User user = users.authenticate(body.userId(), body.password());
        SessionUser.signIn(request, user.getId());
        return Map.of("userId", users.displayUserId(user.getId()));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request) {
        SessionUser.signOut(request);
    }

    @GetMapping("/me")
    public Map<String, String> me(HttpServletRequest request) {
        return Map.of("userId", users.displayUserId(SessionUser.require(request)));
    }

    @PostMapping("/change-password")
    public Map<String, String> changePassword(@RequestBody Requests.ChangePassword body) {
        users.changePassword(body.userId(), body.oldPassword(), body.newPassword(), body.confirmPassword());
        return Map.of("message", "Password updated — sign in with your new password.");
    }
}
