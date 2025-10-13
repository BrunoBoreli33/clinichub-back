package com.example.loginauthapi.controllers;

import com.example.loginauthapi.dto.MessageDTO;
import com.example.loginauthapi.entities.User;
import com.example.loginauthapi.entities.WebInstance;
import com.example.loginauthapi.repositories.WebInstanceRepository;
import com.example.loginauthapi.services.MessageService;
import com.example.loginauthapi.services.zapi.ZapiMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/dashboard/messages")
@RequiredArgsConstructor
@Slf4j
public class MessageController {

    private final MessageService messageService;
    private final ZapiMessageService zapiMessageService;
    private final WebInstanceRepository webInstanceRepository;

    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        log.debug("🔍 Verificando autenticação - Auth: {}, Authenticated: {}, Principal type: {}",
                auth != null,
                auth != null && auth.isAuthenticated(),
                auth != null ? auth.getPrincipal().getClass().getName() : "null");

        if (auth == null || !auth.isAuthenticated()) {
            log.error("❌ Usuário não autenticado - Auth é null ou não está autenticado");
            throw new RuntimeException("Usuário não autenticado");
        }

        if (!(auth.getPrincipal() instanceof User)) {
            log.error("❌ Principal não é User - Tipo: {}", auth.getPrincipal().getClass().getName());
            throw new RuntimeException("Principal não é do tipo User");
        }

