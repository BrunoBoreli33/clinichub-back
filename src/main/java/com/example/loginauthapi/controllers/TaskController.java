package com.example.loginauthapi.controllers;

import com.example.loginauthapi.dto.TaskDTO;
import com.example.loginauthapi.dto.TaskRequestDTO;
import com.example.loginauthapi.entities.User;
import com.example.loginauthapi.services.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
@Slf4j
public class TaskController {

    private final TaskService taskService;

    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof User)) {
            throw new RuntimeException("Usuário não autenticado");
        }
        return (User) auth.getPrincipal();
    }

    /**
     * POST /api/tasks - Criar nova tarefa
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createTask(@RequestBody TaskRequestDTO request) {
        try {
            User user = getAuthenticatedUser();
            TaskDTO task = taskService.createTask(request, user);

            log.info("✅ Tarefa criada via API: {}", task.getId());

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "message", "Tarefa criada com sucesso",
                    "task", task
            ));
        } catch (Exception e) {
            log.error("❌ Erro ao criar tarefa: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * GET /api/tasks/{id} - Buscar tarefa por ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getTask(@PathVariable String id) {
        try {
            User user = getAuthenticatedUser();
            TaskDTO task = taskService.getTaskById(id, user);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "task", task
            ));
        } catch (Exception e) {
            log.error("❌ Erro ao buscar tarefa: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * GET /api/tasks/chat/{chatId} - Buscar tarefa por chatId
     */
    @GetMapping("/chat/{chatId}")
    public ResponseEntity<Map<String, Object>> getTaskByChatId(@PathVariable String chatId) {
        try {
            User user = getAuthenticatedUser();
            TaskDTO task = taskService.getTaskByChatId(chatId, user);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "task", task
            ));
        } catch (Exception e) {
            log.error("❌ Erro ao buscar tarefa por chat: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * PUT /api/tasks/{id} - Atualizar tarefa
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateTask(
            @PathVariable String id,
            @RequestBody TaskRequestDTO request) {
        try {
            User user = getAuthenticatedUser();
            TaskDTO task = taskService.updateTask(id, request, user);

            log.info("✅ Tarefa atualizada via API: {}", id);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Tarefa atualizada com sucesso",
                    "task", task
            ));
        } catch (Exception e) {
            log.error("❌ Erro ao atualizar tarefa: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * DELETE /api/tasks/{id} - Excluir tarefa
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteTask(@PathVariable String id) {
        try {
            User user = getAuthenticatedUser();
            taskService.deleteTask(id, user);

            log.info("✅ Tarefa excluída via API: {}", id);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Tarefa excluída com sucesso"
            ));
        } catch (Exception e) {
            log.error("❌ Erro ao excluir tarefa: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * POST /api/tasks/{id}/complete - Marcar tarefa como concluída
     */
    @PostMapping("/{id}/complete")
    public ResponseEntity<Map<String, Object>> completeTask(@PathVariable String id) {
        try {
            User user = getAuthenticatedUser();
            TaskDTO task = taskService.completeTask(id, user);

            log.info("✅ Tarefa marcada como concluída via API: {}", id);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Tarefa concluída com sucesso",
                    "task", task
            ));
        } catch (Exception e) {
            log.error("❌ Erro ao concluir tarefa: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }
}