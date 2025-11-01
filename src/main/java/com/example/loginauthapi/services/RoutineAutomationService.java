package com.example.loginauthapi.services;

import com.example.loginauthapi.entities.*;
import com.example.loginauthapi.repositories.*;
import com.example.loginauthapi.services.zapi.ZapiMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

// Serviço responsável por automatizar o envio de mensagens de rotina para clientes
@Service
@RequiredArgsConstructor
@Slf4j
public class RoutineAutomationService {

    // Repositórios para acessar dados do banco
    private final ChatRepository chatRepository;
    private final MessageRepository messageRepository;
    private final RoutineTextRepository routineTextRepository;
    private final ChatRoutineStateRepository chatRoutineStateRepository;
    private final UserRepository userRepository;
    private final WebInstanceRepository webInstanceRepository;

    // Serviço para enviar mensagens via WhatsApp (Z-API)
    private final ZapiMessageService zapiMessageService;

    // ✅ NOVO: Serviço para enviar notificações SSE
    private final NotificationService notificationService;

    // Nomes das colunas/categorias onde os chats podem estar
    private static final String REPESCAGEM_COLUMN = "followup"; // Coluna de acompanhamento automático
    private static final String LEAD_FRIO_COLUMN = "cold_lead"; // Coluna de leads frios (sem resposta)

    // Método executado automaticamente a cada 30 segundos
    @Scheduled(fixedRate = 30000)
    public void processRoutineAutomation() {
        log.info("🤖 Iniciando processamento de rotinas automáticas");

        try {
            // Busca todos os usuários cadastrados no sistema
            List<User> users = userRepository.findAll();

            // Para cada usuário, processa suas rotinas de mensagens
            for (User user : users) {
                processUserRoutines(user);
            }

            log.info("✅ Processamento de rotinas automáticas concluído");
        } catch (Exception e) {
            // Registra qualquer erro que ocorra durante o processamento
            log.error("❌ Erro ao processar rotinas automáticas", e);
        }
    }

    // Processa as rotinas de um usuário específico
    private void processUserRoutines(User user) {
        // Busca todas as rotinas configuradas pelo usuário, ordenadas por sequência
        List<RoutineText> routines = routineTextRepository.findByUserIdOrderBySequenceNumberAsc(user.getId());

        // Se não há rotinas configuradas, não faz nada
        if (routines.isEmpty()) {
            return;
        }

        // Busca a primeira rotina (sequência 1) - ela define quando iniciar a repescagem
        RoutineText firstRoutine = routines.stream()
                .filter(r -> r.getSequenceNumber() == 1)
                .findFirst()
                .orElse(null);

        // Se não existe rotina de sequência 1, não pode processar
        if (firstRoutine == null) {
            log.warn("⚠️ [USER: {}] Primeira rotina (sequence=1) não configurada", user.getId());
            return;
        }

        // ✅ PASSO 1: Primeiro processa chats que JÁ ESTÃO em repescagem
        // FILTRO APLICADO: Processa apenas chats com activeInZapi = true
        List<Chat> repescagemChats = chatRepository.findByUserIdAndColumn(user.getId(), REPESCAGEM_COLUMN).stream()
                .filter(chat -> Boolean.TRUE.equals(chat.getActiveInZapi()))
                .toList();

        // Verifica cada chat em repescagem para enviar a próxima mensagem automática
        for (Chat chat : repescagemChats) {
            checkAndSendNextRoutineMessage(chat, user, routines);
        }

        // ✅ PASSO 2: Depois busca chats que PRECISAM ENTRAR em repescagem
        // FILTRO APLICADO: Processa apenas chats com activeInZapi = true
        List<Chat> monitoredChats = chatRepository.findByUserIdAndColumnIn(
                        user.getId(),
                        Arrays.asList("hot_lead", "inbox")
                ).stream()
                .filter(chat -> Boolean.TRUE.equals(chat.getActiveInZapi()))
                .toList();

        // Verifica cada chat para ver se é hora de mover para repescagem
        for (Chat chat : monitoredChats) {
            checkAndMoveToRepescagem(chat, user, firstRoutine, routines);
        }
    }

