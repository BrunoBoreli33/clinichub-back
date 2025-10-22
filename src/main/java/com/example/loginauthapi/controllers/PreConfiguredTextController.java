package com.example.loginauthapi.controllers;

import com.example.loginauthapi.dto.PreConfiguredTextDTO;
import com.example.loginauthapi.dto.PreConfiguredTextRequestDTO;
import com.example.loginauthapi.entities.User;
import com.example.loginauthapi.services.PreConfiguredTextService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/dashboard/pre-configured-texts")
@RequiredArgsConstructor
@Slf4j
public class PreConfiguredTextController {

    private final PreConfiguredTextService preConfiguredTextService;

    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof User)) {
            throw new RuntimeException("Usuário não autenticado");
        }
        return (User) auth.getPrincipal();
    }

    /**
     * GET /dashboard/pre-configured-texts
     * Buscar todos os textos pré-configurados do usuário
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllTexts() {
        try {
            User user = getAuthenticatedUser();
            List<PreConfiguredTextDTO> texts = preConfiguredTextService.getAllTexts(user);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "texts", texts
            ));
        } catch (Exception e) {
            log.error("❌ Erro ao buscar textos pré-configurados", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Erro ao buscar textos: " + e.getMessage()
            ));
        }
    }

    /**
     * POST /dashboard/pre-configured-texts
     * Criar novo texto pré-configurado
     */
    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> createText(@Valid @RequestBody PreConfiguredTextRequestDTO request) {
        try {
            User user = getAuthenticatedUser();
            PreConfiguredTextDTO text = preConfiguredTextService.createText(
                    request.getTitle(),
                    request.getContent(),
                    user
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Texto criado com sucesso",
                    "text", text
            ));
        } catch (Exception e) {
            log.error("❌ Erro ao criar texto pré-configurado", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Erro ao criar texto: " + e.getMessage()
            ));
        }
    }

    /**
     * PUT /dashboard/pre-configured-texts/{id}
     * Atualizar texto pré-configurado
     */
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> updateText(
            @PathVariable String id,
            @Valid @RequestBody PreConfiguredTextRequestDTO request) {
        try {
            User user = getAuthenticatedUser();
            PreConfiguredTextDTO text = preConfiguredTextService.updateText(
                    id,
                    request.getTitle(),
                    request.getContent(),
                    user
            );

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Texto atualizado com sucesso",
                    "text", text
            ));
        } catch (Exception e) {
            log.error("❌ Erro ao atualizar texto pré-configurado", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Erro ao atualizar texto: " + e.getMessage()
            ));
        }
    }

    /**
     * DELETE /dashboard/pre-configured-texts/{id}
     * Deletar texto pré-configurado
     */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteText(@PathVariable String id) {
        try {
            User user = getAuthenticatedUser();
            preConfiguredTextService.deleteText(id, user);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Texto deletado com sucesso"
            ));
        } catch (Exception e) {
            log.error("❌ Erro ao deletar texto pré-configurado", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Erro ao deletar texto: " + e.getMessage()
            ));
        }
    }
}