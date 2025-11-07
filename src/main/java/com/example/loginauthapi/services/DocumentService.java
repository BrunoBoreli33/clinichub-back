package com.example.loginauthapi.services;

import com.example.loginauthapi.dto.DocumentDTO;
import com.example.loginauthapi.entities.Chat;
import com.example.loginauthapi.entities.Document;
import com.example.loginauthapi.entities.User;
import com.example.loginauthapi.entities.WebInstance;
import com.example.loginauthapi.repositories.ChatRepository;
import com.example.loginauthapi.repositories.DocumentRepository;
import com.example.loginauthapi.repositories.WebInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final ChatRepository chatRepository;
    private final WebInstanceRepository webInstanceRepository;

    @Transactional
    public DocumentDTO saveIncomingDocument(
            String chatId, String messageId, String instanceId, String phone,
            Boolean fromMe, Long momment, String documentUrl, String fileName,
            String mimeType, Integer pageCount, String title, String caption,
            Boolean isStatusReply, Boolean isEdit, Boolean isGroup,
            Boolean isNewsletter, Boolean forwarded, String chatName,
            String senderName, String status) {

        log.info("💾 Salvando documento - MessageId: {}, ChatId: {}, FileName: {}",
                messageId, chatId, fileName);

        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat não encontrado: " + chatId));

        Document document = new Document();
        document.setChat(chat);
        document.setMessageId(messageId);
        document.setInstanceId(instanceId);
        document.setPhone(phone);
        document.setFromMe(fromMe);
        document.setTimestamp(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(momment),
                ZoneId.systemDefault()
        ));
        document.setDocumentUrl(documentUrl);

        // ✅ CORREÇÃO: Remover extensão duplicada do fileName (ex: "arquivo.pptx.pptx" -> "arquivo.pptx")
        String cleanFileName = fileName;
        if (fileName != null && fileName.contains(".")) {
            int lastDotIndex = fileName.lastIndexOf(".");
            if (lastDotIndex > 0) {
                String nameWithoutLastExt = fileName.substring(0, lastDotIndex);
                if (nameWithoutLastExt.contains(".")) {
                    cleanFileName = nameWithoutLastExt;
                }
            }
        }
        document.setFileName(cleanFileName);
        document.setMimeType(mimeType);
        document.setPageCount(pageCount);
        document.setTitle(title);
        document.setCaption(caption);
        document.setIsStatusReply(isStatusReply);
        document.setIsEdit(isEdit);
        document.setIsGroup(isGroup);
        document.setIsNewsletter(isNewsletter);
        document.setForwarded(forwarded);
        document.setChatName(chatName);
        document.setSenderName(senderName);
        document.setStatus(status);

        document = documentRepository.save(document);

        log.info("✅ Documento salvo com sucesso - DocumentId: {}", document.getId());

        return toDTO(document);
    }

    @Transactional
    public DocumentDTO saveUploadDocument(String phone, String documentBase64,
                                          String fileName, String instanceId, User user) {

        log.info("💾 Salvando documento para upload - Phone: {}, FileName: {}", phone, fileName);

        // Buscar a WebInstance
        WebInstance webInstance = webInstanceRepository.findById(instanceId)
                .orElseThrow(() -> new RuntimeException("WebInstance não encontrada: " + instanceId));

        // Buscar ou criar chat com base no phone e webInstanceId
        Chat chat = chatRepository.findByPhoneAndWebInstanceId(phone, instanceId)
                .orElseGet(() -> {
                    Chat newChat = new Chat();
                    newChat.setPhone(phone);
                    newChat.setWebInstance(webInstance);
                    newChat.setName(phone);
                    newChat.setProfileThumbnail(null);
                    newChat.setUnread(0);
                    newChat.setLastMessageTime(LocalDateTime.now());
                    newChat.setActiveInZapi(true);
                    newChat.setIsGroup(false);
                    return chatRepository.save(newChat);
                });

        String tempMessageId = "TEMP_DOC_" + System.currentTimeMillis();

        Document document = new Document();
        document.setChat(chat);
        document.setMessageId(tempMessageId);
        document.setInstanceId(instanceId);
        document.setPhone(phone);
        document.setFromMe(true);
        document.setTimestamp(LocalDateTime.now());
        document.setDocumentUrl(documentBase64);
        document.setFileName(fileName);
        document.setStatus("PENDING");

        document = documentRepository.save(document);

        log.info("✅ Documento de upload salvo - DocumentId: {}", document.getId());

        return toDTO(document);
    }

    @Transactional
    public void updateDocumentIdAfterSend(String oldMessageId, String newMessageId, String status) {
        log.info("🔄 Atualizando messageId do documento - Old: {}, New: {}", oldMessageId, newMessageId);

        documentRepository.findByMessageId(oldMessageId).ifPresent(document -> {
            document.setMessageId(newMessageId);
            document.setStatus(status);
            documentRepository.save(document);
            log.info("✅ Documento atualizado com sucesso");
        });
    }

    public List<DocumentDTO> getDocumentsByChat(String chatId) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat não encontrado: " + chatId));

        return documentRepository.findByChatOrderByTimestampAsc(chat)
                .stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * âœ… NOVO: Excluir documento do banco de dados
     */
    @Transactional
    public void deleteDocument(String messageId) {
        try {
            log.info("ðŸ—'️ Excluindo documento - MessageId: {}", messageId);

            documentRepository.findByMessageId(messageId).ifPresent(document -> {
                documentRepository.delete(document);
                log.info("âœ… Documento excluído do banco - MessageId: {}", messageId);
            });

        } catch (Exception e) {
            log.error("â Erro ao excluir documento do banco", e);
            throw new RuntimeException("Erro ao excluir documento: " + e.getMessage(), e);
        }
    }

    private DocumentDTO toDTO(Document document) {
        DocumentDTO dto = new DocumentDTO();
        dto.setId(document.getId());
        dto.setMessageId(document.getMessageId());
        dto.setInstanceId(document.getInstanceId());
        dto.setPhone(document.getPhone());
        dto.setFromMe(document.getFromMe());
        dto.setTimestamp(document.getTimestamp());
        dto.setDocumentUrl(document.getDocumentUrl());
        dto.setFileName(document.getFileName());
        dto.setMimeType(document.getMimeType());
        dto.setPageCount(document.getPageCount());
        dto.setTitle(document.getTitle());
        dto.setCaption(document.getCaption());
        dto.setIsStatusReply(document.getIsStatusReply());
        dto.setIsEdit(document.getIsEdit());
        dto.setIsGroup(document.getIsGroup());
        dto.setIsNewsletter(document.getIsNewsletter());
        dto.setForwarded(document.getForwarded());
        dto.setChatName(document.getChatName());
        dto.setSenderName(document.getSenderName());
        dto.setStatus(document.getStatus());
        return dto;
    }
}