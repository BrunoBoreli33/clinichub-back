package com.example.loginauthapi.controllers;

import com.example.loginauthapi.dto.WebInstanceDTO;
import com.example.loginauthapi.entities.User;
import com.example.loginauthapi.entities.WebInstance;
import com.example.loginauthapi.repositories.UserRepository;
import com.example.loginauthapi.repositories.WebInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller para gerenciamento de WebInstances
 * APENAS PARA DESENVOLVEDORES/ADMINISTRADORES
 */
@RestController
@RequestMapping("/api/dev/webinstances")
@RequiredArgsConstructor
@Slf4j
public class WebInstanceController {

    private final WebInstanceRepository webInstanceRepository;
    private final UserRepository userRepository;

    /**
     * Verifica se o usuário autenticado é ADMIN
     */
    private User getAuthenticatedAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof User)) {
            throw new RuntimeException("Usuário não autenticado");
        }

        User user = (User) auth.getPrincipal();

        // IMPORTANTE: Verificar se o usuário tem role ADMIN
        if (!"ADMIN".equalsIgnoreCase(user.getRole())) {
            throw new RuntimeException("Acesso negado: apenas administradores podem acessar esta rota");
        }

        return user;
    }

    /**
     * GET /api/dev/webinstances
     * Lista todas as WebInstances (com paginação opcional)
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> listAllInstances(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String userId) {

        try {
            getAuthenticatedAdmin(); // Validação extra

            log.info("Listando WebInstances - Status: {}, UserId: {}", status, userId);

            List<WebInstance> instances;

            if (userId != null && !userId.isEmpty()) {
                instances = webInstanceRepository.findByUserId(userId);
            } else if (status != null && !status.isEmpty()) {
                instances = webInstanceRepository.findByStatus(status);
            } else {
                instances = webInstanceRepository.findAll();
            }

            List<WebInstanceDTO> dtos = instances.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("total", dtos.size());
            response.put("instances", dtos);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erro ao listar WebInstances", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * GET /api/dev/webinstances/{id}
     * Busca uma WebInstance específica por ID
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getInstanceById(@PathVariable String id) {
        try {
            getAuthenticatedAdmin();

            log.info("Buscando WebInstance: {}", id);

            WebInstance instance = webInstanceRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("WebInstance não encontrada"));

            WebInstanceDTO dto = convertToDTO(instance);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "instance", dto
            ));

        } catch (Exception e) {
            log.error("Erro ao buscar WebInstance {}", id, e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * POST /api/dev/webinstances
     * Cria uma nova WebInstance
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> createInstance(@RequestBody Map<String, Object> body) {
        try {
            getAuthenticatedAdmin();

            log.info("Criando nova WebInstance");

            // Validações
            String userId = (String) body.get("userId");
            String status = (String) body.get("status");
            String clientToken = (String) body.get("clientToken");
            String seuToken = (String) body.get("seuToken");
            String suaInstancia = (String) body.get("suaInstancia");

            if (userId == null || userId.trim().isEmpty()) {
                throw new RuntimeException("userId é obrigatório");
            }
            if (clientToken == null || clientToken.trim().isEmpty()) {
                throw new RuntimeException("clientToken é obrigatório");
            }
            if (seuToken == null || seuToken.trim().isEmpty()) {
                throw new RuntimeException("seuToken é obrigatório");
            }
            if (suaInstancia == null || suaInstancia.trim().isEmpty()) {
                throw new RuntimeException("suaInstancia é obrigatório");
            }

            // Buscar usuário
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

            // Criar WebInstance
            WebInstance instance = new WebInstance();
            instance.setUser(user);
            instance.setStatus(status != null ? status : "PENDING");
            instance.setClientToken(clientToken);
            instance.setSeuToken(seuToken);
            instance.setSuaInstancia(suaInstancia);

            // Expiração (opcional)
            if (body.containsKey("expiraEm") && body.get("expiraEm") != null) {
                String expiraEmStr = (String) body.get("expiraEm");
                instance.setExpiraEm(LocalDateTime.parse(expiraEmStr));
            }

            WebInstance saved = webInstanceRepository.save(instance);

            log.info("WebInstance criada com sucesso: {}", saved.getId());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "WebInstance criada com sucesso",
                    "instance", convertToDTO(saved)
            ));

        } catch (Exception e) {
            log.error("Erro ao criar WebInstance", e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * PUT /api/dev/webinstances/{id}
     * Atualiza uma WebInstance existente
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> updateInstance(
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {

        try {
            getAuthenticatedAdmin();

            log.info("Atualizando WebInstance: {}", id);

            WebInstance instance = webInstanceRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("WebInstance não encontrada"));

            // Atualizar campos (apenas os que foram enviados)
            if (body.containsKey("status")) {
                instance.setStatus((String) body.get("status"));
            }
            if (body.containsKey("clientToken")) {
                instance.setClientToken((String) body.get("clientToken"));
            }
            if (body.containsKey("seuToken")) {
                instance.setSeuToken((String) body.get("seuToken"));
            }
            if (body.containsKey("suaInstancia")) {
                instance.setSuaInstancia((String) body.get("suaInstancia"));
            }
            if (body.containsKey("expiraEm")) {
                String expiraEmStr = (String) body.get("expiraEm");
                if (expiraEmStr != null && !expiraEmStr.isEmpty()) {
                    instance.setExpiraEm(LocalDateTime.parse(expiraEmStr));
                } else {
                    instance.setExpiraEm(null);
                }
            }

            WebInstance updated = webInstanceRepository.save(instance);

            log.info("WebInstance atualizada com sucesso: {}", id);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "WebInstance atualizada com sucesso",
                    "instance", convertToDTO(updated)
            ));

        } catch (Exception e) {
            log.error("Erro ao atualizar WebInstance {}", id, e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * DELETE /api/dev/webinstances/{id}
     * Deleta uma WebInstance (com confirmação)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> deleteInstance(
            @PathVariable String id,
            @RequestParam(required = false, defaultValue = "false") boolean confirm) {

        try {
            getAuthenticatedAdmin();

            if (!confirm) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Para deletar, adicione ?confirm=true à URL"
                ));
            }

            log.warn("Deletando WebInstance: {}", id);

            WebInstance instance = webInstanceRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("WebInstance não encontrada"));

            // Verificar se há chats associados
            if (instance.getChats() != null && !instance.getChats().isEmpty()) {
                log.warn("WebInstance {} possui {} chats associados", id, instance.getChats().size());
            }

            webInstanceRepository.delete(instance);

            log.info("WebInstance deletada com sucesso: {}", id);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "WebInstance deletada com sucesso"
            ));

        } catch (Exception e) {
            log.error("Erro ao deletar WebInstance {}", id, e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * PATCH /api/dev/webinstances/{id}/status
     * Atualiza apenas o status de uma WebInstance
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> updateStatus(
            @PathVariable String id,
            @RequestBody Map<String, String> body) {

        try {
            getAuthenticatedAdmin();

            String newStatus = body.get("status");
            if (newStatus == null || newStatus.trim().isEmpty()) {
                throw new RuntimeException("status é obrigatório");
            }

            log.info("Atualizando status da WebInstance {} para {}", id, newStatus);

            WebInstance instance = webInstanceRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("WebInstance não encontrada"));

            instance.setStatus(newStatus);
            WebInstance updated = webInstanceRepository.save(instance);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Status atualizado com sucesso",
                    "instance", convertToDTO(updated)
            ));

        } catch (Exception e) {
            log.error("Erro ao atualizar status da WebInstance {}", id, e);
            return ResponseEntity.badRequest()
                    .body(Map.of("success", false, "message", e.getMessage()));
        }
    }

    /**
     * Converte WebInstance para DTO
     */
    private WebInstanceDTO convertToDTO(WebInstance instance) {
        return WebInstanceDTO.builder()
                .id(instance.getId())
                .userId(instance.getUser().getId())
                .userName(instance.getUser().getName())
                .userEmail(instance.getUser().getEmail())
                .status(instance.getStatus())
                .criadoEm(instance.getCriadoEm())
                .expiraEm(instance.getExpiraEm())
                .clientToken(instance.getClientToken())
                .seuToken(instance.getSeuToken())
                .suaInstancia(instance.getSuaInstancia())
                .totalChats(instance.getChats() != null ? instance.getChats().size() : 0)
                .build();
    }
}