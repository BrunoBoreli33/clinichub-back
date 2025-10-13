package com.example.loginauthapi.services;

import com.example.loginauthapi.entities.Chat;
import com.example.loginauthapi.entities.WebInstance;
import com.example.loginauthapi.repositories.ChatRepository;
import com.example.loginauthapi.repositories.WebInstanceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {

    private final MessageService messageService;
    private final ChatRepository chatRepository;
    private final WebInstanceRepository webInstanceRepository;

    /**
     * ✅ NOVO: Método unificado para processar QUALQUER mensagem
     * Extrai as informações importantes e salva no banco
     *
     * Campos importantes do JSON:
     * - fromMe: Boolean (true = enviada, false = recebida)
     * - momment: Long (timestamp)
     * - text.message: String (conteúdo)
     * - connectedPhone: String (telefone da instância)
     * - phone: String (telefone do contato)
     * - instanceId: String
     * - messageId: String
     * - chatName: String
     * - senderName: String
     */
    @Transactional
    public void processMessage(Map<String, Object> payload) {
        try {
            log.info("🔄 Processando mensagem unificada");

            // ===== EXTRAIR INFORMAÇÕES IMPORTANTES =====
            Boolean fromMe = (Boolean) payload.get("fromMe");
            Long momment = payload.get("momment") != null ?
                    ((Number) payload.get("momment")).longValue() : System.currentTimeMillis();

            String connectedPhone = (String) payload.get("connectedPhone");
            String phone = (String) payload.get("phone");
            String instanceId = (String) payload.get("instanceId");
            String messageId = (String) payload.get("messageId");
            String chatName = (String) payload.get("chatName");
            String senderName = (String) payload.get("senderName");
            String status = (String) payload.get("status");
            String senderPhoto = (String) payload.get("senderPhoto");
            Boolean isForwarded = (Boolean) payload.get("forwarded");
            Boolean isGroup = (Boolean) payload.get("isGroup");

            // Extrair conteúdo da mensagem
            @SuppressWarnings("unchecked")
            Map<String, Object> textObj = (Map<String, Object>) payload.get("text");
            String content = textObj != null ? (String) textObj.get("message") : "";

            if (content == null || content.trim().isEmpty()) {
                log.warn("⚠️ Mensagem vazia recebida, ignorando");
                return;
            }

            log.info("📝 Mensagem extraída - FromMe: {}, Phone: {}, InstanceId: {}",
                    fromMe, phone, instanceId);

            // ===== BUSCAR INSTÂNCIA POR INSTANCE ID =====
            Optional<WebInstance> instanceOpt = Optional.empty();

            // Tentar buscar por suaInstancia (que pode ser o instanceId)
            if (instanceId != null && !instanceId.trim().isEmpty()) {
                instanceOpt = webInstanceRepository.findBySuaInstancia(instanceId);
            }

            // Se não encontrou, tentar por connectedPhone como fallback
            if (instanceOpt.isEmpty() && connectedPhone != null) {
                instanceOpt = webInstanceRepository.findByConnectedPhone(connectedPhone);
            }

            if (instanceOpt.isEmpty()) {
                log.warn("⚠️ WebInstance não encontrada - InstanceId: {}, ConnectedPhone: {}",
                        instanceId, connectedPhone);
                return;
            }

            WebInstance instance = instanceOpt.get();
            log.info("✅ Instância encontrada - ID: {}, User: {}",
                    instance.getId(), instance.getUser().getEmail());

            // ===== BUSCAR OU CRIAR CHAT =====
            Optional<Chat> chatOpt = chatRepository.findByWebInstanceIdAndPhone(instance.getId(), phone);
            Chat chat;

            if (chatOpt.isEmpty()) {
                // Criar novo chat se não existir
                chat = new Chat();
                chat.setWebInstance(instance);
                chat.setPhone(phone);
                chat.setName(chatName != null ? chatName : phone);
                chat.setIsGroup(isGroup != null ? isGroup : false);
                chat.setUnread(fromMe ? 0 : 1); // Se recebida, incrementa unread
                chat.setColumn("inbox");
                chat = chatRepository.save(chat);

                log.info("✅ Novo chat criado - ID: {}, Nome: {}, Phone: {}",
                        chat.getId(), chat.getName(), chat.getPhone());
            } else {
                chat = chatOpt.get();

                // Atualizar nome do chat se mudou
                if (chatName != null && !chatName.equals(chat.getName())) {
                    chat.setName(chatName);
                }

                // Se é mensagem recebida, incrementar contador de não lidas
                if (!fromMe) {
                    chat.setUnread(chat.getUnread() + 1);
                }

                chat = chatRepository.save(chat);
                log.info("✅ Chat existente atualizado - ID: {}", chat.getId());
            }

            // ===== SALVAR MENSAGEM =====
            messageService.saveIncomingMessage(
                    chat.getId(),
                    messageId,
                    content,
                    fromMe,
                    momment,
                    status,
                    senderName,
                    senderPhoto,
                    isForwarded != null ? isForwarded : false,
                    isGroup != null ? isGroup : false
            );

            // ✅ ATUALIZAR lastMessageTime do chat
            chat.setLastMessageTime(java.time.LocalDateTime.ofInstant(
                    java.time.Instant.ofEpochMilli(momment),
                    java.time.ZoneId.systemDefault()
            ));
            chatRepository.save(chat);

            log.info("✅ Mensagem processada com sucesso - MessageId: {}, Chat: {}",
                    messageId, chat.getId());

        } catch (Exception e) {
            log.error("❌ Erro ao processar mensagem", e);
            throw new RuntimeException("Erro ao processar webhook: " + e.getMessage(), e);
        }
    }

    /**
     * ⚠️ MANTIDO PARA COMPATIBILIDADE: Métodos antigos ainda funcionam
     */
    @Transactional
    public void processIncomingMessage(Map<String, Object> payload) {
        processMessage(payload);
    }

    @Transactional
    public void processOutcomingMessage(Map<String, Object> payload) {
        processMessage(payload);
    }
}