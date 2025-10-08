package com.example.loginauthapi.services;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ProfileUpdateService {
    private final Map<String, PendingUpdate> pendingUpdates = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private static final int CODE_EXPIRATION_MINUTES = 15;
    private static final int MAX_ATTEMPTS = 3;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class PendingUpdate {
        private String userId;
        private String currentEmail;
        private String newValue; // Novo email ou nova senha (encriptada)
        private String updateType; // "EMAIL" ou "PASSWORD"
        private String code;
        private LocalDateTime createdAt;
        private int attempts;
    }

    public String createPendingUpdate(String userId, String currentEmail, String newValue, String updateType) {
        String code = String.format("%06d", random.nextInt(1000000));

        // Usa o email atual como chave única
        String key = currentEmail + "_" + updateType;

        PendingUpdate pending = new PendingUpdate(
                userId,
                currentEmail,
                newValue,
                updateType,
                code,
                LocalDateTime.now(),
                0
        );

        pendingUpdates.put(key, pending);
        return code;
    }

    public PendingUpdate getPendingUpdate(String currentEmail, String updateType) {
        String key = currentEmail + "_" + updateType;
        PendingUpdate update = pendingUpdates.get(key);

        if (update == null) return null;

        // Verifica se expirou
        if (update.getCreatedAt().plusMinutes(CODE_EXPIRATION_MINUTES).isBefore(LocalDateTime.now())) {
            pendingUpdates.remove(key);
            return null;
        }

        return update;
    }

    public boolean verifyCode(String currentEmail, String updateType, String code) {
        PendingUpdate update = getPendingUpdate(currentEmail, updateType);
        if (update == null) return false;

        if (update.getAttempts() >= MAX_ATTEMPTS) {
            String key = currentEmail + "_" + updateType;
            pendingUpdates.remove(key);
            return false;
        }

        update.setAttempts(update.getAttempts() + 1);
        return update.getCode().equals(code);
    }

    public void removePendingUpdate(String currentEmail, String updateType) {
        String key = currentEmail + "_" + updateType;
        pendingUpdates.remove(key);
    }
}