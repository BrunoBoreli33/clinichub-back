package com.example.loginauthapi.services;

import com.example.loginauthapi.entities.*;
import com.example.loginauthapi.repositories.*;
import com.example.loginauthapi.services.zapi.ZapiMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

import static com.example.loginauthapi.entities.ChatRoutineStatus.*;

// Serviço responsável por automatizar o envio de mensagens de rotina para clientes
@Component
@RequiredArgsConstructor
@Slf4j
public class RoutineAutomationService implements CommandLineRunner {

    // Repositórios para acessar dados do banco
    private final ChatRepository chatRepository;
    private final MessageRepository messageRepository;
    private final RoutineTextRepository routineTextRepository;
    private final ChatRoutineStateRepository chatRoutineStateRepository;
    private final UserRepository userRepository;
    private final WebInstanceRepository webInstanceRepository;
    private final TaskScheduler taskScheduler;

    // Serviço para enviar mensagens via WhatsApp (Z-API)
    private final ZapiMessageService zapiMessageService;

    // ✅ NOVO: Serviço para enviar notificações SSE
    private final NotificationService notificationService;

    // ✅ NOVO: Serviços e repositórios para enviar fotos e vídeos
    private final PhotoService photoService;
    private final VideoService videoService;
    private final PhotoRepository photoRepository;
    private final VideoRepository videoRepository;

    // Nomes das colunas/categorias onde os chats podem estar
    private static final String REPESCAGEM_COLUMN = "followup"; // Coluna de acompanhamento automático
    private static final String LEAD_FRIO_COLUMN = "cold_lead"; // Coluna de leads frios (sem resposta)

    private void scheduleNext() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        long delay;

        if (isHorarioComercial(now) && !isFeriado(now.toLocalDate())) {
            // Intervalo aleatório entre 2 e 5 minutos (120.000ms a 300.000ms)
            delay = 120000 + (long) (Math.random() * 180000);
            log.info("⏱️ Próxima execução agendada para daqui a {} segundos", delay / 1000);
        } else {
            // Se for noite ou feriado, calcula o tempo até as 08:00 do próximo dia útil
            delay = calcularMillisAteProximoDiaUtil(now);
            log.info("😴 Fora do horário comercial ou feriado. Sistema dormindo por {} minutos", delay / 60000);
        }

