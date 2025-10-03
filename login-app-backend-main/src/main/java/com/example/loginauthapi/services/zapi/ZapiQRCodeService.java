package com.example.loginauthapi.services.zapi;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class ZapiQRCodeService {

    @Value("${zapi.instance}")
    private String zapiInstance;

    @Value("${zapi.token}")
    private String zapiToken;

    @Value("${zapi.client-token}")
    private String zapiClientToken;

    @Value("${zapi.base-url:https://api.z-api.io}")
    private String zapiBaseUrl;

    private final RestTemplate restTemplate;

    public ZapiQRCodeService() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Busca o QR Code da instância do WhatsApp Business
     * @return Mapa com os dados do QR Code (base64, status, etc)
     */
    public Map<String, Object> getQRCode() {
        Map<String, Object> result = new HashMap<>();

        // 1. Primeiro verifica status
        Map<String, Object> status = getConnectionStatus();
        if (Boolean.TRUE.equals(status.get("connected"))) {
            result.put("success", true);
            result.put("connected", true);
            result.put("message", "WhatsApp já conectado");
            return result;
        }

        // 2. Caso não esteja conectado, busca QR Code
        String url = String.format("%s/instances/%s/token/%s/qr-code/image",
                zapiBaseUrl, zapiInstance, zapiToken);

        try {
            log.info("Buscando QR Code da Z-API: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("client-token", zapiClientToken);

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {}
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();

                if (body.containsKey("value")) {
                    result.put("qrCode", body.get("value"));
                    result.put("success", true);
                    result.put("connected", false);
                    result.put("message", "QR Code gerado com sucesso");
                } else {
                    result.put("success", false);
                    result.put("connected", false);
                    result.put("message", "QR Code não disponível no momento");
                }
            } else {
                result.put("success", false);
                result.put("connected", false);
                result.put("message", "Erro ao obter QR Code");
            }

            return result;

        } catch (Exception e) {
            log.error("Erro ao buscar QR Code", e);
            result.put("success", false);
            result.put("connected", false);
            result.put("message", "Erro interno ao processar solicitação");
            return result;
        }
    }

    /**
     * Verifica o status da conexão do WhatsApp
     * @return Mapa com informações do status
     */
    public Map<String, Object> getConnectionStatus() {
        // URL correta com token na rota
        String url = String.format("%s/instances/%s/token/%s/status",
                zapiBaseUrl, zapiInstance, zapiToken);

        try {
            log.info("Verificando status da conexão Z-API: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("client-token", zapiClientToken);

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {}
            );

            Map<String, Object> result = new HashMap<>();

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                result.put("connected", body.getOrDefault("connected", false));
                result.put("status", body.getOrDefault("status", "disconnected"));
                result.put("success", true);
                log.info("Status obtido: {}", body);
            } else {
                result.put("connected", false);
                result.put("success", false);
                result.put("message", "Erro ao obter status");
                log.error("Resposta inválida da Z-API: status={}", response.getStatusCode());
            }

            return result;

        } catch (Exception e) {
            log.error("Erro ao verificar status da conexão", e);

            Map<String, Object> errorResult = new HashMap<>();
            errorResult.put("connected", false);
            errorResult.put("success", false);
            errorResult.put("message", e.getMessage());
            return errorResult;
        }
    }
}
