package com.example.loginauthapi.services;

import com.example.loginauthapi.dto.ReplyDTO;
import com.example.loginauthapi.entities.*;
import com.example.loginauthapi.repositories.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReplyService {

    private final ReplyRepository replyRepository;
    private final MessageRepository messageRepository;
    private final ChatRepository chatRepository;
    private final PhotoRepository photoRepository;
    private final VideoRepository videoRepository;
    private final AudioRepository audioRepository;
    private final DocumentRepository documentRepository;

    /**
     * Salvar reply de mensagem de texto
     */
    @Transactional
    public Reply saveTextReply(String messageId, String referenceMessageId, String chatId,
                               String mensagemEnviada, Boolean fromMe, Long timestamp) {

        log.info("📨 Salvando reply de texto - MessageId: {}, ReferenceId: {}",
                messageId, referenceMessageId);

        Message message = messageRepository.findByMessageId(messageId)
                .orElseThrow(() -> new RuntimeException("Mensagem não encontrada"));

        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat não encontrado"));

        Optional<Reply> existing = replyRepository.findByMessageMessageId(messageId);
        if (existing.isPresent()) {
            log.info("Reply já existe para a mensagem {}", messageId);
            return existing.get();
        }

        // Buscar mensagem original de texto
        Optional<Message> originalMessage = messageRepository.findByMessageId(referenceMessageId);

        Reply reply = new Reply();
        reply.setMessage(message);
        reply.setReferenceMessageId(referenceMessageId);
        reply.setChat(chat);
        reply.setMensagemEnviada(mensagemEnviada);

        // Salvar conteúdo e senderName da mensagem original
        originalMessage.ifPresent(m -> {
            reply.setMessageContent(m.getContent());
            reply.setSenderName(m.getSenderName());
        });

        reply.setReplyType("text");
        reply.setFromMe(fromMe);
        reply.setTimestamp(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp),
                ZoneId.systemDefault()
        ));

        Reply saved = replyRepository.save(reply);
        log.info("✅ Reply de texto salvo - Id: {}", saved.getId());

        return saved;
    }

    /**
     * Salvar reply com referência a uma imagem
     */
    @Transactional
    public Reply saveImageReply(String messageId, String referenceMessageId, String chatId,
                                String mensagemEnviada, Boolean fromMe, Long timestamp) {

        log.info("🖼️ Salvando reply de imagem - MessageId: {}, ReferenceId: {}",
                messageId, referenceMessageId);

        Message message = messageRepository.findByMessageId(messageId)
                .orElseThrow(() -> new RuntimeException("Mensagem não encontrada"));

        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat não encontrado"));

        Optional<Photo> photo = photoRepository.findByMessageId(referenceMessageId);

        // Buscar mensagem original para pegar senderName
        Optional<Message> originalMessage = messageRepository.findByMessageId(referenceMessageId);

        Reply reply = new Reply();
        reply.setMessage(message);
        reply.setReferenceMessageId(referenceMessageId);
        reply.setChat(chat);
        reply.setMensagemEnviada(mensagemEnviada);
        reply.setReplyType("image");

        photo.ifPresent(p -> {
            reply.setImageUrl(p.getImageUrl());
            reply.setMessageContent(p.getCaption());
        });

        originalMessage.ifPresent(m -> reply.setSenderName(m.getSenderName()));

        reply.setFromMe(fromMe);
        reply.setTimestamp(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp),
                ZoneId.systemDefault()
        ));

        Reply saved = replyRepository.save(reply);
        log.info("✅ Reply de imagem salvo - Id: {}", saved.getId());

        return saved;
    }

    /**
     * Salvar reply com referência a um áudio
     */
    @Transactional
    public Reply saveAudioReply(String messageId, String referenceMessageId, String chatId,
                                String mensagemEnviada, Boolean fromMe, Long timestamp) {

        log.info("🎤 Salvando reply de áudio - MessageId: {}, ReferenceId: {}",
                messageId, referenceMessageId);

        Message message = messageRepository.findByMessageId(messageId)
                .orElseThrow(() -> new RuntimeException("Mensagem não encontrada"));

        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat não encontrado"));

        Optional<Audio> audio = audioRepository.findByMessageId(referenceMessageId);

        // Buscar mensagem original para pegar senderName
        Optional<Message> originalMessage = messageRepository.findByMessageId(referenceMessageId);

        Reply reply = new Reply();
        reply.setMessage(message);
        reply.setReferenceMessageId(referenceMessageId);
        reply.setChat(chat);
        reply.setMensagemEnviada(mensagemEnviada);
        reply.setReplyType("audio");

        audio.ifPresent(a -> reply.setAudioUrl(a.getAudioUrl()));

        originalMessage.ifPresent(m -> reply.setSenderName(m.getSenderName()));

        reply.setFromMe(fromMe);
        reply.setTimestamp(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp),
                ZoneId.systemDefault()
        ));

        Reply saved = replyRepository.save(reply);
        log.info("✅ Reply de áudio salvo - Id: {}", saved.getId());

        return saved;
    }

    /**
     * Salvar reply com referência a um vídeo
     */
    @Transactional
    public Reply saveVideoReply(String messageId, String referenceMessageId, String chatId,
                                String mensagemEnviada, Boolean fromMe, Long timestamp) {

        log.info("🎥 Salvando reply de vídeo - MessageId: {}, ReferenceId: {}",
                messageId, referenceMessageId);

        Message message = messageRepository.findByMessageId(messageId)
                .orElseThrow(() -> new RuntimeException("Mensagem não encontrada"));

        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat não encontrado"));

        Optional<Video> video = videoRepository.findByMessageId(referenceMessageId);

        // Buscar mensagem original para pegar senderName
        Optional<Message> originalMessage = messageRepository.findByMessageId(referenceMessageId);

        Reply reply = new Reply();
        reply.setMessage(message);
        reply.setReferenceMessageId(referenceMessageId);
        reply.setChat(chat);
        reply.setMensagemEnviada(mensagemEnviada);
        reply.setReplyType("video");

        video.ifPresent(v -> {
            reply.setVideoUrl(v.getVideoUrl());
            reply.setMessageContent(v.getCaption());
        });

        originalMessage.ifPresent(m -> reply.setSenderName(m.getSenderName()));

        reply.setFromMe(fromMe);
        reply.setTimestamp(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp),
                ZoneId.systemDefault()
        ));

        Reply saved = replyRepository.save(reply);
        log.info("✅ Reply de vídeo salvo - Id: {}", saved.getId());

        return saved;
    }

    /**
     * Salvar reply com referência a um documento
     */
    @Transactional
    public Reply saveDocumentReply(String messageId, String referenceMessageId, String chatId,
                                   String mensagemEnviada, Boolean fromMe, Long timestamp) {

        log.info("📄 Salvando reply de documento - MessageId: {}, ReferenceId: {}",
                messageId, referenceMessageId);

        Message message = messageRepository.findByMessageId(messageId)
                .orElseThrow(() -> new RuntimeException("Mensagem não encontrada"));

        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat não encontrado"));

        Optional<Document> document = documentRepository.findByMessageId(referenceMessageId);

        // Buscar mensagem original para pegar senderName
        Optional<Message> originalMessage = messageRepository.findByMessageId(referenceMessageId);

        Reply reply = new Reply();
        reply.setMessage(message);
        reply.setReferenceMessageId(referenceMessageId);
        reply.setChat(chat);
        reply.setMensagemEnviada(mensagemEnviada);
        reply.setReplyType("document");

        document.ifPresent(d -> {
            reply.setDocumentUrl(d.getDocumentUrl());
            reply.setMessageContent(d.getCaption());
        });

        originalMessage.ifPresent(m -> reply.setSenderName(m.getSenderName()));

        reply.setFromMe(fromMe);
        reply.setTimestamp(LocalDateTime.ofInstant(
                Instant.ofEpochMilli(timestamp),
                ZoneId.systemDefault()
        ));

        Reply saved = replyRepository.save(reply);
        log.info("✅ Reply de documento salvo - Id: {}", saved.getId());

        return saved;
    }

    public Optional<Reply> getReplyByMessageId(String messageId) {
        return replyRepository.findByMessageMessageId(messageId);
    }

    public List<Reply> getRepliesByChatId(String chatId) {
        return replyRepository.findByChatIdOrderByTimestampAsc(chatId);
    }

    public ReplyDTO convertToDTO(Reply reply) {
        return ReplyDTO.builder()
                .id(reply.getId())
                .messageId(reply.getMessage().getMessageId())
                .referenceMessageId(reply.getReferenceMessageId())
                .messageContent(reply.getMessageContent())
                .mensagemEnviada(reply.getMensagemEnviada())
                .senderName(reply.getSenderName())
                .audioUrl(reply.getAudioUrl())
                .documentUrl(reply.getDocumentUrl())
                .imageUrl(reply.getImageUrl())
                .videoUrl(reply.getVideoUrl())
                .replyType(reply.getReplyType())
                .fromMe(reply.getFromMe())
                .timestamp(reply.getTimestamp().toString())
                .build();
    }
}