    // Verifica se um chat deve ser movido para repescagem e envia a primeira mensagem
    private void checkAndMoveToRepescagem(Chat chat, User user, RoutineText firstRoutine, List<RoutineText> routines) {

        // *************************************************************************
        // CORREÇÃO: PRIMEIRA VERIFICAÇÃO DE ATIVIDADE DO CLIENTE (MANTÉM O CHAT FORA SE ATIVO)
        // Usando a sintaxe CORRETA do seu MessageRepository: findTopByChatIdOrderByTimestampDesc
        // *************************************************************************
        Optional<Message> lastAnyMessageOpt = messageRepository
                .findTopByChatIdOrderByTimestampDesc(chat.getId()); // <-- CORREÇÃO DA SINTAXE

        if (lastAnyMessageOpt.isEmpty()) {
            return; // Se não tem nenhuma mensagem, ignora
        }

        Message lastAnyMessage = lastAnyMessageOpt.get();

        // Se a ÚLTIMA mensagem GERAL foi DO CLIENTE (fromMe=false), o chat está ativo. NÃO move para repescagem.
        if (!lastAnyMessage.getFromMe()) {
            return;
        }
        // *************************************************************************
        // FIM DA CORREÇÃO DE ATIVIDADE
        // *************************************************************************


        // A PARTIR DAQUI, SABEMOS QUE A ÚLTIMA MENSAGEM FOI ENVIADA PELO USUÁRIO (fromMe=true)

        // Busca a última mensagem enviada PELO USUÁRIO (fromMe=true) neste chat
        Optional<Message> lastUserMessageOpt = messageRepository
                .findFirstByChatIdAndFromMeTrueOrderByTimestampDesc(chat.getId());

        // Se não existe mensagem do usuário, não faz nada
        if (lastUserMessageOpt.isEmpty()) {
            return;
        }

        // Calcula quanto tempo passou desde a última mensagem do usuário
        Message lastUserMessage = lastUserMessageOpt.get();
        LocalDateTime lastMessageTime = lastUserMessage.getTimestamp();
        LocalDateTime now = LocalDateTime.now();
        long hoursSinceLastMessage = Duration.between(lastMessageTime, now).toHours();

        // Se passou tempo suficiente (definido em hours_delay em HORAS)
        // então move o chat para repescagem e envia a primeira mensagem automática
        if (hoursSinceLastMessage >= firstRoutine.getHoursDelay()) {
            // Passa a lista completa de rotinas
            moveToRepescagemAndSendFirstMessage(chat, user, routines);
        }
    }

