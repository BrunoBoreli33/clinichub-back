package com.example.loginauthapi.services;

import com.example.loginauthapi.dto.TagDTO;
import com.example.loginauthapi.dto.TagRequestDTO;
import com.example.loginauthapi.entities.Chat;
import com.example.loginauthapi.entities.Tag;
import com.example.loginauthapi.entities.User;
import com.example.loginauthapi.repositories.ChatRepository;
import com.example.loginauthapi.repositories.TagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TagService {

    private final TagRepository tagRepository;
    private final ChatRepository chatRepository;
    private final NotificationService notificationService; // ✅ NOVO: Injetar NotificationService

    /**
     * Buscar todas as tags de um usuário
     */
    public List<TagDTO> getAllTags(User user) {
        List<Tag> tags = tagRepository.findByUserId(user.getId());
        return tags.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Criar nova tag
     */
    @Transactional
    public TagDTO createTag(TagRequestDTO request, User user) {
        // Validar se já existe tag com mesmo nome para este usuário
        if (tagRepository.existsByNameAndUserId(request.getName(), user.getId())) {
            throw new RuntimeException("Já existe uma etiqueta com este nome");
        }

        Tag tag = new Tag();
        tag.setName(request.getName());
        tag.setColor(request.getColor());
        tag.setUser(user);

        Tag savedTag = tagRepository.save(tag);
        log.info("Tag criada: {} para usuário {}", savedTag.getName(), user.getId());

        return convertToDTO(savedTag);
    }

    /**
     * ✅ MODIFICADO: Atualizar tag existente e emitir evento SSE
     */
    @Transactional
    public TagDTO updateTag(String tagId, TagRequestDTO request, User user) {
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new RuntimeException("Etiqueta não encontrada"));

        if (!tag.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Você não tem permissão para editar esta etiqueta");
        }

        // Verificar se novo nome já existe (exceto para a própria tag)
        if (!tag.getName().equals(request.getName()) &&
                tagRepository.existsByNameAndUserId(request.getName(), user.getId())) {
            throw new RuntimeException("Já existe uma etiqueta com este nome");
        }

        tag.setName(request.getName());
        tag.setColor(request.getColor());

        Tag updatedTag = tagRepository.save(tag);
        log.info("Tag atualizada: {} (ID: {})", updatedTag.getName(), tagId);

        // ✅ NOVO: Enviar notificação SSE de atualização de tag
        try {
            Map<String, Object> tagData = new HashMap<>();
            tagData.put("tagId", updatedTag.getId());
            tagData.put("name", updatedTag.getName());
            tagData.put("color", updatedTag.getColor());

            notificationService.sendTagUpdateNotification(user.getId(), tagData);
            log.info("✅ Notificação de atualização de tag enviada via SSE - TagId: {}", tagId);
        } catch (Exception e) {
            log.error("❌ Erro ao enviar notificação SSE de atualização de tag", e);
            // Não lançar exceção para não quebrar o fluxo principal
        }

        return convertToDTO(updatedTag);
    }

    /**
     * ✅ MODIFICADO: Deletar tag e emitir evento SSE
     */
    @Transactional
    public void deleteTag(String tagId, User user) {
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new RuntimeException("Etiqueta não encontrada"));

        if (!tag.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Você não tem permissão para deletar esta etiqueta");
        }

        // ✅ Remover associações com chats antes de deletar
        // Limpar a tag de todos os chats que a utilizam
        if (tag.getChats() != null && !tag.getChats().isEmpty()) {
            log.info("Removendo tag {} de {} chats associados", tag.getName(), tag.getChats().size());

            // Criar cópia para evitar ConcurrentModificationException
            var chatsToUpdate = new HashSet<>(tag.getChats());

            for (Chat chat : chatsToUpdate) {
                chat.removeTag(tag);
                chatRepository.save(chat);
            }

            // Garantir que a coleção está vazia antes de deletar
            tag.getChats().clear();
            tagRepository.save(tag);
        }

        // Agora é seguro deletar a tag
        tagRepository.delete(tag);
        log.info("Tag deletada: {} (ID: {})", tag.getName(), tagId);

        // ✅ NOVO: Enviar notificação SSE de exclusão de tag
        try {
            Map<String, Object> tagData = new HashMap<>();
            tagData.put("tagId", tagId);

            notificationService.sendTagDeleteNotification(user.getId(), tagData);
            log.info("✅ Notificação de exclusão de tag enviada via SSE - TagId: {}", tagId);
        } catch (Exception e) {
            log.error("❌ Erro ao enviar notificação SSE de exclusão de tag", e);
            // Não lançar exceção para não quebrar o fluxo principal
        }
    }

    /**
     * Converter Tag para TagDTO
     */
    private TagDTO convertToDTO(Tag tag) {
        return TagDTO.builder()
                .id(tag.getId())
                .name(tag.getName())
                .color(tag.getColor())
                .criadoEm(tag.getCriadoEm())
                .atualizadoEm(tag.getAtualizadoEm())
                .build();
    }
}