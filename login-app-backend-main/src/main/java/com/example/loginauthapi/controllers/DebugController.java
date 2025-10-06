package com.example.loginauthapi.controllers;

import com.example.loginauthapi.entities.User;
import com.example.loginauthapi.entities.WebInstance;
import com.example.loginauthapi.repositories.WebInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * Controller temporário para debug - REMOVER EM PRODUÇÃO
 */
@RestController
@RequestMapping("/debug")
@RequiredArgsConstructor
@Slf4j
public class DebugController {

    private final WebInstanceRepository webInstanceRepository;
    private final RestTemplate restTemplate;

    @GetMapping("/auth-info")
    public ResponseEntity<Map<String, Object>> getAuthInfo() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        Map<String, Object> info = new HashMap<>();
        info.put("authenticated", auth != null && auth.isAuthenticated());
        info.put("principalType", auth != null ? auth.getPrincipal().getClass().getName() : "null");

        if (auth != null && auth.getPrincipal() instanceof User) {
            User user = (User) auth.getPrincipal();
            info.put("userId", user.getId());
            info.put("userEmail", user.getEmail());
            info.put("userName", user.getName());

            // Buscar instâncias do usuário
            List<WebInstance> instances = webInstanceRepository.findByUserId(user.getId());
            info.put("totalInstances", instances.size());

            if (!instances.isEmpty()) {
                info.put("instances", instances.stream().map(i -> {
                    Map<String, Object> instanceInfo = new HashMap<>();
                    instanceInfo.put("id", i.getId());
                    instanceInfo.put("status", i.getStatus());
                    instanceInfo.put("suaInstancia", i.getSuaInstancia());
                    instanceInfo.put("hasClientToken", i.getClientToken() != null && !i.getClientToken().isEmpty());
                    instanceInfo.put("hasSeuToken", i.getSeuToken() != null && !i.getSeuToken().isEmpty());
                    return instanceInfo;
                }).toList());
            }
        }

        return ResponseEntity.ok(info);
    }

    @GetMapping("/test-zapi-connection")
    public ResponseEntity<Map<String, Object>> testZapiConnection() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();

            if (auth == null || !(auth.getPrincipal() instanceof User)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "message", "Usuário não autenticado"));
            }

            User user = (User) auth.getPrincipal();

            // Buscar instância ativa
            List<WebInstance> instances = webInstanceRepository.findByUserId(user.getId());

            if (instances.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "message", "Nenhuma instância encontrada para o usuário"
                ));
            }

            Optional<WebInstance> activeInstance = instances.stream()
                    .filter(i -> "ACTIVE".equalsIgnoreCase(i.getStatus()))
                    .findFirst();

            if (activeInstance.isEmpty()) {
                return ResponseEntity.ok(Map.of(
                        "success", false,
                        "message", "Nenhuma instância ACTIVE encontrada",
                        "instances", instances.stream().map(i -> Map.of(
                                "id", i.getId(),
                                "status", i.getStatus()
                        )).toList()
                ));
            }

            WebInstance instance = activeInstance.get();

            // Testar conexão com Z-API
            String url = String.format("https://api.z-api.io/instances/%s/token/%s/chats",
                    instance.getSuaInstancia(),
                    instance.getSeuToken());

            log.info("Testando conexão Z-API: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Client-Token", instance.getClientToken());
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            try {
                ResponseEntity<String> response = restTemplate.exchange(
                        url,
                        HttpMethod.GET,
                        entity,
                        String.class
                );

                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("message", "Conexão com Z-API bem-sucedida");
                result.put("statusCode", response.getStatusCode().value());
                result.put("instanceId", instance.getId());
                result.put("suaInstancia", instance.getSuaInstancia());
                result.put("responseBody", response.getBody());

                return ResponseEntity.ok(result);

            } catch (Exception e) {
                log.error("Erro ao testar Z-API", e);

                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "Erro ao conectar com Z-API");
                result.put("error", e.getMessage());
                result.put("errorType", e.getClass().getSimpleName());
                result.put("url", url);
                result.put("instanceId", instance.getId());
                result.put("suaInstancia", instance.getSuaInstancia());
                result.put("clientTokenPresent", instance.getClientToken() != null && !instance.getClientToken().isEmpty());
                result.put("seuTokenPresent", instance.getSeuToken() != null && !instance.getSeuToken().isEmpty());

                return ResponseEntity.ok(result);
            }

        } catch (Exception e) {
            log.error("Erro no teste de conexão", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "success", false,
                            "message", "Erro interno: " + e.getMessage()
                    ));
        }
    }

    @GetMapping("/test")
    public ResponseEntity<Map<String, String>> test() {
        return ResponseEntity.ok(Map.of(
                "message", "Endpoint funcionando!",
                "timestamp", java.time.LocalDateTime.now().toString()
        ));
    }
}