    // Move um chat para a coluna de repescagem e envia a primeira mensagem da rotina
    private void moveToRepescagemAndSendFirstMessage(Chat chat, User user, List<RoutineText> routines) {
        try {
            // Busca ou cria um registro de estado de rotina para este chat
            ChatRoutineState state = chatRoutineStateRepository.findByChatId(chat.getId())
                    .orElse(new ChatRoutineState());

            // Calcula qual seria a próxima rotina a ser enviada (baseado em lastRoutineSent)
            int nextSequence = state.getLastRoutineSent() + 1;

            // Busca a rotina correspondente à próxima sequência
            Optional<RoutineText> routineToSendOpt = routines.stream()
                    .filter(r -> r.getSequenceNumber() == nextSequence)
                    .findFirst();

            // ✅ TRATAMENTO: Se não existe a próxima rotina configurada, move para Lead Frio
            if (routineToSendOpt.isEmpty()) {
                log.warn("⚠️ [CHAT: {}] Não há rotina #{} configurada. Movendo para Lead Frio.", chat.getId(), nextSequence);

                // Configura o estado mínimo necessário
                state.setChat(chat);
                state.setUser(user);
                state.setInRepescagem(false);
                chatRoutineStateRepository.save(state);

                // Move direto para Lead Frio
                moveToLeadFrio(chat, state, user);
                return;
            }

            RoutineText routineToSend = routineToSendOpt.get();

            // ✅ TRATAMENTO: Se o textContent da rotina está vazio/null, move para Lead Frio
            if (routineToSend.getTextContent() == null || routineToSend.getTextContent().trim().isEmpty()) {
                log.warn("⚠️ [CHAT: {}] Rotina #{} com textContent vazio. Movendo para Lead Frio.", chat.getId(), nextSequence);

                // Configura o estado mínimo necessário
                state.setChat(chat);
                state.setUser(user);
                state.setInRepescagem(false);
                chatRoutineStateRepository.save(state);

                // Move direto para Lead Frio
                moveToLeadFrio(chat, state, user);
                return;
            }

            // Guarda qual era a coluna anterior do chat
            String previousColumn = chat.getColumn();
            state.setInRepescagem(true);
            // Move o chat para a coluna de repescagem
            chat.setColumn(REPESCAGEM_COLUMN);
            chatRepository.save(chat);

            // Configura o estado inicial da rotina
            state.setChat(chat);
            state.setUser(user);
            state.setPreviousColumn(previousColumn); // Guarda de onde veio

            // ATUALIZAÇÃO DO CONTADOR SEM DEPENDER DO Z-API
            state.setLastRoutineSent(nextSequence); // Define a rotina que será enviada
            state.setInRepescagem(true); // Marca que está em repescagem

            // Busca e guarda o horário da última mensagem do usuário
            Optional<Message> lastUserMessageOpt = messageRepository
                    .findFirstByChatIdAndFromMeTrueOrderByTimestampDesc(chat.getId());
            lastUserMessageOpt.ifPresent(msg -> state.setLastUserMessageTime(msg.getTimestamp()));

            // Salva o estado no banco de dados, garantindo o incremento de lastRoutineSent
            // O lastAutomatedMessageSent será atualizado após a tentativa de envio
            chatRoutineStateRepository.save(state);

            // Busca a instância ativa do WhatsApp do usuário para enviar mensagens
            Optional<WebInstance> webInstanceOpt = webInstanceRepository.findByUserId(user.getId()).stream()
                    .filter(wi -> "ACTIVE".equals(wi.getStatus()))
                    .findFirst();

            // Se não tem instância ativa, não pode enviar mensagem
            if (webInstanceOpt.isEmpty()) {
                log.error("❌ [CHAT: {}] Usuário {} sem instância ativa", chat.getId(), user.getId());
                return;
            }

            WebInstance webInstance = webInstanceOpt.get();

            // ATUALIZAÇÃO DO TEMPO DE ENVIO ANTES DA TENTATIVA DO Z-API
            state.setLastAutomatedMessageSent(LocalDateTime.now());
            chatRoutineStateRepository.save(state);

            // Envia a mensagem via Z-API (WhatsApp)
            Map<String, Object> result = zapiMessageService.sendTextMessage(
                    webInstance,
                    chat.getPhone(),
                    routineToSend.getTextContent()
            );

            // Verifica se foi enviada com sucesso
            boolean sent = result != null && Boolean.TRUE.equals(result.get("success"));

            if (sent) {
                log.info("✅ [CHAT: {}] Chat movido para Repescagem → Rotina #{} enviada",
                        chat.getId(), routineToSend.getSequenceNumber());
            } else {
                log.error("❌ [CHAT: {}] Falha ao enviar rotina #{}", chat.getId(), routineToSend.getSequenceNumber());
            }

        } catch (Exception e) {
            log.error("❌ [CHAT: {}] Erro ao mover para repescagem", chat.getId(), e);
        }
    }

