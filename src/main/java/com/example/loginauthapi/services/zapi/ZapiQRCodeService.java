package com.example.loginauthapi.services.zapi;

import com.example.loginauthapi.entities.User;
import com.example.loginauthapi.entities.WebInstance;
import com.example.loginauthapi.repositories.WebInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ZapiQRCodeService {

    @Value("${zapi.base-url:https://api.z-api.io}")
    private String zapiBaseUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final WebInstanceRepository webInstanceRepository;

    /**
     * Busca o QR Code da instância ativa do usuário
     * @param user Usuário autenticado
     * @return Mapa com os dados do QR Code (base64, status, etc)
     */
    public Map<String, Object> getQRCode(User user) {
        Map<String, Object> result = new HashMap<>();

        // Busca a instância ativa do usuário
        Optional<WebInstance> activeInstanceOpt = getActiveInstanceByUser(user);

        if (activeInstanceOpt.isEmpty()) {
            result.put("success", false);
            result.put("connected", false);
            result.put("message", "Nenhuma instância ativa encontrada para o usuário");
            log.warn("Usuário {} não possui instância com status ACTIVE", user.getId());
            return result;
        }

        WebInstance instance = activeInstanceOpt.get();

        // Valida se a instância tem os dados necessários
        if (instance.getSuaInstancia() == null || instance.getSeuToken() == null) {
            result.put("success", false);
            result.put("connected", false);
            result.put("message", "Instância com dados incompletos");
            log.error("Instância {} tem dados incompletos", instance.getId());
            return result;
        }

        // 1. Primeiro verifica status
        Map<String, Object> status = getConnectionStatus(user);
        if (Boolean.TRUE.equals(status.get("connected"))) {
            result.put("success", true);
            result.put("connected", true);
            result.put("message", "WhatsApp já conectado");
            result.put("instanceId", instance.getId());
            result.put("instanceName", instance.getSuaInstancia());
            return result;
        }

        // 2. Caso não esteja conectado, busca QR Code
        String url = String.format("%s/instances/%s/token/%s/qr-code/image",
                zapiBaseUrl, instance.getSuaInstancia(), instance.getSeuToken());

        try {
            log.info("Buscando QR Code da Z-API para usuário {} - Instância: {}",
                    user.getId(), instance.getSuaInstancia());

            HttpHeaders headers = new HttpHeaders();
            if (instance.getClientToken() != null) {
                headers.set("client-token", instance.getClientToken());
            }

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
                    result.put("instanceId", instance.getId());
                    result.put("instanceName", instance.getSuaInstancia());
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
            log.error("Erro ao buscar QR Code para usuário {}", user.getId(), e);
            result.put("success", false);
            result.put("connected", false);
            result.put("message", "Erro interno ao processar solicitação: " + e.getMessage());
            return result;
        }
    }

    /**
     * Verifica o status da conexão do WhatsApp da instância ativa do usuário
     * @param user Usuário autenticado
     * @return Mapa com informações do status
     */
    public Map<String, Object> getConnectionStatus(User user) {
        Map<String, Object> result = new HashMap<>();

        // Busca a instância ativa do usuário
        Optional<WebInstance> activeInstanceOpt = getActiveInstanceByUser(user);

        if (activeInstanceOpt.isEmpty()) {
            result.put("connected", false);
            result.put("success", false);
            result.put("message", "Nenhuma instância ativa encontrada");
            log.warn("Usuário {} não possui instância com status ACTIVE", user.getId());
            return result;
        }

        WebInstance instance = activeInstanceOpt.get();

        // Valida dados da instância
        if (instance.getSuaInstancia() == null || instance.getSeuToken() == null) {
            result.put("connected", false);
            result.put("success", false);
            result.put("message", "Instância com dados incompletos");
            return result;
        }

        String url = String.format("%s/instances/%s/token/%s/status",
                zapiBaseUrl, instance.getSuaInstancia(), instance.getSeuToken());

        try {
            log.info("Verificando status da conexão Z-API para usuário {} - Instância: {}",
                    user.getId(), instance.getSuaInstancia());

            HttpHeaders headers = new HttpHeaders();
            if (instance.getClientToken() != null) {
                headers.set("client-token", instance.getClientToken());
            }

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    new ParameterizedTypeReference<>() {}
            );

            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                result.put("connected", body.getOrDefault("connected", false));
                result.put("status", body.getOrDefault("status", "disconnected"));
                result.put("success", true);
                result.put("instanceId", instance.getId());
                result.put("instanceName", instance.getSuaInstancia());
                log.info("Status obtido para usuário {}: {}", user.getId(), body);
            } else {
                result.put("connected", false);
                result.put("success", false);
                result.put("message", "Erro ao obter status");
                log.error("Resposta inválida da Z-API para usuário {}: status={}",
                        user.getId(), response.getStatusCode());
            }

            return result;

        } catch (Exception e) {
            log.error("Erro ao verificar status da conexão para usuário {}", user.getId(), e);

            result.put("connected", false);
            result.put("success", false);
            result.put("message", "Erro ao verificar status: " + e.getMessage());
            return result;
        }
    }

    /**
     * Busca a instância ativa do usuário (status = "ACTIVE")
     * @param user Usuário autenticado
     * @return Optional com a WebInstance ativa
     */
    private Optional<WebInstance> getActiveInstanceByUser(User user) {
        List<WebInstance> userInstances = webInstanceRepository.findByUserId(user.getId());

        log.info("Total de instâncias encontradas para usuário {}: {}", user.getId(), userInstances.size());

        // Log detalhado de cada instância
        for (WebInstance instance : userInstances) {
            log.info("Instância ID: {}, Status: '{}', Status é null? {}, SuaInstancia: {}",
                    instance.getId(),
                    instance.getStatus(),
                    instance.getStatus() == null,
                    instance.getSuaInstancia());
        }

        Optional<WebInstance> activeInstance = userInstances.stream()
                .filter(instance -> {
                    String status = instance.getStatus();
                    if (status == null) {
                        log.warn("Instância {} tem status NULL", instance.getId());
                        return false;
                    }
                    // Remove espaços e compara
                    boolean isActive = "ACTIVE".equalsIgnoreCase(status.trim());
                    log.debug("Instância {} - Status: '{}' - É ACTIVE? {}",
                            instance.getId(), status, isActive);
                    return isActive;
                })
                .findFirst();

        if (activeInstance.isEmpty()) {
            log.warn("Nenhuma instância ACTIVE encontrada após filtro para usuário {}", user.getId());
        } else {
            log.info("Instância ACTIVE encontrada: {}", activeInstance.get().getId());
        }

        return activeInstance;
    }

    /**
     * Busca a instância ativa do usuário (método público para uso externo)
     * @param user Usuário autenticado
     * @return WebInstance ativa ou null
     */
    public WebInstance getActiveInstance(User user) {
        return getActiveInstanceByUser(user).orElse(null);
    }
}