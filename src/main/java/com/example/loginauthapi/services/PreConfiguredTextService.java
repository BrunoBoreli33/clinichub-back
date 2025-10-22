package com.example.loginauthapi.services;

import com.example.loginauthapi.dto.PreConfiguredTextDTO;
import com.example.loginauthapi.entities.PreConfiguredText;
import com.example.loginauthapi.entities.User;
import com.example.loginauthapi.repositories.PreConfiguredTextRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PreConfiguredTextService {

    private final PreConfiguredTextRepository preConfiguredTextRepository;

    /**
     * Buscar todos os textos pré-configurados de um usuário
     */
    public List<PreConfiguredTextDTO> getAllTexts(User user) {
        List<PreConfiguredText> texts = preConfiguredTextRepository.findByUserIdOrderByCreatedAtDesc(user.getId());
        log.info("📋 [USER: {}] Buscando textos pré-configurados - Total: {}", user.getId(), texts.size());

        return texts.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Criar novo texto pré-configurado
     */
    @Transactional
    public PreConfiguredTextDTO createText(String title, String content, User user) {
        log.info("➕ [USER: {}] Criando texto pré-configurado - Título: '{}'", user.getId(), title);

        PreConfiguredText text = new PreConfiguredText();
        text.setUser(user);
        text.setTitle(title);
        text.setContent(content);

        PreConfiguredText saved = preConfiguredTextRepository.save(text);

        log.info("✅ [USER: {}] Texto pré-configurado criado com sucesso (ID: {})", user.getId(), saved.getId());

        return convertToDTO(saved);
    }

    /**
     * Atualizar texto pré-configurado
     */
    @Transactional
    public PreConfiguredTextDTO updateText(String id, String title, String content, User user) {
        log.info("✏️ [USER: {}] Atualizando texto pré-configurado ID: {}", user.getId(), id);

        PreConfiguredText text = preConfiguredTextRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Texto pré-configurado não encontrado"));

        if (!text.getUser().getId().equals(user.getId())) {
            log.warn("⚠️ [USER: {}] Tentou editar texto de outro usuário (ID: {})", user.getId(), id);
            throw new RuntimeException("Sem permissão para editar este texto");
        }

        text.setTitle(title);
        text.setContent(content);

        PreConfiguredText updated = preConfiguredTextRepository.save(text);

        log.info("✅ [USER: {}] Texto pré-configurado atualizado com sucesso", user.getId());

        return convertToDTO(updated);
    }

    /**
     * Deletar texto pré-configurado
     */
    @Transactional
    public void deleteText(String id, User user) {
        log.info("🗑️ [USER: {}] Deletando texto pré-configurado ID: {}", user.getId(), id);

        PreConfiguredText text = preConfiguredTextRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Texto pré-configurado não encontrado"));

        if (!text.getUser().getId().equals(user.getId())) {
            log.warn("⚠️ [USER: {}] Tentou deletar texto de outro usuário (ID: {})", user.getId(), id);
            throw new RuntimeException("Sem permissão para deletar este texto");
        }

        preConfiguredTextRepository.delete(text);

        log.info("✅ [USER: {}] Texto pré-configurado deletado com sucesso", user.getId());
    }

    /**
     * Converter entidade para DTO
     */
    private PreConfiguredTextDTO convertToDTO(PreConfiguredText text) {
        PreConfiguredTextDTO dto = new PreConfiguredTextDTO();
        dto.setId(text.getId());
        dto.setTitle(text.getTitle());
        dto.setContent(text.getContent());
        dto.setCreatedAt(text.getCreatedAt().toString());
        dto.setUpdatedAt(text.getUpdatedAt().toString());
        return dto;
    }
}