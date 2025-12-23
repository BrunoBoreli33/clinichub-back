package com.example.loginauthapi.services;

import com.example.loginauthapi.dto.MessageDTO;
import com.example.loginauthapi.dto.PhotoDTO;
import com.example.loginauthapi.dto.VideoDTO;
import com.example.loginauthapi.entities.Campaign;
import com.example.loginauthapi.entities.Chat;
import com.example.loginauthapi.entities.Photo;
import com.example.loginauthapi.entities.Video;
import com.example.loginauthapi.entities.User;
import com.example.loginauthapi.entities.WebInstance;
import com.example.loginauthapi.repositories.CampaignRepository;
import com.example.loginauthapi.repositories.ChatRepository;
import com.example.loginauthapi.repositories.PhotoRepository;
import com.example.loginauthapi.repositories.VideoRepository;
import com.example.loginauthapi.repositories.WebInstanceRepository;
import com.example.loginauthapi.services.zapi.ZapiMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignDispatcherService {

    private final CampaignRepository campaignRepository;
    private final ChatRepository chatRepository;
    private final WebInstanceRepository webInstanceRepository;
    private final ZapiMessageService zapiMessageService;
    private final MessageService messageService;
    private final PhotoService photoService;
    private final VideoService videoService;
    private final PhotoRepository photoRepository;
    private final VideoRepository videoRepository;

    @Autowired
    private ApplicationContext applicationContext;

    /**
     * Executa a cada minuto para verificar campanhas que precisam ser disparadas
     */
    @Scheduled(fixedDelay = 30000) // 30 segundos
    public void processCampaigns() {
        try {
            LocalDateTime now = LocalDateTime.now();
            List<Campaign> campaigns = campaignRepository.findCampaignsReadyForDispatch(now);

            if (campaigns.isEmpty()) {
                return;
            }

            log.info("📢 Processando {} campanhas prontas para disparo", campaigns.size());

            // Obter proxy do próprio serviço para garantir que @Transactional funcione
            CampaignDispatcherService self = applicationContext.getBean(CampaignDispatcherService.class);

            for (Campaign campaign : campaigns) {
                try {
                    self.dispatchCampaignBatch(campaign.getId());
                } catch (Exception e) {
                    log.error("❌ Erro ao processar campanha {}: {}", campaign.getId(), e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("❌ Erro ao processar campanhas", e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void dispatchCampaignBatch(String campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId).orElse(null);
        if (campaign == null) return;

        // Inicializar coleção lazy dentro da transação
        campaign.getDispatchedChatIds().size();

        log.info("📤 Disparando lote da campanha: {} ({}/{})",
                campaign.getName(), campaign.getDispatchedChats(), campaign.getTotalChats());

        User user = campaign.getUser();

        // Obter chats elegíveis que ainda não receberam a mensagem
        List<Chat> eligibleChats = getEligibleChatsForCampaign(campaign, user);

        if (eligibleChats.isEmpty()) {
            log.info("✅ Campanha {} concluída - todos os chats foram processados", campaign.getId());
            campaign.setStatus("CONCLUIDA");
            campaign.setNextDispatchTime(null);
            campaignRepository.save(campaign);
            return;
        }

        // Determinar quantos chats enviar neste lote
        int chatsToDispatch = Math.min(campaign.getChatsPerDispatch(), eligibleChats.size());
        List<Chat> batchChats = eligibleChats.subList(0, chatsToDispatch);

        // Obter instância ativa do usuário
        List<WebInstance> instances = webInstanceRepository.findByUserIdAndStatus(user.getId(), "ACTIVE");

        if (instances.isEmpty()) {
            log.warn("⚠️ Nenhuma instância ativa para o usuário {}. Pausando campanha {}",
                    user.getEmail(), campaign.getId());
            campaign.setStatus("PAUSADA");
            campaign.setNextDispatchTime(null);
            campaignRepository.save(campaign);
            return;
        }

        WebInstance instance = instances.get(0);

        // ✅ NOVO: Obter listas de fotos e vídeos da campanha
        List<Photo> campaignPhotos = getCampaignPhotos(campaign);
        List<Video> campaignVideos = getCampaignVideos(campaign);

        // Obter proxy para chamar método com nova transação
        CampaignDispatcherService self = applicationContext.getBean(CampaignDispatcherService.class);

        // Enviar mensagens + mídias
        for (Chat chat : batchChats) {
            boolean messageSentViaZapi = false;

            try {
                // ✅ CRÍTICO: Marcar chat como disparado em TRANSAÇÃO SEPARADA
                boolean wasMarked = self.markChatAsDispatched(campaignId, chat.getId());

                if (!wasMarked) {
                    log.warn("⚠️ Chat {} já foi processado anteriormente, pulando...", chat.getId());
                    continue;
                }

                log.info("💬 Enviando conteúdo da campanha para: {} ({})", chat.getName(), chat.getPhone());

                // ===== PASSO 1: Enviar mensagem de texto =====
                try {
                    // Tentar salvar no banco (ignorar erros de duplicação)
                    MessageDTO savedMessage = null;
                    try {
                        savedMessage = messageService.saveOutgoingMessage(chat.getId(), campaign.getMessage(), user);
                    } catch (DataIntegrityViolationException e) {
                        log.warn("⚠️ Erro de duplicação ao salvar mensagem no banco. Continuando...");
                    }

                    // Enviar via Z-API (CRÍTICO)
                    Map<String, Object> zapiResult = zapiMessageService.sendTextMessage(
                            instance,
                            chat.getPhone(),
                            campaign.getMessage(),
                            false
                    );

                    if (zapiResult != null && zapiResult.containsKey("messageId")) {
                        String realMessageId = (String) zapiResult.get("messageId");
                        messageSentViaZapi = true;
                        log.info("✅ Mensagem de texto enviada via Z-API - MessageId: {}", realMessageId);

                        // Tentar atualizar no banco (ignorar erros de duplicação)
                        if (savedMessage != null) {
                            try {
                                messageService.updateMessageIdAfterSend(savedMessage.getMessageId(), realMessageId, "SENT");
                            } catch (DataIntegrityViolationException e) {
                                log.warn("⚠️ Erro de duplicação ao atualizar messageId. Ignorando.");
                            }
                        }
                    }
                } catch (Exception e) {
                    log.error("❌ Erro ao enviar mensagem de texto: {}", e.getMessage());
                    if (!messageSentViaZapi) {
                        throw e; // Re-lançar apenas se não enviou via Z-API
                    }
                }

                // Delay entre mensagem e fotos
                Thread.sleep(2000);

                // ===== PASSO 2: Enviar fotos (se houver) =====
                if (!campaignPhotos.isEmpty()) {
                    log.info("📷 Enviando {} foto(s) para {}", campaignPhotos.size(), chat.getName());
                    for (Photo photo : campaignPhotos) {
                        try {
                            PhotoDTO savedPhoto = null;
                            try {
                                savedPhoto = photoService.saveOutgoingPhoto(
                                        chat.getId(),
                                        chat.getPhone(),
                                        photo.getImageUrl(),
                                        instance.getId(),
                                        null
                                );
                            } catch (DataIntegrityViolationException e) {
                                log.warn("⚠️ Erro de duplicação ao salvar foto. Continuando...");
                            }

                            Map<String, Object> photoResult = zapiMessageService.sendImage(
                                    instance,
                                    chat.getPhone(),
                                    photo.getImageUrl(),
                                    false
                            );

                            if (photoResult != null && photoResult.containsKey("messageId")) {
                                String photoMessageId = (String) photoResult.get("messageId");
                                log.info("✅ Foto enviada via Z-API - MessageId: {}", photoMessageId);

                                if (savedPhoto != null) {
                                    try {
                                        photoService.updatePhotoIdAfterSend(savedPhoto.getMessageId(), photoMessageId, "SENT");
                                    } catch (DataIntegrityViolationException e) {
                                        log.warn("⚠️ Erro de duplicação ao atualizar photo messageId. Ignorando.");
                                    }
                                }
                            }

                            Thread.sleep(2000);

                        } catch (Exception e) {
                            log.error("❌ Erro ao enviar foto: {}", e.getMessage());
                        }
                    }
                }

                // ===== PASSO 3: Enviar vídeos (se houver) =====
                if (!campaignVideos.isEmpty()) {
                    log.info("🎥 Enviando {} vídeo(s) para {}", campaignVideos.size(), chat.getName());
                    for (Video video : campaignVideos) {
                        try {
                            VideoDTO savedVideo = null;
                            try {
                                savedVideo = videoService.saveOutgoingVideo(
                                        chat.getId(),
                                        chat.getPhone(),
                                        video.getVideoUrl(),
                                        instance.getId(),
                                        null
                                );
                            } catch (DataIntegrityViolationException e) {
                                log.warn("⚠️ Erro de duplicação ao salvar vídeo. Continuando...");
                            }

                            Map<String, Object> videoResult = zapiMessageService.sendVideo(
                                    instance,
                                    chat.getPhone(),
                                    video.getVideoUrl(),
                                    false
                            );

                            if (videoResult != null && videoResult.containsKey("messageId")) {
                                String videoMessageId = (String) videoResult.get("messageId");
                                log.info("✅ Vídeo enviado via Z-API - MessageId: {}", videoMessageId);

                                if (savedVideo != null) {
                                    try {
                                        videoService.updateVideoIdAfterSend(savedVideo.getMessageId(), videoMessageId, "SENT");
                                    } catch (DataIntegrityViolationException e) {
                                        log.warn("⚠️ Erro de duplicação ao atualizar video messageId. Ignorando.");
                                    }
                                }
                            }

                            Thread.sleep(3000);

                        } catch (Exception e) {
                            log.error("❌ Erro ao enviar vídeo: {}", e.getMessage());
                        }
                    }
                }

                log.info("✅ Campanha enviada completamente para: {} ({})", chat.getName(), chat.getPhone());

                // Delay entre chats
                Thread.sleep(2000);

            } catch (Exception e) {
                log.error("❌ Erro CRÍTICO ao processar chat {}: {}", chat.getId(), e.getMessage());

                // ✅ DECISÃO: Apenas desmarcar se NÃO enviou via Z-API
                if (!messageSentViaZapi) {
                    log.warn("⚠️ Mensagem NÃO foi enviada. Desmarcando chat {} para nova tentativa.", chat.getId());
                    self.unmarkChatAsDispatched(campaignId, chat.getId());
                } else {
                    log.info("ℹ️ Mensagem foi enviada via Z-API. Mantendo chat {} como disparado.", chat.getId());
                }
            }
        }

        // ✅ Recarregar campanha para atualizar status
        campaign = campaignRepository.findById(campaignId).orElse(campaign);
        campaign.getDispatchedChatIds().size(); // Inicializar lazy

        // O contador dispatched_chats é automaticamente sincronizado pelo getter
        log.info("📊 Status da campanha: {}/{} disparos ({}%)",
                campaign.getDispatchedChats(),
                campaign.getTotalChats(),
                String.format("%.1f", campaign.getProgressPercentage()));

        // Verificar se a campanha foi concluída
        if (campaign.getDispatchedChats() >= campaign.getTotalChats()) {
            log.info("✅ Campanha {} concluída", campaign.getId());
            campaign.setStatus("CONCLUIDA");
            campaign.setNextDispatchTime(null);
        } else {
            // Agendar próximo disparo
            campaign.setNextDispatchTime(LocalDateTime.now().plusMinutes(campaign.getIntervalMinutes()));
            log.info("⏰ Próximo disparo da campanha {} em {} minutos",
                    campaign.getId(), campaign.getIntervalMinutes());
        }

        campaignRepository.save(campaign);
    }

    /**
     * ✅ Marca o chat como disparado em uma transação SEPARADA
     * Retorna true se conseguiu marcar, false se já estava marcado
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean markChatAsDispatched(String campaignId, String chatId) {
        Campaign campaign = campaignRepository.findById(campaignId).orElse(null);
        if (campaign == null) return false;

        // Inicializar coleção lazy
        campaign.getDispatchedChatIds().size();

        // Verificar se já foi processado
        if (campaign.getDispatchedChatIds().contains(chatId)) {
            log.debug("Chat {} já está na lista de disparados", chatId);
            return false;
        }

        // Adicionar à lista
        campaign.getDispatchedChatIds().add(chatId);
        campaignRepository.saveAndFlush(campaign);

        log.info("🔒 Chat {} adicionado à lista de disparados (transação commitada)", chatId);
        return true;
    }

    /**
     * ✅ Remove o chat da lista de disparados em caso de erro ANTES do envio
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void unmarkChatAsDispatched(String campaignId, String chatId) {
        Campaign campaign = campaignRepository.findById(campaignId).orElse(null);
        if (campaign == null) return;

        campaign.getDispatchedChatIds().size();

        if (campaign.getDispatchedChatIds().remove(chatId)) {
            campaignRepository.saveAndFlush(campaign);
            log.warn("🔓 Chat {} removido da lista de disparados devido a erro", chatId);
        }
    }

    // ✅ NOVO: Obter fotos da galeria para a campanha
    private List<Photo> getCampaignPhotos(Campaign campaign) {
        if (campaign.getPhotoIds() == null || campaign.getPhotoIds().isEmpty()) {
            return new ArrayList<>();
        }

        List<String> photoIds = Arrays.asList(campaign.getPhotoIds().split(","));
        return photoRepository.findAllById(photoIds);
    }

    // ✅ NOVO: Obter vídeos da galeria para a campanha
    private List<Video> getCampaignVideos(Campaign campaign) {
        if (campaign.getVideoIds() == null || campaign.getVideoIds().isEmpty()) {
            return new ArrayList<>();
        }

        List<String> videoIds = Arrays.asList(campaign.getVideoIds().split(","));
        return videoRepository.findAllById(videoIds);
    }

    private List<Chat> getEligibleChatsForCampaign(Campaign campaign, User user) {
        List<Chat> allEligibleChats;

        if (campaign.getAllTrustworthy()) {
            // Todos os chats confiáveis do usuário
            allEligibleChats = chatRepository.findByWebInstance_UserAndIsTrustworthyTrue(user);
        } else if (campaign.getTagIds() != null && !campaign.getTagIds().isEmpty()) {
            // Chats com tags específicas e is_trustworthy=true
            List<String> tagIds = Arrays.asList(campaign.getTagIds().split(","));
            allEligibleChats = chatRepository.findByWebInstance_UserAndTagsIdInAndIsTrustworthyTrue(user, tagIds);
        } else {
            allEligibleChats = new ArrayList<>();
        }

        // Filtrar chats que já receberam a mensagem
        Set<String> dispatchedIds = campaign.getDispatchedChatIds();
        return allEligibleChats.stream()
                .filter(chat -> !dispatchedIds.contains(chat.getId()))
                .collect(Collectors.toList());
    }
}