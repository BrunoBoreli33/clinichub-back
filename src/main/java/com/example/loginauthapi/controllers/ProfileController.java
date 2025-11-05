package com.example.loginauthapi.controllers;

import com.example.loginauthapi.dto.informacoesDaConta.*;
import com.example.loginauthapi.entities.User;
import com.example.loginauthapi.infra.security.TokenService;
import com.example.loginauthapi.repositories.UserRepository;
import com.example.loginauthapi.services.EmailService;
import com.example.loginauthapi.services.ProfileUpdateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*", allowedHeaders = "*")
public class ProfileController {

    private final UserRepository userRepository;
    private final ProfileUpdateService profileUpdateService;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    /**
     * Obtém o usuário autenticado do contexto
     */
    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof User)) {
            throw new RuntimeException("Usuário não autenticado");
        }

        return (User) auth.getPrincipal();
    }

    /**
     * Atualiza o nome do usuário (sem confirmação por email)
     * PUT /api/profile/update-name
     */
    @PutMapping("/update-name")
    @Transactional
    public ResponseEntity<?> updateName(@RequestBody UpdateNameRequestDTO body) {
        try {
            User user = getAuthenticatedUser();

            if (body.name() == null || body.name().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Nome não pode ser vazio"));
            }

            // Busca usuário atualizado do banco
            User dbUser = userRepository.findById(user.getId())
                    .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

            dbUser.setName(body.name().trim());
            userRepository.save(dbUser);

            log.info("Nome do usuário {} atualizado com sucesso", user.getId());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Nome atualizado com sucesso",
                    "name", dbUser.getName()
            ));

        } catch (Exception e) {
            log.error("Erro ao atualizar nome", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", "Erro ao atualizar nome"));
        }
    }

    /**
     * Solicita mudança de email (envia código de confirmação)
     * POST /api/profile/request-email-change
     */
    @PostMapping("/request-email-change")
    public ResponseEntity<?> requestEmailChange(@RequestBody RequestEmailChangeDTO body) {
        try {
            User user = getAuthenticatedUser();

            if (body.newEmail() == null || body.newEmail().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Novo email não pode ser vazio"));
            }

            // Verifica se o novo email já está em uso
            if (userRepository.findByEmail(body.newEmail()).isPresent()) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("success", false, "message", "Este email já está em uso"));
            }

            // Cria código de confirmação
            String code = profileUpdateService.createPendingUpdate(
                    user.getId(),
                    user.getEmail(),
                    body.newEmail(),
                    "EMAIL"
            );

            // Envia email de confirmação para o email ATUAL
            emailService.sendProfileUpdateVerificationEmail(user.getEmail(), code, "EMAIL");

            log.info("Código de alteração de email enviado para {}", user.getEmail());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Código de confirmação enviado para " + user.getEmail()
            ));

        } catch (Exception e) {
            log.error("Erro ao solicitar mudança de email", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", "Erro ao enviar código de confirmação"));
        }
    }

    /**
     * Confirma mudança de email com código
     * POST /api/profile/confirm-email-change
     */
    @PostMapping("/confirm-email-change")
    @Transactional
    public ResponseEntity<?> confirmEmailChange(@RequestBody ConfirmEmailChangeDTO body) {
        try {
            User user = getAuthenticatedUser();

            // Verifica o código
            if (!profileUpdateService.verifyCode(user.getEmail(), "EMAIL", body.code())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("success", false, "message", "Código inválido ou expirado"));
            }

            // Busca a atualização pendente
            ProfileUpdateService.PendingUpdate pending =
                    profileUpdateService.getPendingUpdate(user.getEmail(), "EMAIL");

            if (pending == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("success", false, "message", "Código expirado"));
            }

            // Verifica se o novo email ainda está disponível
            if (userRepository.findByEmail(pending.getNewValue()).isPresent()) {
                profileUpdateService.removePendingUpdate(user.getEmail(), "EMAIL");
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("success", false, "message", "Este email já está em uso"));
            }

            // Atualiza o email
            User dbUser = userRepository.findById(user.getId())
                    .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

            String oldEmail = dbUser.getEmail();
            dbUser.setEmail(pending.getNewValue());

            // Gera novo token com o novo email
            String newToken = tokenService.generateToken(dbUser);
            dbUser.setConfirmationToken(newToken);

            userRepository.save(dbUser);

            // Remove a atualização pendente
            profileUpdateService.removePendingUpdate(oldEmail, "EMAIL");

            log.info("Email do usuário {} alterado de {} para {}",
                    user.getId(), oldEmail, dbUser.getEmail());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Email atualizado com sucesso",
                    "email", dbUser.getEmail(),
                    "token", newToken // Retorna novo token
            ));

        } catch (Exception e) {
            log.error("Erro ao confirmar mudança de email", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", "Erro ao atualizar email"));
        }
    }

    /**
     * Solicita mudança de senha (envia código de confirmação)
     * POST /api/profile/request-password-change
     */
    @PostMapping("/request-password-change")
    public ResponseEntity<?> requestPasswordChange(@RequestBody RequestPasswordChangeDTO body) {
        try {
            User user = getAuthenticatedUser();

            if (body.newPassword() == null || body.newPassword().trim().isEmpty()) {
                return ResponseEntity.badRequest()
                        .body(Map.of("success", false, "message", "Nova senha não pode ser vazia"));
            }

            // Criptografa a nova senha antes de armazenar temporariamente
            String encodedPassword = passwordEncoder.encode(body.newPassword());

            // Cria código de confirmação
            String code = profileUpdateService.createPendingUpdate(
                    user.getId(),
                    user.getEmail(),
                    encodedPassword,
                    "PASSWORD"
            );

            // Envia email de confirmação
            emailService.sendProfileUpdateVerificationEmail(user.getEmail(), code, "PASSWORD");

            log.info("Código de alteração de senha enviado para {}", user.getEmail());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Código de confirmação enviado para " + user.getEmail()
            ));

        } catch (Exception e) {
            log.error("Erro ao solicitar mudança de senha", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", "Erro ao enviar código de confirmação"));
        }
    }

    /**
     * Confirma mudança de senha com código
     * POST /api/profile/confirm-password-change
     */
    @PostMapping("/confirm-password-change")
    @Transactional
    public ResponseEntity<?> confirmPasswordChange(@RequestBody ConfirmPasswordChangeDTO body) {
        try {
            User user = getAuthenticatedUser();

            // Verifica o código
            if (!profileUpdateService.verifyCode(user.getEmail(), "PASSWORD", body.code())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("success", false, "message", "Código inválido ou expirado"));
            }

            // Busca a atualização pendente
            ProfileUpdateService.PendingUpdate pending =
                    profileUpdateService.getPendingUpdate(user.getEmail(), "PASSWORD");

            if (pending == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("success", false, "message", "Código expirado"));
            }

            // Atualiza a senha
            User dbUser = userRepository.findById(user.getId())
                    .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

            dbUser.setPassword(pending.getNewValue()); // Já está encriptada
            userRepository.save(dbUser);

            // Remove a atualização pendente
            profileUpdateService.removePendingUpdate(user.getEmail(), "PASSWORD");

            log.info("Senha do usuário {} atualizada com sucesso", user.getId());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Senha atualizada com sucesso"
            ));

        } catch (Exception e) {
            log.error("Erro ao confirmar mudança de senha", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", "Erro ao atualizar senha"));
        }
    }

    // ✅ NOVO: PUT /api/profile/upload-phone
    // Configurar número de telefone para upload de mídias
    @PutMapping("/upload-phone")
    public ResponseEntity<Map<String, Object>> setUploadPhoneNumber(@RequestBody Map<String, String> body) {
        try {
            log.info("📱 Requisição para configurar número de upload");

            User user = getAuthenticatedUser();
            log.info("👤 Usuário autenticado: {} (ID: {})", user.getEmail(), user.getId());

            String phoneNumber = body.get("phoneNumber");
            log.info("📞 Número recebido: {}", phoneNumber);

            if (phoneNumber == null || phoneNumber.trim().isEmpty()) {
                log.warn("⚠️ Número de telefone vazio ou nulo");
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Número de telefone é obrigatório"
                ));
            }

            String cleanPhone = phoneNumber.trim();
            log.info("🧹 Número limpo: {}", cleanPhone);

            User updatedUser = profileUpdateService.setUploadPhoneNumber(user.getId(), cleanPhone);

            log.info("✅ Número de upload configurado com sucesso: {}", updatedUser.getUploadPhoneNumber());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Número de upload configurado com sucesso",
                    "uploadPhoneNumber", updatedUser.getUploadPhoneNumber()
            ));

        } catch (Exception e) {
            log.error("❌ Erro ao configurar número de upload", e);
            log.error("❌ Tipo de erro: {}", e.getClass().getName());
            log.error("❌ Mensagem: {}", e.getMessage());
            if (e.getCause() != null) {
                log.error("❌ Causa raiz: {}", e.getCause().getMessage());
            }
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Erro ao salvar número de upload: " + e.getMessage()
            ));
        }
    }

    // ✅ NOVO: GET /api/profile/upload-phone
    // Buscar número de upload configurado
    @GetMapping("/upload-phone")
    public ResponseEntity<Map<String, Object>> getUploadPhoneNumber() {
        try {
            log.info("🔍 Requisição para buscar número de upload");

            User user = getAuthenticatedUser();
            String phoneNumber = profileUpdateService.getUploadPhoneNumber(user.getId());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "uploadPhoneNumber", phoneNumber != null ? phoneNumber : ""
            ));

        } catch (Exception e) {
            log.error("❌ Erro ao buscar número de upload", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    // ✅ NOVO: DELETE /api/profile/upload-phone
    // Remover configuração de número de upload
    @DeleteMapping("/upload-phone")
    public ResponseEntity<Map<String, Object>> removeUploadPhoneNumber() {
        try {
            log.info("🗑️ Requisição para remover número de upload");

            User user = getAuthenticatedUser();
            profileUpdateService.removeUploadPhoneNumber(user.getId());

            log.info("✅ Número de upload removido com sucesso");

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Número de upload removido com sucesso"
            ));

        } catch (Exception e) {
            log.error("❌ Erro ao remover número de upload", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }
}