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

    // Nomes das colunas/categorias onde os chats podem estar
    private static final String REPESCAGEM_COLUMN = "followup"; // Coluna de acompanhamento automático
    private static final String LEAD_FRIO_COLUMN = "cold_lead"; // Coluna de leads frios (sem resposta)

    // Método executado automaticamente a cada 60 segundos (1 minuto)
    @Scheduled(fixedRate = 60000)
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
        // Isso evita que chats recém-movidos sejam processados duas vezes no mesmo ciclo
        List<Chat> repescagemChats = chatRepository.findByUserIdAndColumn(user.getId(), REPESCAGEM_COLUMN);

        // Verifica cada chat em repescagem para enviar a próxima mensagem automática
        for (Chat chat : repescagemChats) {
            checkAndSendNextRoutineMessage(chat, user, routines);
        }

        // ✅ PASSO 2: Depois busca chats que PRECISAM ENTRAR em repescagem
        // Como isso é feito por último, esses chats não serão processados duas vezes
        List<Chat> monitoredChats = chatRepository.findByUserIdAndColumnIn(
                user.getId(),
                Arrays.asList("hot_lead", "inbox")
        );

        // Verifica cada chat para ver se é hora de mover para repescagem
        for (Chat chat : monitoredChats) {
            checkAndMoveToRepescagem(chat, user, firstRoutine, routines);
        }
    }

    // Verifica se um chat deve ser movido para repescagem e envia a primeira mensagem
    private void checkAndMoveToRepescagem(Chat chat, User user, RoutineText firstRoutine, List<RoutineText> routines) {
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
        long minutesSinceLastMessage = Duration.between(lastMessageTime, now).toMinutes();

        // Se passou tempo suficiente (definido em hours_delay, mas usado como minutos no modo desenvolvimento)
        // então move o chat para repescagem e envia a primeira mensagem automática
        if (minutesSinceLastMessage >= firstRoutine.getHoursDelay()) {
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

            // Se não existe a próxima rotina configurada (ex: se lastRoutineSent=7 e tentou entrar repescagem)
            if (routineToSendOpt.isEmpty()) {
                log.warn("⚠️ [CHAT: {}] Não há rotina #{} configurada para enviar ao entrar em repescagem", chat.getId(), nextSequence);
                return;
            }

            RoutineText routineToSend = routineToSendOpt.get();

            // Guarda qual era a coluna anterior do chat
            String previousColumn = chat.getColumn();

            // Move o chat para a coluna de repescagem
            chat.setColumn(REPESCAGEM_COLUMN);
            chatRepository.save(chat);

            // Configura o estado inicial da rotina
            state.setChat(chat);
            state.setUser(user);
            state.setPreviousColumn(previousColumn); // Guarda de onde veio

            // **ATUALIZAÇÃO DO CONTADOR SEM DEPENDER DO Z-API**
            state.setLastRoutineSent(nextSequence); // Define a rotina que será enviada
            state.setInRepescagem(true); // Marca que está em repescagem

            // Busca e guarda o horário da última mensagem do usuário
            Optional<Message> lastUserMessageOpt = messageRepository
                    .findFirstByChatIdAndFromMeTrueOrderByTimestampDesc(chat.getId());
            lastUserMessageOpt.ifPresent(msg -> state.setLastUserMessageTime(msg.getTimestamp()));

            // Salva o estado no banco de dados, garantindo o incremento de lastRoutineSent
            chatRoutineStateRepository.save(state);
            // **FIM DA CORREÇÃO**

            // Busca a instância ativa do WhatsApp do usuário para enviar mensagens
            Optional<WebInstance> webInstanceOpt = webInstanceRepository.findByUserId(user.getId()).stream()
                    .filter(wi -> "ACTIVE".equals(wi.getStatus()))
                    .findFirst();

            // Se não tem instância ativa, não pode enviar mensagem
            if (webInstanceOpt.isEmpty()) {
                log.error("❌ [CHAT: {}] Usuário {} não possui WebInstance ativa", chat.getId(), user.getId());
                return;
            }

            WebInstance webInstance = webInstanceOpt.get();

            // Envia a mensagem da rotina via Z-API (WhatsApp)
            Map<String, Object> result = zapiMessageService.sendTextMessage(
                    webInstance,
                    chat.getPhone(),
                    routineToSend.getTextContent()
            );

            // Verifica se a mensagem foi enviada com sucesso
            boolean sent = result != null && Boolean.TRUE.equals(result.get("success"));

            if (sent) {
                // SÓ ATUALIZA O HORÁRIO DE ENVIO E SALVA O ESTADO EM CASO DE SUCESSO DO Z-API
                state.setLastAutomatedMessageSent(LocalDateTime.now());
                chatRoutineStateRepository.save(state);
                log.info("✅ [CHAT: {}] Rotina #{} enviada ao entrar em repescagem", chat.getId(), nextSequence);
            } else {
                log.error("❌ [CHAT: {}] Falha ao enviar rotina #{} ao entrar em repescagem. Contador já atualizado.", chat.getId(), nextSequence);
            }

        } catch (Exception e) {
            log.error("❌ [CHAT: {}] Erro ao mover para repescagem", chat.getId(), e);
        }
    }

    // Verifica se é hora de enviar a próxima mensagem da rotina para um chat em repescagem
    private void checkAndSendNextRoutineMessage(Chat chat, User user, List<RoutineText> routines) {
        // Busca o estado da rotina deste chat
        Optional<ChatRoutineState> stateOpt = chatRoutineStateRepository.findByChatId(chat.getId());

        // Se não tem estado, algo está errado (chat em repescagem sem estado)
        if (stateOpt.isEmpty()) {
            log.warn("⚠️ [CHAT: {}] Chat em Repescagem sem estado de rotina", chat.getId());
            return;
        }

        ChatRoutineState state = stateOpt.get();

        // Busca todas as mensagens do chat, mais recentes primeiro
        List<Message> messages = messageRepository.findByChatIdOrderByTimestampDesc(chat.getId());

        if (!messages.isEmpty()) {
            // Pega a última mensagem do chat
            Message lastMessage = messages.get(0);

            // Se a última mensagem foi DO CLIENTE (não do usuário), significa que o cliente respondeu
            // Neste caso, remove da repescagem porque o cliente está engajado novamente.
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
                    long minutesSinceLastAutomated = Duration.between(
                            state.getLastAutomatedMessageSent(),
                            now
                    ).toMinutes();

                    // Se passou tempo suficiente, move para Lead Frio (cliente não respondeu)
                    if (minutesSinceLastAutomated >= lastRoutine.getHoursDelay()) {
                        moveToLeadFrio(chat, state);
                    }
                }
            }
            return;
        }

        // Se já enviou alguma mensagem automática antes
        if (state.getLastAutomatedMessageSent() != null) {
            LocalDateTime now = LocalDateTime.now();

            // Calcula quanto tempo passou desde a última mensagem automática
            long minutesSinceLastAutomated = Duration.between(
                    state.getLastAutomatedMessageSent(),
                    now
            ).toMinutes();

            // Calcula qual seria a próxima rotina a ser enviada
            int nextSequence = state.getLastRoutineSent() + 1;

            // Busca a configuração da próxima rotina
            Optional<RoutineText> nextRoutineOpt = routines.stream()
                    .filter(r -> r.getSequenceNumber() == nextSequence)
                    .findFirst();

            // Se não existe a próxima rotina configurada, não faz nada
            if (nextRoutineOpt.isEmpty()) {
                return;
            }

            RoutineText nextRoutine = nextRoutineOpt.get();

            // Se passou tempo suficiente (definido no hours_delay da próxima rotina)
            // então envia a próxima mensagem
            if (minutesSinceLastAutomated >= nextRoutine.getHoursDelay()) {

                // **CORREÇÃO: Incrementa e salva o estado ANTES do envio do Z-API**
                state.setLastRoutineSent(nextSequence);
                chatRoutineStateRepository.save(state);
                // **FIM DA CORREÇÃO**

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

            // Envia a mensagem via Z-API (WhatsApp)
            Map<String, Object> result = zapiMessageService.sendTextMessage(
                    webInstance,
                    chat.getPhone(),
                    routine.getTextContent()
            );

            // Verifica se foi enviada com sucesso
            boolean sent = result != null && Boolean.TRUE.equals(result.get("success"));

            if (sent) {
                // Atualiza o estado: marca SÓ o horário, pois o lastRoutineSent já foi atualizado em checkAndSendNextRoutineMessage
                // **REMOVIDA A LINHA: state.setLastRoutineSent(routine.getSequenceNumber());**
                state.setLastAutomatedMessageSent(LocalDateTime.now());
                chatRoutineStateRepository.save(state);

                log.info("✅ [CHAT: {}] Rotina #{} enviada", chat.getId(), routine.getSequenceNumber());
            } else {
                // O log de erro acontece, mas o lastRoutineSent já foi atualizado, garantindo a progressão.
                // Como lastAutomatedMessageSent não foi atualizado, o próximo ciclo tentará esta rotina novamente.
                log.error("❌ [CHAT: {}] Falha ao enviar rotina #{}. Contador já atualizado.", chat.getId(), routine.getSequenceNumber());
            }

        } catch (Exception e) {
            log.error("❌ [CHAT: {}] Erro ao enviar rotina #{}", chat.getId(), routine.getSequenceNumber(), e);
        }
    }

    // Move um chat para a coluna "Lead Frio" após completar todas as rotinas sem resposta
    private void moveToLeadFrio(Chat chat, ChatRoutineState state) {
        try {
            // Move o chat para a coluna de Lead Frio
            chat.setColumn(LEAD_FRIO_COLUMN);
            chatRepository.save(chat);

            log.info("✅ [CHAT: {}] Movido para Lead Frio", chat.getId());

            // Marca que não está mais em repescagem
            state.setInRepescagem(false);
            chatRoutineStateRepository.save(state);

        } catch (Exception e) {
            log.error("❌ [CHAT: {}] Erro ao mover para Lead Frio", chat.getId(), e);
        }
    }

    // Remove um chat da repescagem quando o cliente responde
    private void removeFromRepescagem(Chat chat, ChatRoutineState state) {
        try {
            // Retorna o chat para a coluna onde ele estava antes da repescagem
            String previousColumn = state.getPreviousColumn();
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
    // Útil para quando se quer reiniciar o processo de repescagem do zero
    @Transactional
    public void resetChatRoutineState(String chatId) {
        // Busca o estado e reseta todos os valores
        chatRoutineStateRepository.findByChatId(chatId).ifPresent(state -> {
            state.setLastRoutineSent(0); // Volta para 0 (nenhuma rotina enviada)
            state.setLastAutomatedMessageSent(null); // Remove o horário da última mensagem
            state.setInRepescagem(false); // Marca que não está em repescagem
            chatRoutineStateRepository.save(state);

            log.info("✅ [CHAT: {}] Estado de rotina resetado", chatId);
        });
    }
}