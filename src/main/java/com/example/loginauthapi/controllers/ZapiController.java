package com.example.loginauthapi.controllers;

import com.example.loginauthapi.dto.ChatsListResponseDTO;
import com.example.loginauthapi.entities.User;
import com.example.loginauthapi.services.ChatService;
import com.example.loginauthapi.services.zapi.ZapiQRCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/dashboard/zapi")
@RequiredArgsConstructor
@Slf4j
public class ZapiController {

    private final ZapiQRCodeService zapiQRCodeService;
    private final ChatService chatService;

    /**
     * Endpoint para obter o QR Code do WhatsApp Business
     * GET /dashboard/zapi/qr-code
     *
     * Busca a instância ativa do usuário autenticado e gera o QR Code
     */
    @GetMapping("/qr-code")
    public ResponseEntity<Map<String, Object>> getQRCode() {
        log.info("Requisição recebida para gerar QR Code");

        try {
            User authenticatedUser = getAuthenticatedUser();

            // Passa o usuário para o serviço buscar a instância ativa
            Map<String, Object> result = zapiQRCodeService.getQRCode(authenticatedUser);

            if (Boolean.TRUE.equals(result.get("success"))) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.badRequest().body(result);
            }

        } catch (Exception e) {
            log.error("Erro no controller ao buscar QR Code", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "success", false,
                            "message", "Erro interno ao processar solicitação: " + e.getMessage()
                    ));
        }
    }

    /**
     * Endpoint para verificar status da conexão
     * GET /dashboard/zapi/status
     *
     * Verifica o status da instância ativa do usuário autenticado
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getConnectionStatus() {
        log.info("Verificando status da conexão WhatsApp");

        try {
            User authenticatedUser = getAuthenticatedUser();

            // Passa o usuário para o serviço buscar a instância ativa
            Map<String, Object> result = zapiQRCodeService.getConnectionStatus(authenticatedUser);

            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Erro ao verificar status", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "success", false,
                            "connected", false,
                            "message", "Erro ao verificar status: " + e.getMessage()
                    ));
        }
    }

    /**
     * Endpoint para sincronizar e obter informações de chats
     * GET /dashboard/zapi/chats_info
     *
     * Sincroniza com a Z-API e retorna todos os chats da instância ativa do usuário
     */
    @GetMapping("/chats_info")
    public ResponseEntity<ChatsListResponseDTO> getChatsInfo() {
        log.info("Requisição recebida para obter informações de chats");

        try {
            User authenticatedUser = getAuthenticatedUser();

            // Sincronizar e buscar chats
            ChatsListResponseDTO response = chatService.syncAndGetChats(authenticatedUser);

            if (response.isSuccess()) {
                log.info("Chats carregados com sucesso para usuário {}: {} chats, {} não lidos",
                        authenticatedUser.getId(),
                        response.getTotalChats(),
                        response.getUnreadCount());
                return ResponseEntity.ok(response);
            } else {
                log.warn("Falha ao carregar chats: {}", response.getMessage());
                return ResponseEntity.badRequest().body(response);
            }

        } catch (Exception e) {
            log.error("Erro ao processar requisição de chats", e);
            return ResponseEntity.internalServerError()
                    .body(ChatsListResponseDTO.builder()
                            .success(false)
                            .message("Erro interno ao processar solicitação: " + e.getMessage())
                            .totalChats(0)
                            .unreadCount(0)
                            .build());
        }
    }

    /**
     * Endpoint para verificar o progresso do carregamento de fotos
     * GET /dashboard/zapi/chats_loading_progress
     *
     * Retorna o progresso do carregamento das fotos dos contatos
     */
    @GetMapping("/chats_loading_progress")
    public ResponseEntity<Map<String, Object>> getChatsLoadingProgress() {
        log.info("Requisição recebida para verificar progresso de carregamento");

        try {
            User authenticatedUser = getAuthenticatedUser();
            ChatService.LoadingProgress progress = chatService.getLoadingProgress(authenticatedUser.getId());

            if (progress == null) {
                // Ainda não iniciou ou já foi concluído há muito tempo
                return ResponseEntity.ok(Map.of(
                        "loading", false,
                        "completed", true,
                        "percentage", 100,
                        "loaded", 0,
                        "total", 0
                ));
            }

            return ResponseEntity.ok(Map.of(
                    "loading", true,
                    "completed", progress.isCompleted(),
                    "percentage", progress.getPercentage(),
                    "loaded", progress.getLoaded(),
                    "total", progress.getTotal()
            ));

        } catch (Exception e) {
            log.error("Erro ao verificar progresso de carregamento", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "loading", false,
                            "completed", false,
                            "error", e.getMessage()
                    ));
        }
    }

    /**
     * Endpoint alternativo para buscar chats apenas do banco (sem sincronizar)
     * GET /dashboard/zapi/chats
     *
     * Mais rápido, não faz requisições à Z-API
     */
    @GetMapping("/chats")
    public ResponseEntity<ChatsListResponseDTO> getChats() {
        log.info("Requisição recebida para obter chats do banco");

        try {
            User authenticatedUser = getAuthenticatedUser();
            ChatsListResponseDTO response = chatService.getChatsFromDatabase(authenticatedUser);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Erro ao buscar chats do banco", e);
            return ResponseEntity.internalServerError()
                    .body(ChatsListResponseDTO.builder()
                            .success(false)
                            .message("Erro ao buscar chats: " + e.getMessage())
                            .totalChats(0)
                            .unreadCount(0)
                            .build());
        }
    }

    /**
     * Endpoint para atualizar a coluna de um chat
     * PUT /dashboard/zapi/chats/{chatId}/column
     */
    @PutMapping("/chats/{chatId}/column")
    public ResponseEntity<Map<String, Object>> updateChatColumn(
            @PathVariable String chatId,
            @RequestBody Map<String, String> body) {

        try {
            String column = body.get("column");

            if (column == null || column.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Coluna não informada"
                ));
            }

            // ✅ NOVA VALIDAÇÃO: Bloquear movimentação manual para Repescagem
            if ("Repescagem".equalsIgnoreCase(column)) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "A coluna 'Repescagem' é exclusiva para o sistema de rotinas automáticas. Não é permitido mover conversas manualmente para esta coluna."
                ));
            }

            chatService.updateChatColumn(chatId, column);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Coluna atualizada com sucesso"
            ));

        } catch (Exception e) {
            log.error("Erro ao atualizar coluna do chat {}", chatId, e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Erro ao atualizar coluna: " + e.getMessage()
            ));
        }
    }

    // ============================================
    // ✅ NOVOS ENDPOINTS DE GERENCIAMENTO DE TAGS
    // ============================================

    /**
     * Endpoint para adicionar tags a um chat
     * POST /dashboard/zapi/chats/{chatId}/tags
     */
    @PostMapping("/chats/{chatId}/tags")
    public ResponseEntity<Map<String, Object>> addTagsToChat(
            @PathVariable String chatId,
            @RequestBody Map<String, List<String>> body) {

        try {
            User user = getAuthenticatedUser();
            List<String> tagIds = body.get("tagIds");

            if (tagIds == null || tagIds.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Lista de tags não informada"
                ));
            }

            chatService.addTagsToChat(chatId, tagIds, user);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Tags adicionadas com sucesso"
            ));

        } catch (Exception e) {
            log.error("Erro ao adicionar tags ao chat {}", chatId, e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Endpoint para remover tag de um chat
     * DELETE /dashboard/zapi/chats/{chatId}/tags/{tagId}
     */
    @DeleteMapping("/chats/{chatId}/tags/{tagId}")
    public ResponseEntity<Map<String, Object>> removeTagFromChat(
            @PathVariable String chatId,
            @PathVariable String tagId) {

        try {
            User user = getAuthenticatedUser();
            chatService.removeTagFromChat(chatId, tagId, user);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Tag removida com sucesso"
            ));

        } catch (Exception e) {
            log.error("Erro ao remover tag {} do chat {}", tagId, chatId, e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * Endpoint para substituir todas as tags de um chat
     * PUT /dashboard/zapi/chats/{chatId}/tags
     */
    @PutMapping("/chats/{chatId}/tags")
    public ResponseEntity<Map<String, Object>> setTagsForChat(
            @PathVariable String chatId,
            @RequestBody Map<String, List<String>> body) {

        try {
            User user = getAuthenticatedUser();
            List<String> tagIds = body.get("tagIds");

            chatService.setTagsForChat(chatId, tagIds, user);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Tags atualizadas com sucesso"
            ));

        } catch (Exception e) {
            log.error("Erro ao atualizar tags do chat {}", chatId, e);
            return ResponseEntity.badRequest().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    // ============================================
    // MÉTODO AUXILIAR
    // ============================================

    /**
     * Método auxiliar para obter o usuário autenticado
     */
    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            log.error("Autenticação não encontrada ou inválida");
            throw new RuntimeException("Usuário não autenticado");
        }

        Object principal = authentication.getPrincipal();

        log.debug("Principal type: {}", principal.getClass().getName());

        // O SecurityFilter injeta o User diretamente como principal
        if (principal instanceof User) {
            User user = (User) principal;
            log.debug("Usuário autenticado: ID={}, Email={}", user.getId(), user.getEmail());
            return user;
        }

        // Fallback: se for UserDetails do Spring Security
        if (principal instanceof org.springframework.security.core.userdetails.UserDetails) {
            String email = ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername();
            log.error("Principal é UserDetails, mas deveria ser User. Email: {}", email);
            throw new RuntimeException("Configuração incorreta - User não injetado no contexto");
        }

        log.error("Tipo de principal não reconhecido: {}", principal.getClass().getName());
        throw new RuntimeException("Usuário não encontrado no contexto de segurança");
    }
}