    // Verifica e envia a próxima mensagem de rotina para um chat já em repescagem
    private void checkAndSendNextRoutineMessage(Chat chat, User user, List<RoutineText> routines) {
        // Busca o estado de rotina deste chat
        Optional<ChatRoutineState> stateOpt = chatRoutineStateRepository.findByChatId(chat.getId());

        // Se não existe estado, não faz nada
        if (stateOpt.isEmpty()) {
            return;
        }

        ChatRoutineState state = stateOpt.get();

        // Verifica se o cliente respondeu olhando a última mensagem
        Optional<Message> lastMessageOpt = messageRepository
                .findTopByChatIdOrderByTimestampDesc(chat.getId());

        if (lastMessageOpt.isPresent()) {
            Message lastMessage = lastMessageOpt.get();

            // Se a última mensagem foi DO CLIENTE (fromMe=false), remove da repescagem
            if (!lastMessage.getFromMe()) {
                log.info("📨 [CHAT: {}] Cliente respondeu, removendo da repescagem", chat.getId());
                removeFromRepescagem(chat, state);
                return;
            }
        }

        // Se já enviou todas as 7 mensagens da rotina
        if (state.getLastRoutineSent() >= 7) {
            // Verifica se passou tempo suficiente para mover para Lead Frio
            if (state.getLastAutomatedMessageSent() != null) {
                // Busca a configuração da última rotina (rotina 7)
                Optional<RoutineText> lastRoutineOpt = routines.stream()
                        .filter(r -> r.getSequenceNumber() == 7)
                        .findFirst();

                if (lastRoutineOpt.isPresent()) {
                    RoutineText lastRoutine = lastRoutineOpt.get();
                    LocalDateTime now = LocalDateTime.now();

                    // Calcula quanto tempo passou desde a última mensagem automática
                    long hoursSinceLastAutomated = Duration.between(
                            state.getLastAutomatedMessageSent(),
                            now
                    ).toHours();

                    // Se passou tempo suficiente, move para Lead Frio (cliente não respondeu)
                    if (hoursSinceLastAutomated >= lastRoutine.getHoursDelay()) {
                        moveToLeadFrio(chat, state, user);
                    }
                }
            }
            return;
        }

        // Se já enviou alguma mensagem automática antes
        if (state.getLastAutomatedMessageSent() != null) {
            LocalDateTime now = LocalDateTime.now();

            // Calcula quanto tempo passou desde a última mensagem automática
            long hoursSinceLastAutomated = Duration.between(
                    state.getLastAutomatedMessageSent(),
                    now
            ).toHours();

            // Calcula qual seria a próxima rotina a ser enviada
            int nextSequence = state.getLastRoutineSent() + 1;

            // Busca a configuração da próxima rotina
            Optional<RoutineText> nextRoutineOpt = routines.stream()
                    .filter(r -> r.getSequenceNumber() == nextSequence)
                    .findFirst();

            // ✅ TRATAMENTO: Se não existe a próxima rotina configurada, move para Lead Frio
            if (nextRoutineOpt.isEmpty()) {
                log.warn("⚠️ [CHAT: {}] Não há rotina #{} configurada. Movendo para Lead Frio.", chat.getId(), nextSequence);
                moveToLeadFrio(chat, state, user);
                return;
            }

            RoutineText nextRoutine = nextRoutineOpt.get();

            // ✅ TRATAMENTO: Se o textContent está vazio/null, move para Lead Frio
            if (nextRoutine.getTextContent() == null || nextRoutine.getTextContent().trim().isEmpty()) {
                log.warn("⚠️ [CHAT: {}] Rotina #{} com textContent vazio. Movendo para Lead Frio.", chat.getId(), nextSequence);
                moveToLeadFrio(chat, state, user);
                return;
            }

            // Se passou tempo suficiente (definido no hours_delay da próxima rotina em HORAS)
            // então envia a próxima mensagem
            if (hoursSinceLastAutomated >= nextRoutine.getHoursDelay()) {

                // Incrementa e salva o estado ANTES do envio do Z-API
                state.setLastRoutineSent(nextSequence);
                chatRoutineStateRepository.save(state);

                sendNextRoutineMessage(chat, user, state, nextRoutine);
            }
        }
    }

    // Envia a próxima mensagem da rotina para um chat
    private void sendNextRoutineMessage(Chat chat, User user, ChatRoutineState state, RoutineText routine) {
        try {
            // Busca a instância ativa do WhatsApp do usuário
            Optional<WebInstance> webInstanceOpt = webInstanceRepository.findByUserId(user.getId()).stream()
                    .filter(wi -> "ACTIVE".equals(wi.getStatus()))
                    .findFirst();

            // Se não tem instância ativa, não pode enviar
            if (webInstanceOpt.isEmpty()) {
                log.error("❌ [CHAT: {}] Usuário {} sem WebInstance ativa", chat.getId(), user.getId());
                return;
            }

            WebInstance webInstance = webInstanceOpt.get();

            state.setInRepescagem(true);

            // Envia a mensagem via Z-API (WhatsApp)
            Map<String, Object> result = zapiMessageService.sendTextMessage(
                    webInstance,
                    chat.getPhone(),
                    routine.getTextContent()
            );

            // ATUALIZAÇÃO DO TEMPO DE ENVIO APÓS A TENTATIVA DO Z-API
            // lastRoutineSent já foi atualizado em checkAndSendNextRoutineMessage
            state.setLastAutomatedMessageSent(LocalDateTime.now());
            chatRoutineStateRepository.save(state);
            // FIM DA CORREÇÃO

            // Verifica se foi enviada com sucesso
            boolean sent = result != null && Boolean.TRUE.equals(result.get("success"));

            if (sent) {
                log.info("✅ [CHAT: {}] Rotina #{} enviada", chat.getId(), routine.getSequenceNumber());
            } else {
                // O log de erro acontece. O contador e o tempo de envio foram atualizados.
                log.error("❌ [CHAT: {}] Falha ao enviar rotina #{}. Contador e tempo atualizados.", chat.getId(), routine.getSequenceNumber());
            }

        } catch (Exception e) {
            log.error("❌ [CHAT: {}] Erro ao enviar rotina #{}", chat.getId(), routine.getSequenceNumber(), e);
        }
    }