        User user = (User) auth.getPrincipal();
        log.info("✅ Usuário autenticado - ID: {}, Email: {}", user.getId(), user.getEmail());
        return user;
    }

    private WebInstance getActiveInstance(User user) {
        log.debug("🔍 Buscando instância ativa para usuário: {}", user.getId());

        WebInstance instance = webInstanceRepository.findByUserId(user.getId()).stream()
                .filter(i -> "ACTIVE".equalsIgnoreCase(i.getStatus()))
                .findFirst()
                .orElseThrow(() -> {
                    log.error("❌ Nenhuma instância ativa encontrada para usuário: {}", user.getId());
                    return new RuntimeException("Nenhuma instância ativa encontrada");
                });

        log.info("✅ Instância ativa encontrada - ID: {}, SuaInstancia: {}",
                instance.getId(), instance.getSuaInstancia());
        return instance;
    }

    /**
     * GET /dashboard/messages/{chatId}
     * Buscar mensagens de um chat
     */
    @GetMapping("/{chatId}")
    public ResponseEntity<Map<String, Object>> getMessages(@PathVariable String chatId) {
        try {
            log.info("📥 Requisição para buscar mensagens - ChatId: {}", chatId);

            User user = getAuthenticatedUser();
            List<MessageDTO> messages = messageService.getMessagesByChatId(chatId, user);

            log.info("✅ Mensagens carregadas com sucesso - Total: {}", messages.size());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "messages", messages,
                    "total", messages.size()
            ));
        } catch (Exception e) {
            log.error("❌ Erro ao buscar mensagens - ChatId: {}, Erro: {}", chatId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * ✅ MODIFICADO: POST /dashboard/messages/send
     * Enviar mensagem (salva ANTES de enviar)
     */
    @PostMapping("/send")
    public ResponseEntity<Map<String, Object>> sendMessage(@RequestBody Map<String, String> body) {
        try {
            log.info("📤 Requisição para enviar mensagem");
            log.debug("Body recebido: {}", body);

            User user = getAuthenticatedUser();
            WebInstance instance = getActiveInstance(user);

            String chatId = body.get("chatId");
            String phone = body.get("phone");
            String message = body.get("message");

            if (chatId == null || chatId.trim().isEmpty()) {
                log.warn("⚠️ ChatId não informado");
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "ChatId é obrigatório"
                ));
            }

            if (phone == null || phone.trim().isEmpty()) {
                log.warn("⚠️ Phone não informado");
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Phone é obrigatório"
                ));
            }

            if (message == null || message.trim().isEmpty()) {
                log.warn("⚠️ Message não informada");
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Message é obrigatória"
                ));
            }

            // ✅ PASSO 1: Salvar mensagem NO BANCO PRIMEIRO
            log.info("💾 Salvando mensagem no banco antes de enviar");
            MessageDTO savedMessage = messageService.saveOutgoingMessage(chatId, message, user);

            // ✅ PASSO 2: Enviar via Z-API
            log.info("📨 Enviando mensagem via Z-API - Phone: {}, Instance: {}",
                    phone, instance.getSuaInstancia());

            Map<String, Object> zapiResult = zapiMessageService.sendTextMessage(instance, phone, message);

            // ✅ PASSO 3: Atualizar com o messageId real do WhatsApp
            if (zapiResult != null && zapiResult.containsKey("messageId")) {
                String realMessageId = (String) zapiResult.get("messageId");
                messageService.updateMessageIdAfterSend(savedMessage.getMessageId(), realMessageId, "SENT");
                savedMessage.setMessageId(realMessageId);
                savedMessage.setStatus("SENT");
                log.info("✅ MessageId atualizado: {}", realMessageId);
            }

            log.info("✅ Mensagem enviada e salva com sucesso");

            // ✅ PASSO 4: Retornar a mensagem salva para o frontend
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Mensagem enviada com sucesso",
                    "data", savedMessage,
                    "zapiResponse", zapiResult != null ? zapiResult : Map.of()
            ));
        } catch (Exception e) {
            log.error("❌ Erro ao enviar mensagem - Erro: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Erro ao enviar mensagem: " + e.getMessage()
            ));
        }
    }

    /**
     * ✅ NOVO: POST /dashboard/messages/send-audio
     * Enviar mensagem de áudio
     */
    @PostMapping("/send-audio")
    public ResponseEntity<Map<String, Object>> sendAudio(@RequestBody Map<String, Object> body) {
        try {
            log.info("🎤 Requisição para enviar áudio");

            User user = getAuthenticatedUser();
            WebInstance instance = getActiveInstance(user);

            String chatId = (String) body.get("chatId");
            String phone = (String) body.get("phone");
            String audioBase64 = (String) body.get("audio");
            Integer duration = body.get("duration") != null ?
                    ((Number) body.get("duration")).intValue() : null;
            Boolean waveform = body.get("waveform") != null ?
                    (Boolean) body.get("waveform") : true;

            if (chatId == null || phone == null || audioBase64 == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "ChatId, phone e audio são obrigatórios"
                ));
            }

            // ✅ PASSO 1: Salvar áudio no banco
            log.info("💾 Salvando áudio no banco antes de enviar");
            MessageDTO savedMessage = messageService.saveOutgoingAudioMessage(
                    chatId, audioBase64, duration, user
            );

            // ✅ PASSO 2: Enviar via Z-API
            log.info("📨 Enviando áudio via Z-API - Phone: {}", phone);
            Map<String, Object> zapiResult = zapiMessageService.sendAudio(
                    instance, phone, audioBase64, waveform
            );

            // ✅ PASSO 3: Atualizar com messageId real
            if (zapiResult != null && zapiResult.containsKey("messageId")) {
                String realMessageId = (String) zapiResult.get("messageId");
                messageService.updateMessageIdAfterSend(
                        savedMessage.getMessageId(), realMessageId, "SENT"
                );
                savedMessage.setMessageId(realMessageId);
                savedMessage.setStatus("SENT");
            }

            log.info("✅ Áudio enviado e salvo com sucesso");

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Áudio enviado com sucesso",
                    "data", savedMessage
            ));
        } catch (Exception e) {
            log.error("❌ Erro ao enviar áudio: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Erro ao enviar áudio: " + e.getMessage()
            ));
        }
    }

    /**
     * PUT /dashboard/messages/edit
     * Editar mensagem
     */
    @PutMapping("/edit")
    public ResponseEntity<Map<String, Object>> editMessage(@RequestBody Map<String, String> body) {
        try {
            log.info("✏️ Requisição para editar mensagem");
            log.debug("Body recebido: {}", body);

            User user = getAuthenticatedUser();
            WebInstance instance = getActiveInstance(user);

            String phone = body.get("phone");
            String editMessageId = body.get("editMessageId");
            String newMessage = body.get("message");

            if (phone == null || editMessageId == null || newMessage == null) {
                log.warn("⚠️ Parâmetros incompletos para edição");
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Phone, editMessageId e message são obrigatórios"
                ));
            }

            log.info("📝 Editando mensagem via Z-API - MessageId: {}", editMessageId);

            Map<String, Object> result = zapiMessageService.editMessage(
                    instance, phone, editMessageId, newMessage
            );

            messageService.editMessage(editMessageId, newMessage);

            log.info("✅ Mensagem editada com sucesso");

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Mensagem editada com sucesso",
                    "data", result
            ));
        } catch (Exception e) {
            log.error("❌ Erro ao editar mensagem - Erro: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Erro ao editar mensagem: " + e.getMessage()
            ));
        }
    }
}