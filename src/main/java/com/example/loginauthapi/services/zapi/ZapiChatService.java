package com.example.loginauthapi.services.zapi;

import com.example.loginauthapi.dto.zapi.ZapiChatDetailResponseDTO;
import com.example.loginauthapi.dto.zapi.ZapiChatItemDTO;
import com.example.loginauthapi.entities.WebInstance;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ZapiChatService {

    private static final String ZAPI_BASE_URL = "https://api.z-api.io";
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Busca todos os chats da instância
     * Documentação: https://developer.z-api.io/chats/get-chats
     */
    public List<ZapiChatItemDTO> getChats(WebInstance instance) {
        try {
            String url = String.format("%s/instances/%s/token/%s/chats?page=1&pageSize=256",
                    ZAPI_BASE_URL,
                    instance.getSuaInstancia(),
                    instance.getSeuToken());

            log.info("=== REQUISIÇÃO Z-API GET CHATS ===");
            log.info("URL: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Client-Token", instance.getClientToken());
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    String.class
            );

            log.info("Status da resposta: {}", response.getStatusCode());
            log.debug("Response body: {}", response.getBody());

            if (response.getBody() != null && !response.getBody().isEmpty()) {
                List<ZapiChatItemDTO> chats = objectMapper.readValue(
                        response.getBody(),
                        new TypeReference<List<ZapiChatItemDTO>>() {}
                );

                log.info("Total de chats encontrados: {}", chats.size());
                return chats;
            }

            log.warn("Resposta da Z-API está vazia");
            return Collections.emptyList();

        } catch (HttpClientErrorException e) {
            log.error("Erro HTTP 4xx ao buscar chats da Z-API");
            log.error("Status: {}", e.getStatusCode());
            log.error("Body: {}", e.getResponseBodyAsString());
            throw new RuntimeException("Erro ao buscar chats da Z-API: " + e.getMessage(), e);

        } catch (HttpServerErrorException e) {
            log.error("Erro HTTP 5xx ao buscar chats da Z-API");
            log.error("Status: {}", e.getStatusCode());
            log.error("Body: {}", e.getResponseBodyAsString());
            throw new RuntimeException("Erro no servidor Z-API: " + e.getMessage(), e);

        } catch (Exception e) {
            log.error("Erro inesperado ao buscar chats da Z-API", e);
            log.error("Tipo da exceção: {}", e.getClass().getName());
            log.error("Mensagem: {}", e.getMessage());
            throw new RuntimeException("Erro ao buscar chats da Z-API: " + e.getMessage(), e);
        }
    }

    /**
     * Busca detalhes de um chat específico (incluindo foto de perfil)
     * Documentação: https://developer.z-api.io/chats/get-metadata-chat
     */
    public ZapiChatDetailResponseDTO getChatDetail(WebInstance instance, String phone) {
        try {
            String url = String.format("%s/instances/%s/token/%s/chats/%s",
                    ZAPI_BASE_URL,
                    instance.getSuaInstancia(),
                    instance.getSeuToken(),
                    phone);

            log.debug("=== REQUISIÇÃO Z-API GET CHAT DETAIL ===");
            log.debug("URL: {}", url);
            log.debug("Phone: {}", phone);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Client-Token", instance.getClientToken());
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<ZapiChatDetailResponseDTO> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    entity,
                    ZapiChatDetailResponseDTO.class
            );

            log.debug("Status da resposta chat detail: {}", response.getStatusCode());

            if (response.getBody() != null) {
                ZapiChatDetailResponseDTO detail = response.getBody();

                if (detail.getProfileThumbnail() != null && !detail.getProfileThumbnail().isEmpty()) {
                    log.debug("Foto de perfil encontrada para {}: {}", phone, detail.getProfileThumbnail());
                } else {
                    log.debug("Nenhuma foto de perfil disponível para: {}", phone);
                }

                return detail;
            }

            return null;

        } catch (HttpClientErrorException e) {
            log.warn("Erro HTTP 4xx ao buscar detalhes do chat {}: {} - {}",
                    phone, e.getStatusCode(), e.getMessage());
            return null;

        } catch (Exception e) {
            log.warn("Erro ao buscar detalhes do chat {}: {}", phone, e.getMessage());
            return null;
        }
    }
}