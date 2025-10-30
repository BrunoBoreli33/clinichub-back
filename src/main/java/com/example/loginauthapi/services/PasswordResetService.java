package com.example.loginauthapi.services;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PasswordResetService {
    private final Map<String, PasswordReset> passwordResets = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private static final int CODE_EXPIRATION_MINUTES = 15;
    private static final int MAX_ATTEMPTS = 3;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PasswordReset implements Serializable {
        private String email;
        private String code;
        private LocalDateTime createdAt;
        private int attempts;
        private boolean verified;
    }

    public String createPasswordReset(String email) {
        String code = String.format("%06d", random.nextInt(1000000));
        PasswordReset passwordReset = new PasswordReset(
                email,
                code,
                LocalDateTime.now(),
                0,
                false
        );
        passwordResets.put(email, passwordReset);
        return code;
    }

    public PasswordReset getPasswordReset(String email) {
        PasswordReset reset = passwordResets.get(email);
        if (reset == null) return null;

        if (reset.getCreatedAt().plusMinutes(CODE_EXPIRATION_MINUTES).isBefore(LocalDateTime.now())) {
            passwordResets.remove(email);
            return null;
        }

        return reset;
    }

    public boolean verifyCode(String email, String code) {
        PasswordReset reset = getPasswordReset(email);
        if (reset == null) return false;

        if (reset.getAttempts() >= MAX_ATTEMPTS) {
            passwordResets.remove(email);
            return false;
        }

        reset.setAttempts(reset.getAttempts() + 1);

        if (reset.getCode().equals(code)) {
            reset.setVerified(true);
            return true;
        }

        return false;
    }

    public boolean isVerified(String email) {
        PasswordReset reset = getPasswordReset(email);
        return reset != null && reset.isVerified();
    }

    public void removePasswordReset(String email) {
        passwordResets.remove(email);
    }
}