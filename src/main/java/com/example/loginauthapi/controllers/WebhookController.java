package com.example.loginauthapi.controllers;

import com.example.loginauthapi.services.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/webhook")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final WebhookService webhookService;

    /**
     * ✅ NOVO: Endpoint único para receber TODAS as mensagens (enviadas e recebidas)
     * Processa tanto mensagens incoming quanto outcoming através do campo 'fromMe'
     */
    @PostMapping("/message")
    public ResponseEntity<Map<String, Object>> handleMessage(@RequestBody Map<String, Object> payload) {
        try {
            log.info("📨 Webhook de mensagem recebido");
            log.debug("Payload: {}", payload);

            // Processar a mensagem (incoming ou outcoming baseado em fromMe)
            webhookService.processMessage(payload);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Mensagem processada com sucesso"
            ));
        } catch (Exception e) {
            log.error("❌ Erro ao processar webhook de mensagem", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }


}