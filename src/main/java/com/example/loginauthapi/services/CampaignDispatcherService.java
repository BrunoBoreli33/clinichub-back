package com.example.loginauthapi.services;

import com.example.loginauthapi.dto.MessageDTO;
import com.example.loginauthapi.entities.Campaign;
import com.example.loginauthapi.entities.Chat;
import com.example.loginauthapi.entities.User;
import com.example.loginauthapi.entities.WebInstance;
import com.example.loginauthapi.repositories.CampaignRepository;
import com.example.loginauthapi.repositories.ChatRepository;
import com.example.loginauthapi.repositories.WebInstanceRepository;
import com.example.loginauthapi.services.zapi.ZapiMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
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

    /**
     * Executa a cada minuto para verificar campanhas que precisam ser disparadas
     */
    @Scheduled(fixedDelay = 60000) // 60 segundos
    @Transactional
    public void processCampaigns() {
        try {
            LocalDateTime now = LocalDateTime.now();
            List<Campaign> campaigns = campaignRepository.findCampaignsReadyForDispatch(now);

            if (campaigns.isEmpty()) {
                return;
            }

            log.info("📢 Processando {} campanhas prontas para disparo", campaigns.size());

            for (Campaign campaign : campaigns) {
                try {
                    dispatchCampaignBatch(campaign);
                } catch (Exception e) {
                    log.error("❌ Erro ao processar campanha {}: {}", campaign.getId(), e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("❌ Erro ao processar campanhas", e);
        }
    }

    @Transactional
    public void dispatchCampaignBatch(Campaign campaign) {
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
        int successCount = 0;

        // Enviar mensagens
        for (Chat chat : batchChats) {
            try {
                // ✅ CORRIGIDO: Usar MessageService.saveOutgoingMessage + ZapiMessageService
                // Este é o MESMO método usado pelo ChatWindow para enviar mensagens
                log.info("💬 Enviando mensagem da campanha para: {} ({})", chat.getName(), chat.getPhone());

                // PASSO 1: Salvar mensagem no banco
                MessageDTO savedMessage = messageService.saveOutgoingMessage(chat.getId(), campaign.getMessage(), user);

                // PASSO 2: Enviar via Z-API
                Map<String, Object> zapiResult = zapiMessageService.sendTextMessage(
                        instance,
                        chat.getPhone(),
                        campaign.getMessage()
                );

                // PASSO 3: Atualizar com messageId real do WhatsApp
                if (zapiResult != null && zapiResult.containsKey("messageId")) {
                    String realMessageId = (String) zapiResult.get("messageId");
                    messageService.updateMessageIdAfterSend(savedMessage.getMessageId(), realMessageId, "SENT");
                    log.info("✅ Mensagem enviada e salva - MessageId: {}", realMessageId);
                }

                // Adicionar chat à lista de disparados
                campaign.getDispatchedChatIds().add(chat.getId());
                successCount++;

                log.info("✅ Campanha enviada para chat: {} ({})", chat.getName(), chat.getPhone());

                // Pequeno delay entre envios para evitar bloqueio
                Thread.sleep(2000);

            } catch (Exception e) {
                log.error("❌ Erro ao enviar mensagem da campanha para chat {}: {}", chat.getId(), e.getMessage());
            }
        }

        // Atualizar contadores da campanha
        campaign.setDispatchedChats(campaign.getDispatchedChats() + successCount);

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

        log.info("📊 Campanha {}: {}/{} disparos realizados ({}%)",
                campaign.getName(),
                campaign.getDispatchedChats(),
                campaign.getTotalChats(),
                String.format("%.1f", campaign.getProgressPercentage()));
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