    // ✅ MODIFICADO: Move um chat para a coluna "Lead Frio" após completar todas as rotinas sem resposta
    // Agora envia notificação SSE para atualizar o frontend
    private void moveToLeadFrio(Chat chat, ChatRoutineState state, User user) {
        try {
            // Move o chat para a coluna de Lead Frio
            chat.setColumn(LEAD_FRIO_COLUMN);
            chatRepository.save(chat);

            log.info("❄️ [CHAT: {}] Movido para Lead Frio", chat.getId());

            // Marca que não está mais em repescagem
            state.setInRepescagem(false);
            chatRoutineStateRepository.save(state);

            // ✅ NOVO: Enviar notificação SSE para atualizar frontend
            notificationService.sendTaskCompletedNotification(
                    user.getId(),
                    Map.of(
                            "chatId", chat.getId(),
                            "chatName", chat.getName(),
                            "chatColumn", chat.getColumn(),
                            "type", "repescagem-completed"
                    )
            );

            log.info("📡 [CHAT: {}] Notificação SSE enviada - Chat movido para Lead Frio", chat.getId());

        } catch (Exception e) {
            log.error("❌ [CHAT: {}] Erro ao mover para Lead Frio", chat.getId(), e);
        }
    }

    // Remove um chat da repescagem quando o cliente responde
    private void removeFromRepescagem(Chat chat, ChatRoutineState state) {
        try {
            // Retorna o chat para a coluna onde ele estava antes da repescagem
            String previousColumn = state.getPreviousColumn();

            // ✅ VALIDAÇÃO: Se previousColumn for null ou vazio, usar coluna padrão
            if (previousColumn == null || previousColumn.isEmpty()) {
                previousColumn = "inbox"; // Coluna padrão
                log.warn("⚠️ [CHAT: {}] previousColumn estava null/vazio, usando 'inbox' como padrão", chat.getId());
            }

            chat.setColumn(previousColumn);
            chatRepository.save(chat);

            log.info("✅ [CHAT: {}] Removido da Repescagem → {}", chat.getId(), previousColumn);

            // Marca que não está mais em repescagem
            // Mantém o lastRoutineSent para referência futura
            state.setInRepescagem(false);
            chatRoutineStateRepository.save(state);

        } catch (Exception e) {
            log.error("❌ [CHAT: {}] Erro ao remover da repescagem", chat.getId(), e);
        }
    }

    // Método público para resetar manualmente o estado de rotina de um chat
    // ✅ MELHORADO: Agora remove da Repescagem se o chat estiver lá
    @Transactional
    public void resetChatRoutineState(String chatId) {
        try {
            // Busca o chat
            Optional<Chat> chatOpt = chatRepository.findById(chatId);
            if (chatOpt.isEmpty()) {
                log.warn("⚠️ [CHAT: {}] Chat não encontrado ao resetar rotina", chatId);
                return;
            }

            Chat chat = chatOpt.get();
            boolean wasInRepescagem = REPESCAGEM_COLUMN.equals(chat.getColumn());

            // Busca o estado e reseta todos os valores
            chatRoutineStateRepository.findByChatId(chatId).ifPresent(state -> {
                state.setLastRoutineSent(0); // Volta para 0 (nenhuma rotina enviada)
                state.setLastAutomatedMessageSent(null); // Remove o horário da última mensagem
                state.setInRepescagem(false); // Marca que não está em repescagem
                chatRoutineStateRepository.save(state);

                log.info("✅ [CHAT: {}] Estado de rotina resetado", chatId);

                // ✅ NOVA LÓGICA: Se estava em Repescagem, remove da coluna
                if (wasInRepescagem) {
                    log.info("🔄 [CHAT: {}] Chat estava em Repescagem, removendo da coluna...", chatId);
                    removeFromRepescagem(chat, state);
                }
            });

        } catch (Exception e) {
            log.error("❌ [CHAT: {}] Erro ao resetar estado de rotina", chatId, e);
        }
    }
}