        taskScheduler.schedule(() -> {
            try {
                // Double check para garantir que não processe fora do horário por erro de cálculo
                if (isHorarioComercial(LocalDateTime.now(ZoneId.of("America/Sao_Paulo"))) && !isFeriado(LocalDate.now())) {
                    executeProcess();
                }
            } finally {
                scheduleNext(); // Reagendamento recursivo (a corrente nunca quebra)
            }
        }, Instant.now().plusMillis(delay));
    }

    private void executeProcess() {
        log.info("🤖 Iniciando processamento de rotinas automáticas");
        try {
            List<User> users = userRepository.findAll();
            for (User user : users) {
                processUserRoutines(user);
            }
            log.info("✅ Lote de rotinas processado com sucesso");
        } catch (Exception e) {
            log.error("❌ Erro durante o processamento de rotinas", e);
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

        int updatedCount = chatRepository.updateStatusForRepescagem(
                user.getId(),
                REPESCAGEM_COLUMN,
                ChatRoutineStatus.PENDING
        );

        log.info("Foram colocados {} chats na fila de repescagem.", updatedCount);

        // ✅ PASSO 1: Primeiro processa chats que JÁ ESTÃO em repescagem
        // FILTRO APLICADO: Processa apenas chats com activeInZapi = true
        Pageable limit = PageRequest.of(0, 2);
        List<Chat> repescagemChats = chatRepository.findByUserIdAndColumnAndNotGroupAndStatusIsPending(user.getId(), REPESCAGEM_COLUMN, limit)
                .stream()
                .filter(chat -> Boolean.TRUE.equals(chat.getActiveInZapi()))
                .toList();

        // Verifica cada chat em repescagem para enviar a próxima mensagem automática
        for (Chat chat : repescagemChats) {
            chat.setStatus(PROCESSING);
            chatRepository.saveAndFlush(chat);
            checkAndSendNextRoutineMessage(chat, user, routines);
        }

        int updatedForRepescagemCount = chatRepository.updateStatusForInRepescagem(
                user.getId(),
                Arrays.asList("hot_lead", "inbox"),
                ChatRoutineStatus.PENDING
        );

        log.info("Foram colocados {} chats na fila para repescagem.", updatedForRepescagemCount);

        // ✅ PASSO 2: Depois busca chats que PRECISAM ENTRAR em repescagem
        // FILTRO APLICADO: Processa apenas chats com activeInZapi = true
        List<Chat> monitoredChats = chatRepository.findByUserIdAndStatusIsPendingAndColumnIn(
                        user.getId(),
                        Arrays.asList("hot_lead", "inbox"),
                        limit
                ).stream()
                .filter(chat -> Boolean.TRUE.equals(chat.getActiveInZapi()))
                .toList();

        // Verifica cada chat para ver se é hora de mover para repescagem
        for (Chat chat : monitoredChats) {
            chat.setStatus(PROCESSING);
            chatRepository.saveAndFlush(chat);
            checkAndMoveToRepescagem(chat, user, firstRoutine, routines);
        }
    }

    // Verifica se um chat deve ser movido para repescagem e envia a primeira mensagem
    private void checkAndMoveToRepescagem(Chat chat, User user, RoutineText firstRoutine, List<RoutineText> routines) {

        // ✅ NOVO: Verifica se a repescagem já foi concluída anteriormente
        Optional<ChatRoutineState> stateOpt = chatRoutineStateRepository.findByChatId(chat.getId());
        if (stateOpt.isPresent() && Boolean.TRUE.equals(stateOpt.get().getRepescagemCompleted())) {
            log.info("✋ [CHAT: {}] Repescagem já foi concluída anteriormente. Não será reprocessado.", chat.getId());
            return;
        }

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
        LocalDateTime now = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
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
            Optional<RoutineText> nextRoutineToSendOpt = routines.stream()
                    .filter(r -> r.getSequenceNumber() == nextSequence)
                    .findFirst();

            // ✅ TRATAMENTO: Se não existe a próxima rotina configurada, move para Lead Frio
            if (nextRoutineToSendOpt.isEmpty()) {
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

            RoutineText routineToSend = nextRoutineToSendOpt.get();

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
            state.setLastAutomatedMessageSent(LocalDateTime.now(ZoneId.of("America/Sao_Paulo")));
            chatRoutineStateRepository.save(state);


            LocalDateTime now = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));

            // 1. Caso exista um horário já programado:
            if (state.getScheduledSendTime() != null) {
                if (now.isBefore(state.getScheduledSendTime())) {
                    return; // ainda não chegou o horário de enviar
                }
            }

            // 2. Caso não seja horário comercial:
            if (!isBusinessHours(now)) {
                LocalDateTime scheduled = nextBusinessWindow(now);
                state.setScheduledSendTime(scheduled);
                chatRoutineStateRepository.save(state);
                log.info("⏳ Mensagem reagendada para {} (horário comercial)", scheduled);
                return;
            }

            // 3. Se chegou aqui → ENVIAR
            state.setScheduledSendTime(null); // limpa a fila

            // ✅ NOVO: Enviar texto, fotos e vídeos
            sendRoutineWithMedia(
                    chat,
                    webInstance,
                    routineToSend
            );

            chat.setStatus(PENDING);
            chatRepository.saveAndFlush(chat);

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
                removeFromRepescagem(chat, state, user);
                return;
            }
        }

        if (state.getLastRoutineSent() >= routines.size()) {
            // Verifica se passou tempo suficiente para mover para Lead Frio
            if (state.getLastAutomatedMessageSent() != null) {
                // Busca a configuração da última rotina (rotina 7)
                Optional<RoutineText> lastRoutineOpt = routines.stream()
                        .filter(r -> r.getSequenceNumber() >= routines.size())
                        .findFirst();

                if (lastRoutineOpt.isPresent()) {
                    RoutineText lastRoutine = lastRoutineOpt.get();
                    LocalDateTime now = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));

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
            LocalDateTime now = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));

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

                // 1. Caso exista um horário já programado:
                if (state.getScheduledSendTime() != null) {
                    if (now.isBefore(state.getScheduledSendTime())) {
                        return; // ainda não chegou o horário de enviar
                    }
                }

                // 2. Caso não seja horário comercial:
                if (!isBusinessHours(now)) {
                    LocalDateTime scheduled = nextBusinessWindow(now);
                    state.setScheduledSendTime(scheduled);
                    chatRoutineStateRepository.save(state);
                    log.info("⏳ Mensagem reagendada para {} (horário comercial)", scheduled);
                    return;
                }
                // 3. Se chegou aqui → ENVIAR
                state.setScheduledSendTime(null); // limpa a fila

                sendNextRoutineMessage(chat, user, state, nextRoutine);
                // Incrementa e salva o estado DEPOIS do envio do Z-API
                state.setLastRoutineSent(nextSequence);
                chatRoutineStateRepository.save(state);

                chat.setStatus(PENDING);
                chatRepository.saveAndFlush(chat);
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

            // ✅ NOVO: Enviar texto, fotos e vídeos
            sendRoutineWithMedia(
                    chat,
                    webInstance,
                    routine
            );

            // ATUALIZAÇÃO DO TEMPO DE ENVIO APÓS O ENVIO
            state.setInRepescagem(true);
            state.setLastAutomatedMessageSent(LocalDateTime.now(ZoneId.of("America/Sao_Paulo")));
            chatRoutineStateRepository.save(state);
        } catch (Exception e) {
            log.error("❌ [CHAT: {}] Erro ao enviar rotina #{}", chat.getId(), routine.getSequenceNumber(), e);
        }
    }

    // ✅ MODIFICADO: Move um chat para a coluna "Lead Frio" após completar todas as rotinas sem resposta
    // Agora envia notificação SSE para atualizar o frontend
    private void moveToLeadFrio(Chat chat, ChatRoutineState state, User user) {
        try {
            // Move o chat para a coluna de Lead Frio
            chat.setStatus(SENT);
            chat.setColumn(LEAD_FRIO_COLUMN);
            chatRepository.save(chat);

            log.info("❄️ [CHAT: {}] Movido para Lead Frio", chat.getId());

            // Marca que não está mais em repescagem
            state.setInRepescagem(false);
            // ✅ NOVO: Marca que a repescagem foi concluída
            state.setRepescagemCompleted(true);
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
    // ✅ MODIFICADO: Tornado público para ser chamado pelo WebhookService
    @Transactional
    public void removeFromRepescagem(Chat chat, ChatRoutineState state, User user) {
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

            // ✅ NOVO: Enviar notificação SSE para atualizar frontend
            notificationService.sendTaskCompletedNotification(
                    user.getId(),
                    Map.of(
                            "chatId", chat.getId(),
                            "chatName", chat.getName(),
                            "chatColumn", chat.getColumn(),
                            "previousColumn", previousColumn,
                            "type", "chat-removed-from-repescagem"
                    )
            );

            log.info("📡 [CHAT: {}] Notificação SSE enviada - Chat removido da Repescagem", chat.getId());

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
                // ✅ NOVO: Reseta a flag de repescagem concluída
                state.setRepescagemCompleted(false);
                chatRoutineStateRepository.save(state);

                chatRepository.updateStatusByChatId(NONE, chatId);

                log.info("✅ [CHAT: {}] Estado de rotina resetado (incluindo flag repescagemCompleted)", chatId);

                // ✅ NOVA LÓGICA: Se estava em Repescagem, remove da coluna
                if (wasInRepescagem) {
                    log.info("🔄 [CHAT: {}] Chat estava em Repescagem, removendo da coluna...", chatId);
                    removeFromRepescagem(chat, state, chat.getWebInstance().getUser());
                }
            });

        } catch (Exception e) {
            log.error("❌ [CHAT: {}] Erro ao resetar estado de rotina", chatId, e);
        }
    }

    private boolean isBusinessHours(LocalDateTime dateTime) {
        // Considerar que o servidor pode estar em UTC. Ajustamos para BRT.
        ZonedDateTime brtTime = dateTime.atZone(ZoneId.of("America/Sao_Paulo"));

        DayOfWeek dow = brtTime.getDayOfWeek();
        int hour = brtTime.getHour();

        boolean weekday = dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
        boolean businessTime = hour >= 8 && hour < 20;

        return weekday && businessTime;
    }

    private LocalDateTime nextBusinessWindow(LocalDateTime now) {
        ZonedDateTime brt = now.atZone(ZoneId.of("America/Sao_Paulo"));

        // Ajusta para 08:00 do próprio dia, caso esteja antes
        if (isBusinessHours(now)) {
            return now; // Já está no horário comercial
        }

        // Avança para o próximo dia útil às 08:00
        ZonedDateTime next = brt.withHour(8).withMinute(0).withSecond(0).plusDays(1);

        // Pular finais de semana
        while (next.getDayOfWeek() == DayOfWeek.SATURDAY ||
                next.getDayOfWeek() == DayOfWeek.SUNDAY) {
            next = next.plusDays(1);
        }

        // Volta para LocalDateTime em UTC (para consistência no banco)
        return next.withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime();
    }

    /**
     * ✅ NOVO: Obter fotos da galeria para a rotina
     */
    private List<Photo> getRoutinePhotos(RoutineText routine) {
        if (routine.getPhotoIds() == null || routine.getPhotoIds().isEmpty()) {
            return new ArrayList<>();
        }

        List<String> photoIds = Arrays.asList(routine.getPhotoIds().split(","));
        return photoRepository.findAllById(photoIds);
    }

    /**
     * ✅ NOVO: Obter vídeos da galeria para a rotina
     */
    private List<Video> getRoutineVideos(RoutineText routine) {
        if (routine.getVideoIds() == null || routine.getVideoIds().isEmpty()) {
            return new ArrayList<>();
        }

        List<String> videoIds = Arrays.asList(routine.getVideoIds().split(","));
        return videoRepository.findAllById(videoIds);
    }

    /**
     * ✅ NOVO: Enviar texto, fotos e vídeos de uma rotina
     * Segue o fluxo: texto → fotos → vídeos
     */
    private void sendRoutineWithMedia(
            Chat chat,
            WebInstance webInstance,
            RoutineText routine
    ) {
        String greeting = randomGreeting();
        String fallbackGreeting = randomFallbackGreeting();

        String chatName = chat.getName();
        String receiverName = chatName == null || chatName.isBlank() ? fallbackGreeting : greeting + chat.getName();

        String messageToSend = receiverName + ", " + routine.getTextContent();
        // ===== PASSO 1: Enviar mensagem de texto =====
        boolean result = zapiMessageService.sendTextMessage(
                webInstance,
                chat.getPhone(),
                messageToSend,
                true
        );

        if(result) {
            // ===== PASSO 2: Enviar fotos (se houver) =====
            List<Photo> photos = getRoutinePhotos(routine);
            if (!photos.isEmpty()) {
                log.info("📷 [CHAT: {}] Enviando {} foto(s)", chat.getId(), photos.size());
                for (Photo photo : photos) {

                    try {
                        photoService.saveOutgoingPhoto(
                                chat.getId(),
                                chat.getPhone(),
                                photo.getImageUrl(),
                                webInstance.getId(),
                                null
                        );
                    } catch (DataIntegrityViolationException e) {
                        log.warn("⚠️ Erro de duplicação ao salvar foto. Continuando...");
                    }

                    zapiMessageService.sendImage(
                            webInstance,
                            chat.getPhone(),
                            photo.getImageUrl(),
                            true
                    );

                }
            }

            // ===== PASSO 3: Enviar vídeos (se houver) =====
            List<Video> videos = getRoutineVideos(routine);
            if (!videos.isEmpty()) {
                log.info("🎥 [CHAT: {}] Enviando {} vídeo(s)", chat.getId(), videos.size());
                for (Video video : videos) {

                    try {
                        videoService.saveOutgoingVideo(
                                chat.getId(),
                                chat.getPhone(),
                                video.getVideoUrl(),
                                webInstance.getId(),
                                null
                        );
                    } catch (DataIntegrityViolationException e) {
                        log.warn("⚠️ Erro de duplicação ao salvar vídeo. Continuando...");
                    }

                    zapiMessageService.sendVideo(
                            webInstance,
                            chat.getPhone(),
                            video.getVideoUrl(),
                            true
                    );

                }
            }

        }

    }

    private static final List<String> GREETINGS = List.of(
            "Olá ",
            "Oi ",
            "Oii ",
            "Oiii ",
            "Oiê ",
            "Oiee ",
            "Oieeê "
    );

    private static final List<String> FALLBACK_GREETINGS = List.of(
            "Oii querid@",
            "Oiii",
            "Olá! Espero que esteja bem",
            "Oiê! Como vai?"
    );

    private static final Random RANDOM = new Random();

    private static String randomGreeting() {
        return GREETINGS.get(RANDOM.nextInt(GREETINGS.size()));
    }

    private static String randomFallbackGreeting() {
        return FALLBACK_GREETINGS.get(RANDOM.nextInt(FALLBACK_GREETINGS.size()));
    }

    private boolean isFeriado(LocalDate date) {
        List<MonthDay> feriadosFixos = List.of(
                MonthDay.of(1, 1),   // Ano Novo
                MonthDay.of(2, 16),  // Carnaval
                MonthDay.of(2, 17),  // Carnaval
                MonthDay.of(2, 18),  // Carnaval
                MonthDay.of(4, 3),   // Paixao de Cristo
                MonthDay.of(4, 21),  // Tiradentes
                MonthDay.of(5, 1),   // Dia do Trabalho
                MonthDay.of(6, 4),   // Corpus Christ
                MonthDay.of(9, 7),   // Independência
                MonthDay.of(10, 12), // Padroeira
                MonthDay.of(11, 2),  // Finados
                MonthDay.of(11, 15), // República
                MonthDay.of(11, 20), // CN
                MonthDay.of(12, 25)  // Natal
        );

        return feriadosFixos.contains(MonthDay.from(date));
    }

    @Override
    public void run(String... args) throws Exception {
        log.info("🚀 Iniciando Motor de Automação Dinâmica");
        scheduleNext();
    }

    private boolean isHorarioComercial(LocalDateTime now) {
        int hour = now.getHour();
        DayOfWeek day = now.getDayOfWeek();
        return hour >= 8 && hour < 20 && day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY;
    }

    private long calcularMillisAteProximoDiaUtil(LocalDateTime now) {
        LocalDateTime nextRun = now.withHour(8).withMinute(0).withSecond(0).withNano(0);

        // Se já passou das 08:00 de hoje, agenda para amanhã
        if (now.getHour() >= 20 || (now.getHour() < 8)) {
            if (now.getHour() >= 20) nextRun = nextRun.plusDays(1);
        }

        // Se cair no fim de semana, pula para segunda
        while (nextRun.getDayOfWeek() == DayOfWeek.SATURDAY || nextRun.getDayOfWeek() == DayOfWeek.SUNDAY || isFeriado(nextRun.toLocalDate())) {
            nextRun = nextRun.plusDays(1);
        }

        return Duration.between(now, nextRun).toMillis();
    }
}