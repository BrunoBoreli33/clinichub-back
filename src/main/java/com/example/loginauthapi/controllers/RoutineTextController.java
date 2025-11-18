package com.example.loginauthapi.controllers;

import com.example.loginauthapi.dto.RoutineTextDTO;
import com.example.loginauthapi.dto.RoutineTextRequestDTO;
import com.example.loginauthapi.entities.RoutineText;
import com.example.loginauthapi.entities.User;
import com.example.loginauthapi.repositories.RoutineTextRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/dashboard/routines")
@RequiredArgsConstructor
@Slf4j
public class RoutineTextController {

    private final RoutineTextRepository routineTextRepository;

    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof User)) {
            throw new RuntimeException("Usuário não autenticado");
        }
        return (User) auth.getPrincipal();
    }

    private RoutineTextDTO toDTO(RoutineText routine) {
        // ✅ NOVO: Converter photoIds e videoIds de String para List
        List<String> photoIds = null;
        if (routine.getPhotoIds() != null && !routine.getPhotoIds().isEmpty()) {
            photoIds = Arrays.asList(routine.getPhotoIds().split(","));
        }

        List<String> videoIds = null;
        if (routine.getVideoIds() != null && !routine.getVideoIds().isEmpty()) {
            videoIds = Arrays.asList(routine.getVideoIds().split(","));
        }

        return new RoutineTextDTO(
                routine.getId(),
                routine.getSequenceNumber(),
                routine.getTextContent(),
                routine.getHoursDelay(),
                photoIds,  // ✅ NOVO
                videoIds   // ✅ NOVO
        );
    }

    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllRoutines() {
        try {
            User user = getAuthenticatedUser();
            log.debug("🔍 [USER: {}] Buscando todas as rotinas", user.getId());

            List<RoutineText> routines = routineTextRepository.findByUserIdOrderBySequenceNumberAsc(user.getId());

            log.info("📋 [USER: {}] Total de rotinas encontradas: {}", user.getId(), routines.size());
            routines.forEach(r -> log.debug("  ↳ Rotina #{}: hoursDelay={}, conteúdo: '{}'",
                    r.getSequenceNumber(),
                    r.getHoursDelay(),
                    r.getTextContent().length() > 50
                            ? r.getTextContent().substring(0, 50) + "..."
                            : r.getTextContent()));

            List<RoutineTextDTO> dtos = routines.stream()
                    .map(this::toDTO)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "routines", dtos
            ));
        } catch (Exception e) {
            log.error("❌ Erro ao buscar rotinas", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Erro ao buscar rotinas: " + e.getMessage()
            ));
        }
    }

    @PostMapping
    @Transactional
    public ResponseEntity<Map<String, Object>> createRoutine(@Valid @RequestBody RoutineTextRequestDTO request) {
        try {
            User user = getAuthenticatedUser();

            log.info("➕ [USER: {}] Criando nova rotina", user.getId());
            log.debug("  ↳ sequenceNumber: {}", request.getSequenceNumber());
            log.debug("  ↳ hoursDelay: {} horas", request.getHoursDelay());
            log.debug("  ↳ textContent: '{}'",
                    request.getTextContent().length() > 50
                            ? request.getTextContent().substring(0, 50) + "..."
                            : request.getTextContent());

            // Verificar se já existe uma rotina com este sequence number
            if (routineTextRepository.findByUserIdAndSequenceNumber(user.getId(), request.getSequenceNumber()).isPresent()) {
                log.warn("⚠️ [USER: {}] Já existe rotina com sequenceNumber={}",
                        user.getId(), request.getSequenceNumber());
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Já existe uma rotina com este número de sequência"
                ));
            }

            RoutineText routine = new RoutineText();
            routine.setUser(user);
            routine.setSequenceNumber(request.getSequenceNumber());
            routine.setTextContent(request.getTextContent());
            routine.setHoursDelay(request.getHoursDelay());

            // ✅ NOVO: Salvar photoIds como string separada por vírgulas
            if (request.getPhotoIds() != null && !request.getPhotoIds().isEmpty()) {
                routine.setPhotoIds(String.join(",", request.getPhotoIds()));
            }

            // ✅ NOVO: Salvar videoIds como string separada por vírgulas
            if (request.getVideoIds() != null && !request.getVideoIds().isEmpty()) {
                routine.setVideoIds(String.join(",", request.getVideoIds()));
            }

            RoutineText saved = routineTextRepository.save(routine);

            log.info("✅ [USER: {}] Rotina #{} criada com sucesso (ID: {})",
                    user.getId(), saved.getSequenceNumber(), saved.getId());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Rotina criada com sucesso",
                    "routine", toDTO(saved)
            ));
        } catch (Exception e) {
            log.error("❌ Erro ao criar rotina", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Erro ao criar rotina: " + e.getMessage()
            ));
        }
    }

    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> updateRoutine(
            @PathVariable String id,
            @Valid @RequestBody RoutineTextRequestDTO request) {
        try {
            User user = getAuthenticatedUser();

            log.info("✏️ [USER: {}] Atualizando rotina ID: {}", user.getId(), id);
            log.debug("  ↳ Novos valores - sequenceNumber: {}, hoursDelay: {}",
                    request.getSequenceNumber(), request.getHoursDelay());

            RoutineText routine = routineTextRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Rotina não encontrada"));

            if (!routine.getUser().getId().equals(user.getId())) {
                log.warn("⚠️ [USER: {}] Tentou editar rotina de outro usuário (ID: {})",
                        user.getId(), id);
                return ResponseEntity.status(403).body(Map.of(
                        "success", false,
                        "message", "Sem permissão para editar esta rotina"
                ));
            }

            log.debug("  ↳ Valores antigos - sequenceNumber: {}, hoursDelay: {}",
                    routine.getSequenceNumber(), routine.getHoursDelay());

            routine.setTextContent(request.getTextContent());
            routine.setHoursDelay(request.getHoursDelay());

            // ✅ NOVO: Atualizar photoIds
            if (request.getPhotoIds() != null && !request.getPhotoIds().isEmpty()) {
                routine.setPhotoIds(String.join(",", request.getPhotoIds()));
            } else {
                routine.setPhotoIds(null);
            }

            // ✅ NOVO: Atualizar videoIds
            if (request.getVideoIds() != null && !request.getVideoIds().isEmpty()) {
                routine.setVideoIds(String.join(",", request.getVideoIds()));
            } else {
                routine.setVideoIds(null);
            }

            RoutineText updated = routineTextRepository.save(routine);

            log.info("✅ [USER: {}] Rotina #{} atualizada com sucesso",
                    user.getId(), updated.getSequenceNumber());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Rotina atualizada com sucesso",
                    "routine", toDTO(updated)
            ));
        } catch (Exception e) {
            log.error("❌ Erro ao atualizar rotina", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Erro ao atualizar rotina: " + e.getMessage()
            ));
        }
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteRoutine(@PathVariable String id) {
        try {
            User user = getAuthenticatedUser();

            log.info("🗑️ [USER: {}] Deletando rotina ID: {}", user.getId(), id);

            RoutineText routine = routineTextRepository.findById(id)
                    .orElseThrow(() -> new RuntimeException("Rotina não encontrada"));

            if (!routine.getUser().getId().equals(user.getId())) {
                log.warn("⚠️ [USER: {}] Tentou deletar rotina de outro usuário (ID: {})",
                        user.getId(), id);
                return ResponseEntity.status(403).body(Map.of(
                        "success", false,
                        "message", "Sem permissão para deletar esta rotina"
                ));
            }

            int sequenceNumber = routine.getSequenceNumber();
            routineTextRepository.delete(routine);

            log.info("✅ [USER: {}] Rotina #{} deletada com sucesso", user.getId(), sequenceNumber);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Rotina deletada com sucesso"
            ));
        } catch (Exception e) {
            log.error("❌ Erro ao deletar rotina", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Erro ao deletar rotina: " + e.getMessage()
            ));
        }
    }
}