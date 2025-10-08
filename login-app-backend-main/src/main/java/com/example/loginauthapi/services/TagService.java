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
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class TagService {

    private final TagRepository tagRepository;
    private final ChatRepository chatRepository;

    /**
     * Buscar todas as tags de um usuário
     */
    public List<TagDTO> getAllTagsByUser(User user) {
        List<Tag> tags = tagRepository.findByUserId(user.getId());
        return tags.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Buscar tag por ID (validando que pertence ao usuário)
     */
    public TagDTO getTagById(String tagId, User user) {
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new RuntimeException("Etiqueta não encontrada"));

        if (!tag.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Você não tem permissão para acessar esta etiqueta");
        }

        return convertToDTO(tag);
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
     * Atualizar tag existente
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

        return convertToDTO(updatedTag);
    }

    /**
     * Deletar tag - CORRIGIDO para remover associações primeiro
     */
    @Transactional
    public void deleteTag(String tagId, User user) {
        Tag tag = tagRepository.findById(tagId)
                .orElseThrow(() -> new RuntimeException("Etiqueta não encontrada"));

        if (!tag.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Você não tem permissão para deletar esta etiqueta");
        }

        // ✅ CORRIGIDO: Remover associações com chats antes de deletar
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