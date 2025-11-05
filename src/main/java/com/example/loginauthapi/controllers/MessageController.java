package com.example.loginauthapi.controllers;

import com.example.loginauthapi.dto.AudioDTO;
import com.example.loginauthapi.dto.MessageDTO;
import com.example.loginauthapi.dto.PhotoDTO;
import com.example.loginauthapi.dto.VideoDTO;
import com.example.loginauthapi.entities.User;
import com.example.loginauthapi.entities.WebInstance;
import com.example.loginauthapi.repositories.WebInstanceRepository;
import com.example.loginauthapi.services.AudioService;
import com.example.loginauthapi.services.MessageService;
import com.example.loginauthapi.services.PhotoService;
import com.example.loginauthapi.services.VideoService;
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
    private final AudioService audioService;
    private final PhotoService photoService;
    private final VideoService videoService;
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
     * ✅ MODIFICADO: GET /dashboard/messages/{chatId}
     * Buscar mensagens, áudios, fotos E vídeos de um chat
     */
    @GetMapping("/{chatId}")
    public ResponseEntity<Map<String, Object>> getMessages(@PathVariable String chatId) {
        try {
            log.info("📥 Requisição para buscar mensagens - ChatId: {}", chatId);

            User user = getAuthenticatedUser();
            List<MessageDTO> messages = messageService.getMessagesByChatId(chatId, user);

            // ✅ Buscar áudios
            List<AudioDTO> audios = audioService.getAudiosByChatId(chatId);

            // ✅ Buscar fotos
            List<PhotoDTO> photos = photoService.getPhotosByChatId(chatId);

            // ✅ Buscar vídeos
            List<VideoDTO> videos = videoService.getVideosByChatId(chatId);

            log.info("✅ Dados carregados - Mensagens: {}, Áudios: {}, Fotos: {}, Vídeos: {}",
                    messages.size(), audios.size(), photos.size(), videos.size());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "messages", messages,
                    "audios", audios,
                    "photos", photos,
                    "videos", videos,
                    "totalMessages", messages.size(),
                    "totalAudios", audios.size(),
                    "totalPhotos", photos.size(),
                    "totalVideos", videos.size()
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
     * ✅ NOVO: GET /dashboard/messages/gallery
     * Buscar fotos salvas na galeria do usuário
     */
    @GetMapping("/gallery")
    public ResponseEntity<Map<String, Object>> getGalleryPhotos() {
        try {
            log.info("🖼️ Requisição para buscar fotos da galeria");

            User user = getAuthenticatedUser();
            List<PhotoDTO> photos = photoService.getSavedGalleryPhotos(user.getId());

            log.info("✅ Fotos da galeria carregadas - Total: {}", photos.size());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "photos", photos,
                    "total", photos.size()
            ));
        } catch (Exception e) {
            log.error("❌ Erro ao buscar galeria - Erro: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * ✅ NOVO: GET /dashboard/messages/gallery/all
     * Buscar fotos e vídeos salvos na galeria do usuário
     */
    @GetMapping("/gallery/all")
    public ResponseEntity<Map<String, Object>> getGalleryAll() {
        try {
            log.info("🖼️ Requisição para buscar galeria completa (fotos + vídeos)");

            User user = getAuthenticatedUser();
            List<PhotoDTO> photos = photoService.getSavedGalleryPhotos(user.getId());
            List<VideoDTO> videos = videoService.getSavedGalleryVideos(user.getId());

            log.info("✅ Galeria carregada - Fotos: {}, Vídeos: {}", photos.size(), videos.size());

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "photos", photos,
                    "videos", videos,
                    "totalPhotos", photos.size(),
                    "totalVideos", videos.size()
            ));
        } catch (Exception e) {
            log.error("❌ Erro ao buscar galeria - Erro: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * ✅ NOVO: PUT /dashboard/messages/photos/{photoId}/toggle-gallery
     * Marcar/desmarcar foto como salva na galeria
     */
    @PutMapping("/photos/{photoId}/toggle-gallery")
    public ResponseEntity<Map<String, Object>> togglePhotoInGallery(@PathVariable String photoId) {
        try {
            log.info("🖼️ Requisição para salvar/remover foto da galeria - PhotoId: {}", photoId);

            PhotoDTO photo = photoService.togglePhotoInGallery(photoId);

            log.info("✅ Foto {} da galeria com sucesso",
                    photo.getSavedInGallery() ? "salva na" : "removida da");

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", photo.getSavedInGallery() ? "Foto salva na galeria" : "Foto removida da galeria",
                    "photo", photo
            ));
        } catch (Exception e) {
            log.error("❌ Erro ao salvar/remover foto da galeria - Erro: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * ✅ NOVO: PUT /dashboard/messages/videos/{videoId}/toggle-gallery
     * Marcar/desmarcar vídeo como salvo na galeria
     */
    @PutMapping("/videos/{videoId}/toggle-gallery")
    public ResponseEntity<Map<String, Object>> toggleVideoInGallery(@PathVariable String videoId) {
        try {
            log.info("🎥 Requisição para salvar/remover vídeo da galeria - VideoId: {}", videoId);

            VideoDTO video = videoService.toggleVideoInGallery(videoId);

            log.info("✅ Vídeo {} da galeria com sucesso",
                    video.getSavedInGallery() ? "salvo na" : "removido da");

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", video.getSavedInGallery() ? "Vídeo salvo na galeria" : "Vídeo removido da galeria",
                    "video", video
            ));
        } catch (Exception e) {
            log.error("❌ Erro ao salvar/remover vídeo da galeria - Erro: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        }
    }

    /**
     * POST /dashboard/messages/send
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
     * POST /dashboard/messages/send-audio
     * Enviar mensagem de áudio (usando AudioService ao invés de MessageService)
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

            // ✅ MODIFICADO: Usar audioService ao invés de messageService
            log.info("💾 Salvando áudio no banco antes de enviar");
            AudioDTO savedAudio = audioService.saveOutgoingAudio(chatId, phone, duration, "");

            // ✅ PASSO 2: Enviar via Z-API
            log.info("📨 Enviando áudio via Z-API - Phone: {}", phone);
            Map<String, Object> zapiResult = zapiMessageService.sendAudio(
                    instance, phone, audioBase64, waveform
            );

            // ✅ PASSO 3: Atualizar com messageId real e audioUrl
            if (zapiResult != null && zapiResult.containsKey("messageId")) {
                String realMessageId = (String) zapiResult.get("messageId");

                // ✅ MODIFICADO: Usar audioService ao invés de messageService
                audioService.updateAudioIdAfterSend(
                        savedAudio.getMessageId(), realMessageId, "SENT"
                );

                savedAudio.setMessageId(realMessageId);
                savedAudio.setStatus("SENT");

                // Se a Z-API retornar a URL do áudio, atualizar também
                if (zapiResult.containsKey("audioUrl")) {
                    savedAudio.setAudioUrl((String) zapiResult.get("audioUrl"));
                }
            }

            log.info("✅ Áudio enviado e salvo com sucesso");

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Áudio enviado com sucesso",
                    "data", savedAudio
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
     * POST /dashboard/messages/send-image
     * Enviar imagem
     */
    @PostMapping("/send-image")
    public ResponseEntity<Map<String, Object>> sendImage(@RequestBody Map<String, Object> body) {
        try {
            log.info("📷 Requisição para enviar imagem");

            User user = getAuthenticatedUser();
            WebInstance instance = getActiveInstance(user);

            String chatId = (String) body.get("chatId");
            String phone = (String) body.get("phone");
            String image = (String) body.get("image");
            String photoId = (String) body.get("photoId"); // ✅ Receber photoId

            if (chatId == null || phone == null || image == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "ChatId, phone e image são obrigatórios"
                ));
            }

            // ✅ PASSO 1: Salvar foto no banco antes de enviar
            log.info("💾 Salvando foto no banco antes de enviar");
            PhotoDTO savedPhoto = photoService.saveOutgoingPhoto(chatId, phone, image, instance.getId(), photoId);

            // ✅ PASSO 2: Enviar via Z-API (SEM CAPTION)
            log.info("📨 Enviando imagem via Z-API - Phone: {}", phone);
            Map<String, Object> zapiResult = zapiMessageService.sendImage(
                    instance, phone, image
            );

            // ✅ PASSO 3: Atualizar com messageId real
            if (zapiResult != null && zapiResult.containsKey("messageId")) {
                String realMessageId = (String) zapiResult.get("messageId");
                photoService.updatePhotoIdAfterSend(
                        savedPhoto.getMessageId(), realMessageId, "SENT"
                );
                savedPhoto.setMessageId(realMessageId);
                savedPhoto.setStatus("SENT");
            }

            log.info("✅ Imagem enviada e salva com sucesso");

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Imagem enviada com sucesso",
                    "data", savedPhoto
            ));
        } catch (Exception e) {
            log.error("❌ Erro ao enviar imagem: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Erro ao enviar imagem: " + e.getMessage()
            ));
        }
    }

    /**
     * POST /dashboard/messages/send-video
     * Enviar vídeo
     */
    @PostMapping("/send-video")
    public ResponseEntity<Map<String, Object>> sendVideo(@RequestBody Map<String, Object> body) {
        try {
            log.info("🎥 Requisição para enviar vídeo");

            User user = getAuthenticatedUser();
            WebInstance instance = getActiveInstance(user);

            String chatId = (String) body.get("chatId");
            String phone = (String) body.get("phone");
            String video = (String) body.get("video");
            String videoId = (String) body.get("videoId"); // ✅ Receber videoId

            if (chatId == null || phone == null || video == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "ChatId, phone e video são obrigatórios"
                ));
            }

            // ✅ PASSO 1: Salvar vídeo no banco antes de enviar
            log.info("💾 Salvando vídeo no banco antes de enviar");
            VideoDTO savedVideo = videoService.saveOutgoingVideo(chatId, phone, video, instance.getId(), videoId);

            // ✅ PASSO 2: Enviar via Z-API (SEM CAPTION)
            log.info("📨 Enviando vídeo via Z-API - Phone: {}", phone);
            Map<String, Object> zapiResult = zapiMessageService.sendVideo(
                    instance, phone, video
            );

            // ✅ PASSO 3: Atualizar com messageId real
            if (zapiResult != null && zapiResult.containsKey("messageId")) {
                String realMessageId = (String) zapiResult.get("messageId");
                videoService.updateVideoIdAfterSend(
                        savedVideo.getMessageId(), realMessageId, "SENT"
                );
                savedVideo.setMessageId(realMessageId);
                savedVideo.setStatus("SENT");
            }

            log.info("✅ Vídeo enviado e salvo com sucesso");

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Vídeo enviado com sucesso",
                    "data", savedVideo
            ));
        } catch (Exception e) {
            log.error("❌ Erro ao enviar vídeo: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Erro ao enviar vídeo: " + e.getMessage()
            ));
        }
    }

    /**
     * POST /dashboard/messages/upload-image
     * Upload de imagem direto (sem chatId pré-definido)
     * Cria ou encontra o chat baseado no phone e envia a imagem
     */
    @PostMapping("/upload-image")
    public ResponseEntity<Map<String, Object>> uploadImage(@RequestBody Map<String, Object> body) {
        try {
            log.info("📤 Requisição para upload de imagem");

            User user = getAuthenticatedUser();
            WebInstance instance = getActiveInstance(user);

            String phone = (String) body.get("phone");
            String image = (String) body.get("image");

            if (phone == null || image == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Phone e image são obrigatórios"
                ));
            }

            // ✅ PASSO 1: Salvar foto para upload direto (sem chatId)
            log.info("💾 Salvando foto para upload direto - Phone: {}", phone);
            PhotoDTO savedPhoto = photoService.saveUploadPhoto(phone, image, instance.getId(), user);

            // ✅ PASSO 2: Enviar via Z-API
            log.info("📨 Enviando imagem via Z-API - Phone: {}", phone);
            Map<String, Object> zapiResult = zapiMessageService.sendImage(
                    instance, phone, image
            );

            // ✅ PASSO 3: Atualizar com messageId real
            if (zapiResult != null && zapiResult.containsKey("messageId")) {
                String realMessageId = (String) zapiResult.get("messageId");
                photoService.updatePhotoIdAfterSend(
                        savedPhoto.getMessageId(), realMessageId, "SENT"
                );
                savedPhoto.setMessageId(realMessageId);
                savedPhoto.setStatus("SENT");
            }

            log.info("✅ Imagem enviada via upload com sucesso");

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Imagem enviada com sucesso",
                    "data", savedPhoto
            ));
        } catch (Exception e) {
            log.error("❌ Erro ao fazer upload de imagem: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Erro ao fazer upload de imagem: " + e.getMessage()
            ));
        }
    }

    /**
     * POST /dashboard/messages/upload-video
     * Upload de vídeo direto (sem chatId pré-definido)
     */
    @PostMapping("/upload-video")
    public ResponseEntity<Map<String, Object>> uploadVideo(@RequestBody Map<String, Object> body) {
        try {
            log.info("📤 Requisição para upload de vídeo");

            User user = getAuthenticatedUser();
            WebInstance instance = getActiveInstance(user);

            String phone = (String) body.get("phone");
            String video = (String) body.get("video");

            if (phone == null || video == null) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "message", "Phone e video são obrigatórios"
                ));
            }

            // ✅ PASSO 1: Salvar vídeo para upload direto
            log.info("💾 Salvando vídeo para upload direto - Phone: {}", phone);
            VideoDTO savedVideo = videoService.saveUploadVideo(phone, video, instance.getId(), user);

            // ✅ PASSO 2: Enviar via Z-API
            log.info("📨 Enviando vídeo via Z-API - Phone: {}", phone);
            Map<String, Object> zapiResult = zapiMessageService.sendVideo(
                    instance, phone, video
            );

            // ✅ PASSO 3: Atualizar com messageId real
            if (zapiResult != null && zapiResult.containsKey("messageId")) {
                String realMessageId = (String) zapiResult.get("messageId");
                videoService.updateVideoIdAfterSend(
                        savedVideo.getMessageId(), realMessageId, "SENT"
                );
                savedVideo.setMessageId(realMessageId);
                savedVideo.setStatus("SENT");
            }

            log.info("✅ Vídeo enviado via upload com sucesso");

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Vídeo enviado com sucesso",
                    "data", savedVideo
            ));
        } catch (Exception e) {
            log.error("❌ Erro ao fazer upload de vídeo: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                    "success", false,
                    "message", "Erro ao fazer upload de vídeo: " + e.getMessage()
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