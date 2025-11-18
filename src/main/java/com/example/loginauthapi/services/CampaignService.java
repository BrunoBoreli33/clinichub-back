package com.example.loginauthapi.services;

import com.example.loginauthapi.dto.CampaignDTO;
import com.example.loginauthapi.dto.CampaignRequestDTO;
import com.example.loginauthapi.entities.Campaign;
import com.example.loginauthapi.entities.Chat;
import com.example.loginauthapi.entities.User;
import com.example.loginauthapi.repositories.CampaignRepository;
import com.example.loginauthapi.repositories.ChatRepository;
import com.example.loginauthapi.repositories.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final ChatRepository chatRepository;
    private final TagRepository tagRepository;

    @Transactional
    public CampaignDTO createCampaign(CampaignRequestDTO request, User user) {
        log.info("📢 Criando campanha para usuário: {}", user.getEmail());

        Campaign campaign = new Campaign();
        campaign.setUser(user);
        campaign.setName(request.name());
        campaign.setMessage(request.message());
        campaign.setChatsPerDispatch(request.chatsPerDispatch());
        campaign.setIntervalMinutes(request.intervalMinutes());
        campaign.setStatus("CRIADA");
        campaign.setAllTrustworthy(request.allTrustworthy() != null && request.allTrustworthy());

        // Salvar IDs das tags
        if (request.tagIds() != null && !request.tagIds().isEmpty()) {
            campaign.setTagIds(String.join(",", request.tagIds()));
        }

        // ✅ NOVO: Salvar IDs das fotos
        if (request.photoIds() != null && !request.photoIds().isEmpty()) {
            campaign.setPhotoIds(String.join(",", request.photoIds()));
        }

        // ✅ NOVO: Salvar IDs dos vídeos
        if (request.videoIds() != null && !request.videoIds().isEmpty()) {
            campaign.setVideoIds(String.join(",", request.videoIds()));
        }

        // Calcular total de chats elegíveis
        List<Chat> eligibleChats = getEligibleChats(user, request.tagIds(), campaign.getAllTrustworthy());
        campaign.setTotalChats(eligibleChats.size());
        campaign.setDispatchedChats(0);

        Campaign saved = campaignRepository.save(campaign);
        log.info("✅ Campanha criada: {} com {} chats elegíveis", saved.getId(), saved.getTotalChats());

        return convertToDTO(saved);
    }

    @Transactional(readOnly = true)
    public List<CampaignDTO> getAllCampaigns(User user) {
        List<Campaign> campaigns = campaignRepository.findByUserOrderByAtualizadoEmDesc(user);
        return campaigns.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public CampaignDTO getCampaignById(String id, User user) {
        Campaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Campanha não encontrada"));

        if (!campaign.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Você não tem permissão para acessar esta campanha");
        }

        return convertToDTO(campaign);
    }

    @Transactional
    public CampaignDTO updateCampaign(String id, CampaignRequestDTO request, User user) {
        Campaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Campanha não encontrada"));

        if (!campaign.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Você não tem permissão para editar esta campanha");
        }

        // Não permitir edição de campanhas em andamento ou concluídas
        if (campaign.getStatus().equals("EM_ANDAMENTO")) {
            throw new RuntimeException("Não é possível editar uma campanha em andamento. Pause a campanha primeiro.");
        }

        if (campaign.getStatus().equals("CONCLUIDA")) {
            throw new RuntimeException("Não é possível editar uma campanha concluída");
        }

        campaign.setName(request.name());
        campaign.setMessage(request.message());
        campaign.setChatsPerDispatch(request.chatsPerDispatch());
        campaign.setIntervalMinutes(request.intervalMinutes());
        campaign.setAllTrustworthy(request.allTrustworthy() != null && request.allTrustworthy());

        // Atualizar tags
        if (request.tagIds() != null && !request.tagIds().isEmpty()) {
            campaign.setTagIds(String.join(",", request.tagIds()));
        } else {
            campaign.setTagIds(null);
        }

        // ✅ NOVO: Atualizar IDs das fotos
        if (request.photoIds() != null && !request.photoIds().isEmpty()) {
            campaign.setPhotoIds(String.join(",", request.photoIds()));
        } else {
            campaign.setPhotoIds(null);
        }

        // ✅ NOVO: Atualizar IDs dos vídeos
        if (request.videoIds() != null && !request.videoIds().isEmpty()) {
            campaign.setVideoIds(String.join(",", request.videoIds()));
        } else {
            campaign.setVideoIds(null);
        }

        // Recalcular total de chats se necessário
        if (!campaign.getStatus().equals("EM_ANDAMENTO")) {
            List<Chat> eligibleChats = getEligibleChats(user, request.tagIds(), campaign.getAllTrustworthy());
            campaign.setTotalChats(eligibleChats.size());
        }

        Campaign saved = campaignRepository.save(campaign);
        log.info("✅ Campanha atualizada: {}", saved.getId());

        return convertToDTO(saved);
    }

    @Transactional
    public void deleteCampaign(String id, User user) {
        Campaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Campanha não encontrada"));

        if (!campaign.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Você não tem permissão para deletar esta campanha");
        }

        // Não permitir deleção de campanhas em andamento
        if (campaign.getStatus().equals("EM_ANDAMENTO")) {
            throw new RuntimeException("Não é possível deletar uma campanha em andamento. Pause ou cancele a campanha primeiro.");
        }

        campaignRepository.delete(campaign);
        log.info("✅ Campanha deletada: {}", id);
    }

    @Transactional
    public CampaignDTO startCampaign(String id, User user) {
        Campaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Campanha não encontrada"));

        if (!campaign.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Você não tem permissão para iniciar esta campanha");
        }

        if (!campaign.getStatus().equals("CRIADA") && !campaign.getStatus().equals("PAUSADA")) {
            throw new RuntimeException("Apenas campanhas criadas ou pausadas podem ser iniciadas");
        }

        campaign.setStatus("EM_ANDAMENTO");
        campaign.setNextDispatchTime(LocalDateTime.now().plusMinutes(campaign.getIntervalMinutes()));

        Campaign saved = campaignRepository.save(campaign);
        log.info("✅ Campanha iniciada: {} - Primeiro disparo em {} minutos", saved.getId(), saved.getIntervalMinutes());

        return convertToDTO(saved);
    }

    @Transactional
    public CampaignDTO pauseCampaign(String id, User user) {
        Campaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Campanha não encontrada"));

        if (!campaign.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Você não tem permissão para pausar esta campanha");
        }

        if (!campaign.getStatus().equals("EM_ANDAMENTO")) {
            throw new RuntimeException("Apenas campanhas em andamento podem ser pausadas");
        }

        campaign.setStatus("PAUSADA");
        campaign.setNextDispatchTime(null);

        Campaign saved = campaignRepository.save(campaign);
        log.info("✅ Campanha pausada: {}", saved.getId());

        return convertToDTO(saved);
    }

    @Transactional
    public CampaignDTO cancelCampaign(String id, User user) {
        Campaign campaign = campaignRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Campanha não encontrada"));

        if (!campaign.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Você não tem permissão para cancelar esta campanha");
        }

        if (campaign.getStatus().equals("CONCLUIDA") || campaign.getStatus().equals("CANCELADA")) {
            throw new RuntimeException("Esta campanha já foi finalizada");
        }

        campaign.setStatus("CANCELADA");
        campaign.setNextDispatchTime(null);

        Campaign saved = campaignRepository.save(campaign);
        log.info("✅ Campanha cancelada: {}", saved.getId());

        return convertToDTO(saved);
    }

    // Método auxiliar para obter chats elegíveis
    private List<Chat> getEligibleChats(User user, List<String> tagIds, Boolean allTrustworthy) {
        if (allTrustworthy != null && allTrustworthy) {
            // Todos os chats confiáveis do usuário
            return chatRepository.findByWebInstance_UserAndIsTrustworthyTrue(user);
        } else if (tagIds != null && !tagIds.isEmpty()) {
            // Chats com tags específicas e is_trustworthy=true
            return chatRepository.findByWebInstance_UserAndTagsIdInAndIsTrustworthyTrue(user, tagIds);
        } else {
            return new ArrayList<>();
        }
    }

    // Converter entidade para DTO
    private CampaignDTO convertToDTO(Campaign campaign) {
        List<String> tagIds = null;
        if (campaign.getTagIds() != null && !campaign.getTagIds().isEmpty()) {
            tagIds = Arrays.asList(campaign.getTagIds().split(","));
        }

        // ✅ NOVO: Converter photoIds para lista
        List<String> photoIds = null;
        if (campaign.getPhotoIds() != null && !campaign.getPhotoIds().isEmpty()) {
            photoIds = Arrays.asList(campaign.getPhotoIds().split(","));
        }

        // ✅ NOVO: Converter videoIds para lista
        List<String> videoIds = null;
        if (campaign.getVideoIds() != null && !campaign.getVideoIds().isEmpty()) {
            videoIds = Arrays.asList(campaign.getVideoIds().split(","));
        }

        return new CampaignDTO(
                campaign.getId(),
                campaign.getName(),
                campaign.getMessage(),
                campaign.getChatsPerDispatch(),
                campaign.getIntervalMinutes(),
                campaign.getStatus(),
                campaign.getTotalChats(),
                campaign.getDispatchedChats(),
                campaign.getProgressPercentage(),
                campaign.getNextDispatchTime(),
                tagIds,
                campaign.getAllTrustworthy(),
                photoIds,  // ✅ NOVO
                videoIds,  // ✅ NOVO
                campaign.getCriadoEm(),
                campaign.getAtualizadoEm()
        );
    }
}