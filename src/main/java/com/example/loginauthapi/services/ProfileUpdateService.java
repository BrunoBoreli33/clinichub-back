package com.example.loginauthapi.services;

import com.example.loginauthapi.entities.User;
import com.example.loginauthapi.entities.Chat;
import com.example.loginauthapi.entities.WebInstance;
import com.example.loginauthapi.repositories.UserRepository;
import com.example.loginauthapi.repositories.ChatRepository;
import com.example.loginauthapi.repositories.WebInstanceRepository;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProfileUpdateService {
    private final Map<String, PendingUpdate> pendingUpdates = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private static final int CODE_EXPIRATION_MINUTES = 15;
    private static final int MAX_ATTEMPTS = 3;

    // ✅ NOVO: Injeção de dependências para upload phone
    private final UserRepository userRepository;
    private final ChatRepository chatRepository;
    private final WebInstanceRepository webInstanceRepository;

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

    // ✅ CORRIGIDO: Configurar número de telefone para upload com melhor tratamento de erros
    @Transactional
    public User setUploadPhoneNumber(String userId, String phoneNumber) {
        log.info("📱 Configurando número de upload - UserId: {}, Phone: {}", userId, phoneNumber);

        try {
            // Buscar usuário
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> {
                        log.error("❌ Usuário não encontrado: {}", userId);
                        return new RuntimeException("Usuário não encontrado");
                    });

            log.info("✅ Usuário encontrado: {}", user.getEmail());

            // Limpar marcação de upload em chats anteriores
            if (user.getUploadPhoneNumber() != null && !user.getUploadPhoneNumber().equals(phoneNumber)) {
                log.info("🧹 Limpando número anterior: {}", user.getUploadPhoneNumber());
                clearPreviousUploadChats(userId, user.getUploadPhoneNumber());
            }

            // Definir novo número
            String oldNumber = user.getUploadPhoneNumber();
            user.setUploadPhoneNumber(phoneNumber);

            log.info("💾 Salvando usuário no banco de dados...");
            user = userRepository.save(user);
            log.info("✅ Usuário salvo com sucesso. Número anterior: {}, Novo número: {}",
                    oldNumber, user.getUploadPhoneNumber());

            // Marcar chat existente como upload chat, se houver
            try {
                markChatAsUpload(userId, phoneNumber);
            } catch (Exception e) {
                log.warn("⚠️ Erro ao marcar chat como upload (não crítico): {}", e.getMessage());
                // Não interrompe o fluxo, pois o número foi salvo com sucesso
            }

            log.info("✅ Número de upload configurado com sucesso");
            return user;

        } catch (Exception e) {
            log.error("❌ Erro detalhado ao configurar número de upload", e);
            log.error("❌ Tipo de erro: {}", e.getClass().getName());
            log.error("❌ Mensagem: {}", e.getMessage());
            if (e.getCause() != null) {
                log.error("❌ Causa raiz: {}", e.getCause().getMessage());
            }
            throw new RuntimeException("Erro ao configurar número de upload: " + e.getMessage(), e);
        }
    }

    // ✅ NOVO: Buscar número de upload configurado
    public String getUploadPhoneNumber(String userId) {
        log.info("🔍 Buscando número de upload - UserId: {}", userId);

        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

            String phoneNumber = user.getUploadPhoneNumber();
            log.info("✅ Número de upload encontrado: {}", phoneNumber != null ? phoneNumber : "não configurado");

            return phoneNumber;
        } catch (Exception e) {
            log.error("❌ Erro ao buscar número de upload", e);
            throw new RuntimeException("Erro ao buscar número de upload: " + e.getMessage(), e);
        }
    }

    // ✅ NOVO: Remover configuração de número de upload
    @Transactional
    public User removeUploadPhoneNumber(String userId) {
        log.info("🗑️ Removendo número de upload - UserId: {}", userId);

        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

            if (user.getUploadPhoneNumber() != null) {
                String oldNumber = user.getUploadPhoneNumber();
                clearPreviousUploadChats(userId, oldNumber);
                user.setUploadPhoneNumber(null);
                user = userRepository.save(user);
                log.info("✅ Número de upload removido: {}", oldNumber);
            } else {
                log.info("ℹ️ Usuário não tinha número de upload configurado");
            }

            return user;
        } catch (Exception e) {
            log.error("❌ Erro ao remover número de upload", e);
            throw new RuntimeException("Erro ao remover número de upload: " + e.getMessage(), e);
        }
    }

    // ✅ CORRIGIDO: Limpar marcação de upload em chats anteriores com melhor tratamento de erros
    private void clearPreviousUploadChats(String userId, String phoneNumber) {
        log.info("🧹 Limpando marcação de upload de chats anteriores - Phone: {}", phoneNumber);

        try {
            // Buscar instâncias do usuário
            var instances = webInstanceRepository.findByUserId(userId);
            log.info("📱 Encontradas {} instâncias para o usuário", instances.size());

            if (instances.isEmpty()) {
                log.info("ℹ️ Usuário não possui instâncias conectadas");
                return;
            }

            int chatsUpdated = 0;
            for (WebInstance instance : instances) {
                Optional<Chat> chatOpt = chatRepository.findByWebInstanceIdAndPhone(instance.getId(), phoneNumber);
                if (chatOpt.isPresent()) {
                    Chat chat = chatOpt.get();
                    chat.setIsUploadChat(false);
                    chatRepository.save(chat);
                    chatsUpdated++;
                    log.info("✅ Chat desmarcado como upload - ChatId: {}, Instance: {}",
                            chat.getId(), instance.getSuaInstancia());
                }
            }

            if (chatsUpdated == 0) {
                log.info("ℹ️ Nenhum chat encontrado com o número: {}", phoneNumber);
            } else {
                log.info("✅ Total de {} chat(s) desmarcado(s)", chatsUpdated);
            }

        } catch (Exception e) {
            log.error("❌ Erro ao limpar marcação de chats anteriores", e);
            // Não propaga a exceção para não interromper o fluxo principal
        }
    }

    // ✅ CORRIGIDO: Marcar chat como chat de upload com melhor tratamento de erros
    private void markChatAsUpload(String userId, String phoneNumber) {
        log.info("🏷️ Marcando chat como upload - Phone: {}", phoneNumber);

        try {
            // Buscar instâncias do usuário
            var instances = webInstanceRepository.findByUserId(userId);
            log.info("📱 Encontradas {} instâncias para o usuário", instances.size());

            if (instances.isEmpty()) {
                log.info("ℹ️ Usuário não possui instâncias conectadas. Chat será marcado quando houver conversa.");
                return;
            }

            int chatsMarked = 0;
            for (WebInstance instance : instances) {
                Optional<Chat> chatOpt = chatRepository.findByWebInstanceIdAndPhone(instance.getId(), phoneNumber);
                if (chatOpt.isPresent()) {
                    Chat chat = chatOpt.get();
                    chat.setIsUploadChat(true);
                    chatRepository.save(chat);
                    chatsMarked++;
                    log.info("✅ Chat marcado como upload - ChatId: {}, Instance: {}",
                            chat.getId(), instance.getSuaInstancia());
                }
            }

            if (chatsMarked == 0) {
                log.info("ℹ️ Nenhum chat existente com o número: {}. Será marcado ao iniciar conversa.", phoneNumber);
            } else {
                log.info("✅ Total de {} chat(s) marcado(s) como upload", chatsMarked);
            }

        } catch (Exception e) {
            log.error("❌ Erro ao marcar chat como upload", e);
            // Não propaga a exceção para não interromper o fluxo principal
        }
    }
}