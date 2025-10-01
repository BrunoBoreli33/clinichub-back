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
public class TempUserService {
    private final Map<String, TempUser> tempUsers = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private static final int CODE_EXPIRATION_MINUTES = 15;
    private static final int MAX_ATTEMPTS = 3;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TempUser implements Serializable {
        private String name;
        private String email;
        private String password;
        private String code;
        private LocalDateTime createdAt;
        private int attempts;
    }

    public String createTempUser(String name, String email, String password) {
        String code = String.format("%06d", random.nextInt(1000000));
        TempUser tempUser = new TempUser(
                name,
                email,
                password,
                code,
                LocalDateTime.now(),
                0
        );
        tempUsers.put(email, tempUser);
        return code;
    }

    public TempUser getTempUser(String email) {
        TempUser user = tempUsers.get(email);
        if (user == null) return null;

        if (user.getCreatedAt().plusMinutes(CODE_EXPIRATION_MINUTES).isBefore(LocalDateTime.now())) {
            tempUsers.remove(email);
            return null;
        }

        return user;
    }

    public boolean verifyCode(String email, String code) {
        TempUser user = getTempUser(email);
        if (user == null) return false;

        if (user.getAttempts() >= MAX_ATTEMPTS) {
            tempUsers.remove(email);
            return false;
        }

        user.setAttempts(user.getAttempts() + 1);
        return user.getCode().equals(code);
    }

    public void removeTempUser(String email) {
        tempUsers.remove(email);
    }
}