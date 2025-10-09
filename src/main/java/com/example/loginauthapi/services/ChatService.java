package com.example.loginauthapi.services;

import com.example.loginauthapi.dto.ChatInfoResponseDTO;
import com.example.loginauthapi.dto.ChatsListResponseDTO;
import com.example.loginauthapi.dto.TagDTO;
import com.example.loginauthapi.dto.zapi.ZapiChatDetailResponseDTO;
import com.example.loginauthapi.dto.zapi.ZapiChatItemDTO;
import com.example.loginauthapi.entities.Chat;
import com.example.loginauthapi.entities.Tag;
import com.example.loginauthapi.entities.User;
import com.example.loginauthapi.entities.WebInstance;
import com.example.loginauthapi.repositories.ChatRepository;
import com.example.loginauthapi.repositories.TagRepository;
import com.example.loginauthapi.repositories.WebInstanceRepository;
import com.example.loginauthapi.services.zapi.ZapiChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatRepository chatRepository;
    private final WebInstanceRepository webInstanceRepository;
    private final ZapiChatService zapiChatService;
    private final TagRepository tagRepository; // ✅ ADICIONADO

    // NOVA ESTRUTURA: Armazenar progresso do carregamento por userId
    private final ConcurrentHashMap<String, LoadingProgress> loadingProgressMap = new ConcurrentHashMap<>();

    // NOVA CLASSE INTERNA: Para rastrear o progresso
    public static class LoadingProgress {
        private final AtomicInteger total;
        private final AtomicInteger loaded;
        private volatile boolean completed;

        public LoadingProgress(int total) {
            this.total = new AtomicInteger(total);
            this.loaded = new AtomicInteger(0);
            this.completed = false;
        }

        public void incrementLoaded() {
            loaded.incrementAndGet();
        }

        public int getPercentage() {
            int t = total.get();
            if (t == 0) return 100;
            return (int) ((loaded.get() * 100.0) / t);
        }

        public int getTotal() {
            return total.get();
        }

        public int getLoaded() {
            return loaded.get();
        }

        public boolean isCompleted() {
            return completed;
        }

        public void setCompleted(boolean completed) {
            this.completed = completed;
        }
    }

    // MÉTODO NOVO: Obter progresso do carregamento
    public LoadingProgress getLoadingProgress(String userId) {
        return loadingProgressMap.get(userId);
    }

    // MÉTODO MODIFICADO: Agora rastreia o progresso
    @Transactional
    public ChatsListResponseDTO syncAndGetChats(User user) {
        try {
            WebInstance activeInstance = getActiveWebInstance(user);
            List<ZapiChatItemDTO> zapiChats = zapiChatService.getChats(activeInstance);

            if (zapiChats.isEmpty()) {
                log.warn("Nenhum chat encontrado na Z-API para usuário {}", user.getId());
                return buildEmptyResponse();
            }

            // NOVA LINHA: Inicializar progresso
            LoadingProgress progress = new LoadingProgress(zapiChats.size());
            loadingProgressMap.put(user.getId(), progress);

            List<Chat> syncedChats = syncChatsWithDatabase(activeInstance, zapiChats);

            // MODIFICADO: Passar o progress para sincronização
            syncProfileThumbnailsWithProgress(activeInstance, syncedChats, progress);

            return buildSuccessResponse(syncedChats);

        } catch (Exception e) {
            log.error("Erro ao sincronizar chats para usuário {}: {}", user.getId(), e.getMessage(), e);
            return buildErrorResponse(e.getMessage());
        }
    }

    private WebInstance getActiveWebInstance(User user) {
        List<WebInstance> instances = webInstanceRepository.findByUserId(user.getId());

        if (instances.isEmpty()) {
            throw new RuntimeException("Nenhuma instância encontrada para o usuário");
        }

        return instances.stream()
                .filter(i -> "ACTIVE".equalsIgnoreCase(i.getStatus()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Nenhuma instância ativa encontrada"));
    }

    private List<Chat> syncChatsWithDatabase(WebInstance instance, List<ZapiChatItemDTO> zapiChats) {
        return zapiChats.stream()
                .map(zapiChat -> {
                    try {
                        Optional<Chat> existingChat = chatRepository.findByWebInstanceIdAndPhone(
                                instance.getId(), zapiChat.getPhone());

                        Chat chat = existingChat.orElse(new Chat());
                        chat.setWebInstance(instance);
                        chat.setPhone(zapiChat.getPhone());

                        if (zapiChat.getName() != null && !zapiChat.getName().trim().isEmpty()) {
                            chat.setName(zapiChat.getName());
                        } else {
                            chat.setName(zapiChat.getPhone());
                        }

                        chat.setIsGroup(zapiChat.getIsGroup() != null ? zapiChat.getIsGroup() : false);

                        try {
                            String unreadStr = zapiChat.getUnread();
                            chat.setUnread(unreadStr != null && !unreadStr.isEmpty() ? Integer.parseInt(unreadStr) : 0);
                        } catch (NumberFormatException e) {
                            chat.setUnread(0);
                        }

                        try {
                            String timestampStr = zapiChat.getLastMessageTime();
                            if (timestampStr != null && !timestampStr.isEmpty() && !"0".equals(timestampStr)) {
                                long timestamp = Long.parseLong(timestampStr);
                                chat.setLastMessageTime(LocalDateTime.ofInstant(
                                        Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()));
                            } else {
                                chat.setLastMessageTime(null);
                            }
                        } catch (NumberFormatException e) {
                            chat.setLastMessageTime(null);
                        }

                        if (chat.getId() == null) {
                            chat.setColumn("inbox");
                        }

                        return chatRepository.save(chat);

                    } catch (Exception e) {
                        log.error("Erro ao sincronizar chat {}: {}", zapiChat.getPhone(), e.getMessage());
                        return null;
                    }
                })
                .filter(chat -> chat != null)
                .collect(Collectors.toList());
    }

    // MÉTODO MODIFICADO: Agora atualiza o progresso
    private void syncProfileThumbnailsWithProgress(WebInstance instance, List<Chat> chats, LoadingProgress progress) {
        chats.forEach(chat -> {
            CompletableFuture.runAsync(() -> {
                try {
                    log.debug("Buscando foto de perfil para: {}", chat.getPhone());
                    ZapiChatDetailResponseDTO detail = zapiChatService.getChatDetail(instance, chat.getPhone());

                    if (detail != null) {
                        if (detail.getProfileThumbnail() != null && !detail.getProfileThumbnail().isEmpty()) {
                            chat.setProfileThumbnail(detail.getProfileThumbnail());
                            chatRepository.save(chat);
                            log.debug("Foto de perfil atualizada para {}: {}",
                                    chat.getPhone(), detail.getProfileThumbnail());
                        } else {
                            log.debug("profileThumbnail vazio para: {}", chat.getPhone());
                        }
                    } else {
                        log.debug("Detalhes do chat null para: {}", chat.getPhone());
                    }
                } catch (Exception e) {
                    log.warn("Erro ao buscar foto de perfil para {}: {}", chat.getPhone(), e.getMessage());
                } finally {
                    // NOVA LINHA: Incrementar progresso
                    progress.incrementLoaded();

                    // NOVA LINHA: Marcar como completo se todos foram carregados
                    if (progress.getLoaded() >= progress.getTotal()) {
                        progress.setCompleted(true);
                        log.info("Carregamento de fotos completo: {}/{}", progress.getLoaded(), progress.getTotal());
                    }
                }
            });
        });
    }

    // MANTIDO: Método sem modificações para buscar do banco
    private void syncProfileThumbnailsAsync(WebInstance instance, List<Chat> chats) {
        chats.forEach(chat -> {
            CompletableFuture.runAsync(() -> {
                try {
                    log.debug("Buscando foto de perfil para: {}", chat.getPhone());
                    ZapiChatDetailResponseDTO detail = zapiChatService.getChatDetail(instance, chat.getPhone());

                    if (detail != null) {
                        if (detail.getProfileThumbnail() != null && !detail.getProfileThumbnail().isEmpty()) {
                            chat.setProfileThumbnail(detail.getProfileThumbnail());
                            chatRepository.save(chat);
                            log.debug("Foto de perfil atualizada para {}: {}",
                                    chat.getPhone(), detail.getProfileThumbnail());
                        } else {
                            log.debug("profileThumbnail vazio para: {}", chat.getPhone());
                        }
                    } else {
                        log.debug("Detalhes do chat null para: {}", chat.getPhone());
                    }
                } catch (Exception e) {
                    log.warn("Erro ao buscar foto de perfil para {}: {}", chat.getPhone(), e.getMessage());
                }
            });
        });
    }

    public ChatsListResponseDTO getChatsFromDatabase(User user) {
        try {
            WebInstance activeInstance = getActiveWebInstance(user);
            List<Chat> chats = chatRepository.findByWebInstanceIdOrderByLastMessageTimeDesc(activeInstance.getId());
            return buildSuccessResponse(chats);
        } catch (Exception e) {
            log.error("Erro ao buscar chats do banco: {}", e.getMessage());
            return buildErrorResponse(e.getMessage());
        }
    }

    @Transactional
    public void updateChatColumn(String chatId, String column) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat não encontrado"));
        chat.setColumn(column);
        chatRepository.save(chat);
    }

    // ============================================
    // ✅ NOVOS MÉTODOS DE GERENCIAMENTO DE TAGS
    // ============================================

    /**
     * Adicionar tags a um chat
     */
    @Transactional
    public void addTagsToChat(String chatId, List<String> tagIds, User user) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat não encontrado"));

        // Verificar se o chat pertence à instância ativa do usuário
        WebInstance activeInstance = getActiveWebInstance(user);
        if (!chat.getWebInstance().getId().equals(activeInstance.getId())) {
            throw new RuntimeException("Chat não pertence à sua instância ativa");
        }

        // Buscar e adicionar tags
        for (String tagId : tagIds) {
            Tag tag = tagRepository.findById(tagId)
                    .orElseThrow(() -> new RuntimeException("Tag não encontrada: " + tagId));

            // Verificar se a tag pertence ao usuário
            if (!tag.getUser().getId().equals(user.getId())) {
                throw new RuntimeException("Tag não pertence ao usuário");
            }

            chat.addTag(tag);
        }

        chatRepository.save(chat);
        log.info("Tags adicionadas ao chat {}: {}", chatId, tagIds);
    }

    /**
     * Remover tag de um chat
     */
    @Transactional
    public void removeTagFromChat(String chatId, String tagId, User user) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat não encontrado"));

        // Verificar se o chat pertence à instância ativa do usuário
        WebInstance activeInstance = getActiveWebInstance(user);
        if (!chat.getWebInstance().getId().equals(activeInstance.getId())) {
            throw new RuntimeException("Chat não pertence à sua instância ativa");
        }

        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new RuntimeException("Tag não encontrada"));

        // Verificar se a tag pertence ao usuário
        if (!tag.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Tag não pertence ao usuário");
        }

        chat.removeTag(tag);
        chatRepository.save(chat);
        log.info("Tag {} removida do chat {}", tagId, chatId);
    }

    /**
     * Substituir todas as tags de um chat
     */
    @Transactional
    public void setTagsForChat(String chatId, List<String> tagIds, User user) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat não encontrado"));

        // Verificar se o chat pertence à instância ativa do usuário
        WebInstance activeInstance = getActiveWebInstance(user);
        if (!chat.getWebInstance().getId().equals(activeInstance.getId())) {
            throw new RuntimeException("Chat não pertence à sua instância ativa");
        }

        // Limpar tags atuais
        chat.clearTags();

        // Adicionar novas tags
        if (tagIds != null && !tagIds.isEmpty()) {
            for (String tagId : tagIds) {
                Tag tag = tagRepository.findById(tagId)
                        .orElseThrow(() -> new RuntimeException("Tag não encontrada: " + tagId));

                // Verificar se a tag pertence ao usuário
                if (!tag.getUser().getId().equals(user.getId())) {
                    throw new RuntimeException("Tag não pertence ao usuário");
                }

                chat.addTag(tag);
            }
        }

        chatRepository.save(chat);
        log.info("Tags do chat {} atualizadas: {}", chatId, tagIds);
    }

    // ============================================
    // MÉTODOS DE CONSTRUÇÃO DE RESPOSTAS
    // ============================================

    private ChatsListResponseDTO buildSuccessResponse(List<Chat> chats) {
        List<ChatInfoResponseDTO> chatDtos = chats.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());

        long unreadCount = chats.stream()
                .filter(c -> c.getUnread() != null && c.getUnread() > 0)
                .count();

        return ChatsListResponseDTO.builder()
                .success(true)
                .message("Chats carregados com sucesso")
                .totalChats(chats.size())
                .unreadCount((int) unreadCount)
                .chats(chatDtos)
                .build();
    }

    private ChatsListResponseDTO buildEmptyResponse() {
        return ChatsListResponseDTO.builder()
                .success(true)
                .message("Nenhum chat encontrado")
                .totalChats(0)
                .unreadCount(0)
                .chats(List.of())
                .build();
    }

    private ChatsListResponseDTO buildErrorResponse(String message) {
        return ChatsListResponseDTO.builder()
                .success(false)
                .message("Erro ao carregar chats: " + message)
                .totalChats(0)
                .unreadCount(0)
                .chats(List.of())
                .build();
    }

    // ✅ MÉTODO MODIFICADO: Agora retorna lista de tags ao invés de ticket
    private ChatInfoResponseDTO convertToDto(Chat chat) {
        // Converter tags para TagDTO
        List<TagDTO> tagDtos = chat.getTags().stream()
                .map(tag -> TagDTO.builder()
                        .id(tag.getId())
                        .name(tag.getName())
                        .color(tag.getColor())
                        .criadoEm(tag.getCriadoEm())
                        .atualizadoEm(tag.getAtualizadoEm())
                        .build())
                .collect(Collectors.toList());

        return ChatInfoResponseDTO.builder()
                .id(chat.getId())
                .name(chat.getName())
                .phone(chat.getPhone())
                .lastMessageTime(chat.getLastMessageTime())
                .isGroup(chat.getIsGroup())
                .unread(chat.getUnread())
                .profileThumbnail(chat.getProfileThumbnail())
                .column(chat.getColumn())
                .tags(tagDtos)  // ✅ MODIFICADO: retorna lista de tags
                .build();
    }
}