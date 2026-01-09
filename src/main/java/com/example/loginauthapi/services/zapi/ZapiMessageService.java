package com.example.loginauthapi.services.zapi;

import com.example.loginauthapi.entities.WebInstance;
import com.example.loginauthapi.exceptions.RateLimitException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class ZapiMessageService {

    private static final String ZAPI_BASE_URL = "https://api.z-api.io";
    private final RestTemplate restTemplate;

    public boolean sendTextMessage(WebInstance instance, String phone, String message, boolean isAutomatedRoutine) {

        int delayToSend = 1;

        if (isAutomatedRoutine) {
            delayToSend = ThreadLocalRandom.current().nextInt(10, 15);
        }

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

            Map<String, Object> body = Map.of(
                    "phone", phone,
                    "message", message,
                    "delayTyping" , delayToSend,
                    "delayMessage" , delayToSend
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            var response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            var result = response.getBody();
            log.info("✅ Mensagem enviada com sucesso - MessageId: {}",
                    result != null ? result.get("messageId") : "N/A");

            return true;

        } catch (Exception e) {
            log.error("❌ Erro ao enviar mensagem", e);
            throw new RuntimeException("Erro ao enviar mensagem via Z-API: " + e.getMessage(), e);
        }
    }

    public  Map<String, Object> sendTextMessageWithRetry(WebInstance instance, String phone, String message, boolean isAutomatedRoutine) {

        int delayToSend = 1;

        if (isAutomatedRoutine) {
            delayToSend = ThreadLocalRandom.current().nextInt(10, 15);
        }

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

            Map<String, Object> body = Map.of(
                    "phone", phone,
                    "message", message,
                    "delayTyping" , delayToSend,
                    "delayMessage" , delayToSend
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            var response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            var result = response.getBody();
            log.info("✅ Mensagem enviada com sucesso - MessageId: {}",
                    result != null ? result.get("messageId") : "N/A");
            return result;

        } catch (Exception e) {
            log.error("❌ Erro ao enviar mensagem", e);
            throw new RuntimeException("Erro ao enviar mensagem via Z-API: " + e.getMessage(), e);
        }
    }

    /**
     * ✅ NOVO: Enviar mensagem de texto com reply via Z-API
     */
    public Map<String, Object> sendTextWithReply(WebInstance instance, String phone,
                                                 String message, String messageId) {
        try {
            String url = String.format("%s/instances/%s/token/%s/send-text",
                    ZAPI_BASE_URL,
                    instance.getSuaInstancia(),
                    instance.getSeuToken());

            log.info("💬 Enviando reply para: {} - ReferenceId: {}", phone, messageId);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Client-Token", instance.getClientToken());
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            Map<String, Object> body = new HashMap<>();
            body.put("phone", phone);
            body.put("message", message);
            body.put("messageId", messageId); // ID da mensagem que está sendo respondida


            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    Map.class
            );

            Map<String, Object> result = response.getBody();
            log.info("✅ Reply enviado com sucesso - MessageId: {}",
                    result != null ? result.get("messageId") : "N/A");

            return result;

        } catch (Exception e) {
            log.error("❌ Erro ao enviar reply", e);
            throw new RuntimeException("Erro ao enviar reply via Z-API: " + e.getMessage(), e);
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

    public void sendImage(WebInstance instance, String phone, String image, boolean isAutomatedRoutine) {
        try {

            int delayToSend = 1;

            if (isAutomatedRoutine) {
                delayToSend = ThreadLocalRandom.current().nextInt(10, 15);
            }

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
            body.put("delayTyping" , delayToSend);
            body.put("delayMessage" , delayToSend);
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

        } catch (Exception e) {
            log.error("❌ Erro ao enviar imagem", e);
            throw new RuntimeException("Erro ao enviar imagem via Z-API: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> sendImageWithRetry(WebInstance instance, String phone, String image, boolean isAutomatedRoutine) {
        try {

            int delayToSend = 1;

            if (isAutomatedRoutine) {
                delayToSend = ThreadLocalRandom.current().nextInt(10, 15);
            }

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
            body.put("delayTyping" , delayToSend);
            body.put("delayMessage" , delayToSend);
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


    public void sendVideo(WebInstance instance, String phone, String video, boolean isAutomatedRoutine) {
        try {

            int delayToSend = 1;

            if (isAutomatedRoutine) {
                delayToSend = ThreadLocalRandom.current().nextInt(10, 15);
            }

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
            body.put("delayTyping" , delayToSend);
            body.put("delayMessage" , delayToSend);
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

        } catch (Exception e) {
            log.error("❌ Erro ao enviar vídeo", e);
            throw new RuntimeException("Erro ao enviar vídeo via Z-API: " + e.getMessage(), e);
        }
    }

    public Map<String, Object> sendVideoWithRetry(WebInstance instance, String phone, String video, boolean isAutomatedRoutine) {
        try {

            int delayToSend = 1;

            if (isAutomatedRoutine) {
                delayToSend = ThreadLocalRandom.current().nextInt(10, 15);
            }

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
            body.put("delayTyping" , delayToSend);
            body.put("delayMessage" , delayToSend);
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

    /**
     * ✅ NOVO: Excluir mensagem via Z-API
     * DELETE https://api.z-api.io/instances/SUA_INSTANCIA/token/SEU_TOKEN/messages
     */
    public void deleteMessage(WebInstance instance, String messageId, String phone, boolean owner) {
        try {
            String url = String.format("%s/instances/%s/token/%s/messages?messageId=%s&phone=%s&owner=%s",
                    ZAPI_BASE_URL,
                    instance.getSuaInstancia(),
                    instance.getSeuToken(),
                    messageId,
                    phone,
                    owner);

            log.info("🗑️ Excluindo mensagem - MessageId: {}, Phone: {}, Owner: {}",
                    messageId, phone, owner);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Client-Token", instance.getClientToken());
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<Map> response = restTemplate.exchange(
                    url,
                    HttpMethod.DELETE,
                    entity,
                    Map.class
            );

            // A Z-API retorna 204 No Content em caso de sucesso
            if (response.getStatusCode() == HttpStatus.NO_CONTENT ||
                    response.getStatusCode() == HttpStatus.OK) {
                log.info("✅ Mensagem excluída com sucesso da Z-API - MessageId: {}", messageId);
            } else {
                log.warn("⚠️ Resposta inesperada da Z-API - Status: {}", response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("❌ Erro ao excluir mensagem da Z-API", e);
            throw new RuntimeException("Erro ao excluir mensagem via Z-API: " + e.getMessage(), e);
        }
    }

}