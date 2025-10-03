package com.example.loginauthapi.controllers;


import com.example.loginauthapi.services.zapi.ZapiQRCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/dashboard/zapi")
@RequiredArgsConstructor
@Slf4j
public class ZapiController {

    private final ZapiQRCodeService zapiQRCodeService;

    /**
     * Endpoint para obter o QR Code do WhatsApp Business
     * GET /api/whatsapp/qr-code
     */
    @GetMapping("/qr-code")
    public ResponseEntity<Map<String, Object>> getQRCode() {
        log.info("Requisição recebida para gerar QR Code");

        try {
            Map<String, Object> result = zapiQRCodeService.getQRCode();

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
                            "message", "Erro interno ao processar solicitação"
                    ));
        }
    }

    /**
     * Endpoint para verificar status da conexão
     * GET /api/whatsapp/status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getConnectionStatus() {
        log.info("Verificando status da conexão WhatsApp");

        try {
            Map<String, Object> result = zapiQRCodeService.getConnectionStatus();
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("Erro ao verificar status", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "success", false,
                            "connected", false,
                            "message", "Erro ao verificar status"
                    ));
        }
    }

}
