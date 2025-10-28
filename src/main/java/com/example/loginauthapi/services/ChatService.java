package com.example.loginauthapi.services;

import com.example.loginauthapi.dto.ChatInfoResponseDTO;
import com.example.loginauthapi.dto.ChatsListResponseDTO;
import com.example.loginauthapi.dto.TagDTO;
import com.example.loginauthapi.dto.zapi.ZapiChatDetailResponseDTO;
import com.example.loginauthapi.dto.zapi.ZapiChatItemDTO;
import com.example.loginauthapi.entities.Chat;
import com.example.loginauthapi.entities.Message;
import com.example.loginauthapi.entities.Tag;
import com.example.loginauthapi.entities.User;
import com.example.loginauthapi.entities.WebInstance;
import com.example.loginauthapi.repositories.ChatRepository;
import com.example.loginauthapi.repositories.MessageRepository;
import com.example.loginauthapi.repositories.AudioRepository;
import com.example.loginauthapi.repositories.PhotoRepository;
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
    private final TagRepository tagRepository;
    private final MessageRepository messageRepository;
    private final AudioRepository audioRepository;
    private final PhotoRepository photoRepository;

    // Armazenar progresso do carregamento por userId
    private final ConcurrentHashMap<String, LoadingProgress> loadingProgressMap = new ConcurrentHashMap<>();

    // Classe interna: Para rastrear o progresso
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

    /**
     * Obter progresso do carregamento
     */
    public LoadingProgress getLoadingProgress(String userId) {
        return loadingProgressMap.get(userId);
    }

    /**
     * ✅ MODIFICADO: Sincronizar lastMessageContent apenas de chats ATIVOS
     * Busca a última mensagem de cada chat ativo (texto, áudio OU foto) e atualiza o campo lastMessageContent
     */
    @Transactional
    public void syncLastMessageContent(String webInstanceId) {
        log.info("🔄 Iniciando sincronização de lastMessageContent para instância {}", webInstanceId);

        // ✅ BUSCAR APENAS CHATS ATIVOS
        List<Chat> chats = chatRepository.findByWebInstanceIdAndActiveInZapiTrueOrderByLastMessageTimeDesc(webInstanceId);
        int updated = 0;

        for (Chat chat : chats) {
            try {
                // ✅ BUSCAR ÚLTIMA MENSAGEM DE TEXTO
                Optional<Message> lastMessage = messageRepository.findTopByChatIdOrderByTimestampDesc(chat.getId());

                // ✅ BUSCAR ÚLTIMO ÁUDIO
                Optional<com.example.loginauthapi.entities.Audio> lastAudio =
                        audioRepository.findTopByChatIdOrderByTimestampDesc(chat.getId());

                // ✅ BUSCAR ÚLTIMA FOTO
                Optional<com.example.loginauthapi.entities.Photo> lastPhoto =
                        photoRepository.findTopByChatIdOrderByTimestampDesc(chat.getId());

                // Determinar qual é mais recente (mensagem de texto, áudio ou foto)
                LocalDateTime lastMessageTime = lastMessage.map(Message::getTimestamp).orElse(null);
                LocalDateTime lastAudioTime = lastAudio.map(com.example.loginauthapi.entities.Audio::getTimestamp).orElse(null);
                LocalDateTime lastPhotoTime = lastPhoto.map(com.example.loginauthapi.entities.Photo::getTimestamp).orElse(null);

                String content = null;

                // Comparar timestamps e usar o mais recente
                LocalDateTime mostRecent = null;
                String mostRecentType = null;

                if (lastMessageTime != null) {
                    mostRecent = lastMessageTime;
                    mostRecentType = "message";
                }

                if (lastAudioTime != null && (mostRecent == null || lastAudioTime.isAfter(mostRecent))) {
                    mostRecent = lastAudioTime;
                    mostRecentType = "audio";
                }

                if (lastPhotoTime != null && (mostRecent == null || lastPhotoTime.isAfter(mostRecent))) {
                    mostRecent = lastPhotoTime;
                    mostRecentType = "photo";
                }

                // Definir conteúdo baseado no tipo mais recente
                if ("photo".equals(mostRecentType)) {
                    content = "Foto 📸";
                } else if ("audio".equals(mostRecentType)) {
                    content = "Mensagem de Áudio";
                } else if ("message".equals(mostRecentType)) {
                    Message msg = lastMessage.get();
                    content = "audio".equals(msg.getType()) ? "🎤 Áudio" : msg.getContent();
                }

                if (content != null) {
                    // Truncar mensagem
                    String truncated = truncateMessage(content, 50);

                    // Atualizar apenas se diferente
                    if (!truncated.equals(chat.getLastMessageContent())) {
                        chat.setLastMessageContent(truncated);
                        chatRepository.save(chat);
                        updated++;
                        log.debug("✅ Chat {} atualizado: '{}'", chat.getPhone(), truncated);
                    }
                } else {
                    // Sem mensagens, garantir que está null
                    if (chat.getLastMessageContent() != null) {
                        chat.setLastMessageContent(null);
                        chatRepository.save(chat);
                        updated++;
                        log.debug("ℹ️ Chat {} sem mensagens", chat.getPhone());
                    }
                }
            } catch (Exception e) {
                log.error("❌ Erro ao sincronizar lastMessageContent do chat {}: {}",
                        chat.getPhone(), e.getMessage());
            }
        }

        log.info("✅ Sincronização concluída: {} chats ativos atualizados de {} chats totais",
                updated, chats.size());
    }


    /**
     * Truncar mensagem para exibição
     */
    private String truncateMessage(String message, int maxLength) {
        if (message == null) return "";
        if (message.length() <= maxLength) return message;
        return message.substring(0, maxLength) + "...";
    }

    /**
     * ✅ MODIFICADO: Sincronizar chats com Z-API e controlar active_in_zapi
     * Este é o método principal que implementa a funcionalidade solicitada
     */
    @Transactional
    public ChatsListResponseDTO syncAndGetChats(User user) {
        try {
            WebInstance activeInstance = getActiveWebInstance(user);

            // ✅ PASSO 1: DESATIVAR TODOS OS CHATS DA INSTÂNCIA
            log.info("🔄 Desativando todos os chats da instância {} antes de sincronizar", activeInstance.getId());
            chatRepository.deactivateAllChatsByWebInstanceId(activeInstance.getId());
            log.info("✅ Todos os chats desativados (active_in_zapi = false)");

            // ✅ PASSO 2: BUSCAR CHATS DO Z-API
            log.info("📡 Buscando chats da Z-API para instância {}", activeInstance.getSuaInstancia());
            List<ZapiChatItemDTO> zapiChats = zapiChatService.getChats(activeInstance);

            if (zapiChats.isEmpty()) {
                log.warn("⚠️ Nenhum chat encontrado na Z-API para usuário {}", user.getId());
                return buildEmptyResponse();
            }

            log.info("✅ {} chats recebidos do Z-API", zapiChats.size());

            // Inicializar progresso
            LoadingProgress progress = new LoadingProgress(zapiChats.size());
            loadingProgressMap.put(user.getId(), progress);

            // ✅ PASSO 3: SINCRONIZAR E ATIVAR APENAS CHATS DO Z-API
            log.info("🔄 Sincronizando {} chats do Z-API com o banco de dados", zapiChats.size());
            List<Chat> syncedChats = syncChatsWithDatabase(activeInstance, zapiChats);
            log.info("✅ {} chats sincronizados e marcados como ativos", syncedChats.size());

            // ✅ PASSO 4: SINCRONIZAR lastMessageContent de todos os chats ATIVOS
            syncLastMessageContent(activeInstance.getId());

            // Passar o progress para sincronização de fotos
            syncProfileThumbnailsWithProgress(activeInstance, syncedChats, progress);

            // ✅ PASSO 5: RECARREGAR APENAS CHATS ATIVOS
            log.info("📊 Carregando chats ativos da instância {}", activeInstance.getId());
            List<Chat> updatedChats = chatRepository.findByWebInstanceIdAndActiveInZapiTrueOrderByLastMessageTimeDesc(activeInstance.getId());
            log.info("✅ {} chats ativos carregados para exibição", updatedChats.size());

            return buildSuccessResponse(updatedChats);

        } catch (Exception e) {
            log.error("❌ Erro ao sincronizar chats para usuário {}: {}", user.getId(), e.getMessage(), e);
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

    /**
     * ✅ MODIFICADO: Marcar chats como ATIVOS (active_in_zapi = true) ao sincronizar
     * Chats recebidos do Z-API são marcados como ativos
     * Chats existentes são reativados se estavam inativos
     */
    private List<Chat> syncChatsWithDatabase(WebInstance instance, List<ZapiChatItemDTO> zapiChats) {
        return zapiChats.stream()
                .map(zapiChat -> {
                    try {
                        Optional<Chat> existingChat = chatRepository.findByWebInstanceIdAndPhone(
                                instance.getId(), zapiChat.getPhone());

                        Chat chat = existingChat.orElse(new Chat());
                        chat.setWebInstance(instance);
                        chat.setPhone(zapiChat.getPhone());
                        chat.setName(zapiChat.getName());
                        chat.setIsGroup(zapiChat.getIsGroup() != null ? zapiChat.getIsGroup() : false);

                        // ✅ MARCAR COMO ATIVO NO Z-API (FUNCIONALIDADE PRINCIPAL)
                        chat.setActiveInZapi(true);

                        if (existingChat.isPresent()) {
                            log.debug("♻️ Reativando chat existente: {}", zapiChat.getPhone());
                        } else {
                            log.debug("✨ Criando novo chat ativo: {}", zapiChat.getPhone());
                        }

                        // ✅ IMPORTANTE: NÃO SOBRESCREVER O CAMPO UNREAD!
                        // O campo unread é exclusivo do sistema de notificações
                        // e só deve ser modificado pelo WebhookService e MessageService

                        // O campo lastMessageTime é atualizado pelo WebhookService e MessageService
                        // Atualizar apenas se for um novo chat
                        try {
                            String timestampStr = zapiChat.getLastMessageTime();
                            if (timestampStr != null && !timestampStr.isEmpty() && !"0".equals(timestampStr)) {
                                long timestamp = Long.parseLong(timestampStr);
                                LocalDateTime zapiTime = LocalDateTime.ofInstant(
                                        Instant.ofEpochMilli(timestamp), ZoneId.systemDefault());

                                // Apenas atualizar para novos chats
                                if (chat.getId() == null) {
                                    chat.setLastMessageTime(zapiTime);
                                }
                            } else if (chat.getId() == null) {
                                // Novo chat sem timestamp
                                chat.setLastMessageTime(null);
                            }
                            // Se o chat já existe, manter o timestamp atual
                        } catch (NumberFormatException e) {
                            if (chat.getId() == null) {
                                chat.setLastMessageTime(null);
                            }
                        }

                        // ✅ IMPORTANTE: lastMessageContent não vem do Z-API
                        // Manter valor existente ou deixar null para novos chats
                        // O campo será atualizado pelo método syncLastMessageContent()

                        // Definir coluna apenas para novos chats
                        if (chat.getId() == null) {
                            chat.setColumn("inbox");
                        }

                        return chatRepository.save(chat);

                    } catch (Exception e) {
                        log.error("❌ Erro ao sincronizar chat {}: {}", zapiChat.getPhone(), e.getMessage());
                        return null;
                    }
                })
                .filter(chat -> chat != null)
                .collect(Collectors.toList());
    }

    /**
     * Sincronizar fotos de perfil com progresso
     */
    private void syncProfileThumbnailsWithProgress(WebInstance instance, List<Chat> chats, LoadingProgress progress) {
        chats.forEach(chat -> {
            CompletableFuture.runAsync(() -> {
                try {
                    log.debug("🖼️ Buscando foto de perfil para: {}", chat.getPhone());
                    ZapiChatDetailResponseDTO detail = zapiChatService.getChatDetail(instance, chat.getPhone());

                    if (detail != null) {
                        if (detail.getProfileThumbnail() != null && !detail.getProfileThumbnail().isEmpty()) {
                            chat.setProfileThumbnail(detail.getProfileThumbnail());
                            chatRepository.save(chat);
                            log.debug("✅ Foto de perfil atualizada para {}: {}",
                                    chat.getPhone(), detail.getProfileThumbnail());
                        } else {
                            log.debug("ℹ️ profileThumbnail vazio para: {}", chat.getPhone());
                        }
                    } else {
                        log.debug("⚠️ Detalhes do chat null para: {}", chat.getPhone());
                    }
                } catch (Exception e) {
                    log.warn("⚠️ Erro ao buscar foto de perfil para {}: {}", chat.getPhone(), e.getMessage());
                } finally {
                    // Incrementar progresso
                    progress.incrementLoaded();

                    // Marcar como completo se todos foram carregados
                    if (progress.getLoaded() >= progress.getTotal()) {
                        progress.setCompleted(true);
                        log.info("✅ Carregamento de fotos completo: {}/{}", progress.getLoaded(), progress.getTotal());
                    }
                }
            });
        });
    }

    /**
     * ✅ MODIFICADO: Buscar chats do banco de dados (apenas ativos)
     */
    public ChatsListResponseDTO getChatsFromDatabase(User user) {
        try {
            WebInstance activeInstance = getActiveWebInstance(user);

            // ✅ SINCRONIZAR lastMessageContent antes de retornar
            syncLastMessageContent(activeInstance.getId());

            // ✅ BUSCAR APENAS CHATS ATIVOS
            List<Chat> chats = chatRepository.findByWebInstanceIdAndActiveInZapiTrueOrderByLastMessageTimeDesc(activeInstance.getId());
            log.info("📊 Carregados {} chats ativos do banco de dados", chats.size());

            return buildSuccessResponse(chats);
        } catch (Exception e) {
            log.error("❌ Erro ao buscar chats do banco: {}", e.getMessage());
            return buildErrorResponse(e.getMessage());
        }
    }

    /**
     * Atualizar coluna do chat
     */
    @Transactional
    public void updateChatColumn(String chatId, String column) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat não encontrado"));
        chat.setColumn(column);
        chatRepository.save(chat);
    }

    /**
     * Atualizar coluna do chat salvando a coluna anterior
     */
    @Transactional
    public void updateChatColumnWithPrevious(String chatId, String newColumn) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat não encontrado"));

        // Salvar coluna atual como previousColumn antes de mudar
        if (chat.getColumn() != null && !chat.getColumn().equals(newColumn)) {
            chat.setPreviousColumn(chat.getColumn());
        }

        chat.setColumn(newColumn);
        chatRepository.save(chat);

        log.info("✅ Chat {} movido de '{}' para '{}'", chatId, chat.getPreviousColumn(), newColumn);
    }

    /**
     * Retornar chat para previousColumn
     */
    @Transactional
    public void returnChatToPreviousColumn(String chatId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat não encontrado"));

        String previousColumn = chat.getPreviousColumn();

        // Se previousColumn for null ou vazio, usar "inbox" como padrão
        if (previousColumn == null || previousColumn.isEmpty()) {
            previousColumn = "inbox";
            log.warn("⚠️ Chat {} sem previousColumn, usando 'inbox' como padrão", chatId);
        }

        chat.setColumn(previousColumn);
        chat.setPreviousColumn(null); // Limpar previousColumn
        chatRepository.save(chat);

        log.info("✅ Chat {} retornou para coluna '{}'", chatId, previousColumn);
    }

    // ============================================
    // MÉTODOS DE GERENCIAMENTO DE TAGS
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
        log.info("✅ Tags adicionadas ao chat {}: {}", chatId, tagIds);
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
        log.info("✅ Tag {} removida do chat {}", tagId, chatId);
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
        log.info("✅ Tags do chat {} atualizadas: {}", chatId, tagIds);
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

    /**
     * Converter Chat para ChatInfoResponseDTO com lista de tags
     */
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
                .lastMessageContent(chat.getLastMessageContent())
                .isGroup(chat.getIsGroup())
                .unread(chat.getUnread())
                .profileThumbnail(chat.getProfileThumbnail())
                .column(chat.getColumn())
                .tags(tagDtos)
                .build();
    }
}