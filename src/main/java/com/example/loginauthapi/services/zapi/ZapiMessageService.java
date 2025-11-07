package com.example.loginauthapi.services.zapi;

import com.example.loginauthapi.entities.WebInstance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ZapiMessageService {

    private static final String ZAPI_BASE_URL = "https://api.z-api.io";
    private final RestTemplate restTemplate;

    /**
     * ✅ MODIFICADO: Enviar mensagem de texto via Z-API
     */
    public Map<String, Object> sendTextMessage(WebInstance instance, String phone, String message) {
        try {
            String url = String.format("%s/instances/%s/token/%s/send-text",
                    ZAPI_BASE_URL,
                    instance.getSuaInstancia(),
                    instance.getSeuToken());

            log.info("📨 Enviando mensagem para: {}", phone);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Client-Token", instance.getClientToken());
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            Map<String, String> body = Map.of(
                    "phone", phone,
                    "message", message
            );

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            Map<String, Object> result = response.getBody();
            log.info("✅ Mensagem enviada com sucesso - MessageId: {}",
                    result != null ? result.get("messageId") : "N/A");

            return result;

        } catch (Exception e) {
            log.error("❌ Erro ao enviar mensagem", e);
            throw new RuntimeException("Erro ao enviar mensagem via Z-API: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ NOVO: Enviar áudio via Z-API
     */
    public Map<String, Object> sendAudio(WebInstance instance, String phone, String audioBase64, Boolean waveform) {
        try {
            String url = String.format("%s/instances/%s/token/%s/send-audio",
                    ZAPI_BASE_URL,
                    instance.getSuaInstancia(),
                    instance.getSeuToken());

            log.info("🎤 Enviando áudio para: {}", phone);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Client-Token", instance.getClientToken());
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            Map<String, Object> body = new HashMap<>();
            body.put("phone", phone);
            body.put("audio", audioBase64);
            body.put("viewOnce", false);
            body.put("waveform", waveform != null ? waveform : true);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            Map<String, Object> result = response.getBody();
            log.info("✅ Áudio enviado com sucesso - MessageId: {}",
                    result != null ? result.get("messageId") : "N/A");

            return result;

        } catch (Exception e) {
            log.error("❌ Erro ao enviar áudio", e);
            throw new RuntimeException("Erro ao enviar áudio via Z-API: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ NOVO: Enviar imagem via Z-API
     */
    public Map<String, Object> sendImage(WebInstance instance, String phone, String image) {
        try {
            String url = String.format("%s/instances/%s/token/%s/send-image",
                    ZAPI_BASE_URL,
                    instance.getSuaInstancia(),
                    instance.getSeuToken());

            log.info("📷 Enviando imagem para: {}", phone);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Client-Token", instance.getClientToken());
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            Map<String, Object> body = new HashMap<>();
            body.put("phone", phone);
            body.put("image", image);
            body.put("viewOnce", false);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            Map<String, Object> result = response.getBody();
            log.info("✅ Imagem enviada com sucesso - MessageId: {}",
                    result != null ? result.get("messageId") : "N/A");

            return result;

        } catch (Exception e) {
            log.error("❌ Erro ao enviar imagem", e);
            throw new RuntimeException("Erro ao enviar imagem via Z-API: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ NOVO: Enviar vídeo via Z-API
     */
    public Map<String, Object> sendVideo(WebInstance instance, String phone, String video) {
        try {
            String url = String.format("%s/instances/%s/token/%s/send-video",
                    ZAPI_BASE_URL,
                    instance.getSuaInstancia(),
                    instance.getSeuToken());

            log.info("🎥 Enviando vídeo para: {}", phone);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Client-Token", instance.getClientToken());
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            Map<String, Object> body = new HashMap<>();
            body.put("phone", phone);
            body.put("video", video);
            body.put("viewOnce", false);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            Map<String, Object> result = response.getBody();
            log.info("✅ Vídeo enviado com sucesso - MessageId: {}",
                    result != null ? result.get("messageId") : "N/A");

            return result;

        } catch (Exception e) {
            log.error("❌ Erro ao enviar vídeo", e);
            throw new RuntimeException("Erro ao enviar vídeo via Z-API: " + e.getMessage(), e);
        }
    }

    /**
     * Editar mensagem via Z-API
     */
    public Map<String, Object> editMessage(WebInstance instance, String phone,
                                           String editMessageId, String newMessage) {
        try {
            String url = String.format("%s/instances/%s/token/%s/send-text",
                    ZAPI_BASE_URL,
                    instance.getSuaInstancia(),
                    instance.getSeuToken());

            log.info("📝 Editando mensagem: {}", editMessageId);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Client-Token", instance.getClientToken());
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            Map<String, String> body = Map.of(
                    "phone", phone,
                    "message", newMessage,
                    "editMessageId", editMessageId
            );

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            log.info("✅ Mensagem editada com sucesso");
            return response.getBody();

        } catch (Exception e) {
            log.error("❌ Erro ao editar mensagem", e);
            throw new RuntimeException("Erro ao editar mensagem via Z-API: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ NOVO: Enviar documento via Z-API
     */
    public Map<String, Object> sendDocument(WebInstance instance, String phone,
                                            String document, String fileName,
                                            String caption, String extension) {
        try {
            String url = String.format("%s/instances/%s/token/%s/send-document/%s",
                    ZAPI_BASE_URL,
                    instance.getSuaInstancia(),
                    instance.getSeuToken(),
                    extension);

            log.info("📄 Enviando documento para: {} - Extension: {}", phone, extension);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Client-Token", instance.getClientToken());
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            Map<String, Object> body = new HashMap<>();
            body.put("phone", phone);
            body.put("document", document);

            if (fileName != null && !fileName.isEmpty()) {
                body.put("fileName", fileName);
            }

            if (caption != null && !caption.isEmpty()) {
                body.put("caption", caption);
            }

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            Map<String, Object> result = response.getBody();
            log.info("✅ Documento enviado com sucesso - MessageId: {}",
                    result != null ? result.get("messageId") : "N/A");

            return result;

        } catch (Exception e) {
            log.error("❌ Erro ao enviar documento", e);
            throw new RuntimeException("Erro ao enviar documento via Z-API: " + e.getMessage(), e);
        }
    }
}