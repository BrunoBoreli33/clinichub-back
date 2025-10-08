package com.example.loginauthapi.controllers;

import com.example.loginauthapi.dto.TagDTO;
import com.example.loginauthapi.dto.TagRequestDTO;
import com.example.loginauthapi.entities.User;
import com.example.loginauthapi.services.TagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/dashboard/tags")
@RequiredArgsConstructor
@Slf4j
public class TagController {

    private final TagService tagService;

    /**
     * Obter usuário autenticado
     */
    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof User)) {
            throw new RuntimeException("Usuário não autenticado");
        }
        return (User) auth.getPrincipal();
    }

    /**
     * GET /dashboard/tags - Listar todas as tags do usuário
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllTags() {
        try {
            User user = getAuthenticatedUser();
            List<TagDTO> tags = tagService.getAllTagsByUser(user);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "tags", tags
            ));

        } catch (Exception e) {
            log.error("Erro ao buscar tags", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Erro ao buscar etiquetas: " + e.getMessage()
            ));
        }
    }

    /**
     * GET /dashboard/tags/{id} - Buscar tag específica
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getTagById(@PathVariable String id) {
        try {
            User user = getAuthenticatedUser();
            TagDTO tag = tagService.getTagById(id, user);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "tag", tag
            ));

        } catch (Exception e) {
            log.error("Erro ao buscar tag {}", id, e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * POST /dashboard/tags - Criar nova tag
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createTag(@Valid @RequestBody TagRequestDTO request) {
        try {
            User user = getAuthenticatedUser();
            TagDTO createdTag = tagService.createTag(request, user);

            log.info("Tag criada com sucesso: {} (ID: {})", createdTag.getName(), createdTag.getId());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Etiqueta criada com sucesso",
                    "tag", createdTag
            ));

        } catch (Exception e) {
            log.error("Erro ao criar tag", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * PUT /dashboard/tags/{id} - Atualizar tag existente
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateTag(
            @PathVariable String id,
            @Valid @RequestBody TagRequestDTO request) {
        try {
            User user = getAuthenticatedUser();
            TagDTO updatedTag = tagService.updateTag(id, request, user);

            log.info("Tag atualizada com sucesso: {} (ID: {})", updatedTag.getName(), id);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Etiqueta atualizada com sucesso",
                    "tag", updatedTag
            ));

        } catch (Exception e) {
            log.error("Erro ao atualizar tag {}", id, e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * DELETE /dashboard/tags/{id} - Deletar tag
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteTag(@PathVariable String id) {
        try {
            User user = getAuthenticatedUser();
            tagService.deleteTag(id, user);

            log.info("Tag deletada com sucesso: ID {}", id);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Etiqueta deletada com sucesso"
            ));

        } catch (Exception e) {
            log.error("Erro ao deletar tag {}", id, e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }
}