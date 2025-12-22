package com.example.loginauthapi.services;

import com.example.loginauthapi.dto.PhotoDTO;
import com.example.loginauthapi.dto.VideoDTO;
import com.example.loginauthapi.entities.*;
import com.example.loginauthapi.repositories.*;
import com.example.loginauthapi.services.zapi.ZapiMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Serviço responsável por automatizar o envio de mensagens de rotina
 * COM PROTEÇÃO CONTRA BAN do WhatsApp/Meta
 *
 * Implementa:
 * - Rate limiting por instância (max 10 msg/min, 100 msg/hour)
 * - Delays progressivos entre mensagens
 * - Processamento assíncrono não-bloqueante
 * - Retry com backoff exponencial
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RoutineAutomationService {

    private final ChatRepository chatRepository;
    private final MessageRepository messageRepository;
    private final RoutineTextRepository routineTextRepository;
    private final ChatRoutineStateRepository chatRoutineStateRepository;
    private final UserRepository userRepository;
    private final WebInstanceRepository webInstanceRepository;
    private final ZapiMessageService zapiMessageService;
    private final NotificationService notificationService;
    private final PhotoService photoService;
    private final VideoService videoService;
    private final PhotoRepository photoRepository;
    private final VideoRepository videoRepository;

    private static final String REPESCAGEM_COLUMN = "followup";
    private static final String LEAD_FRIO_COLUMN = "cold_lead";

    // ========== RATE LIMITING CONFIGURATION ==========

    /**
     * Rate limiters por instância do WhatsApp
     * Key: webInstanceId
     * Value: RateLimiter com controle de mensagens/minuto
     */
    private final ConcurrentHashMap<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();

    /**
     * Contador de mensagens por hora para cada instância
     * Evita ban por volume excessivo
     */
    private final ConcurrentHashMap<String, HourlyMessageCounter> hourlyCounters = new ConcurrentHashMap<>();

    /**
     * Executor para processamento assíncrono COM controle de pool
     */
    private final ExecutorService messageExecutor = Executors.newFixedThreadPool(
            5, // Máximo 5 threads simultâneas
            r -> {
                Thread t = new Thread(r);
                t.setName("routine-msg-" + System.currentTimeMillis());
                t.setDaemon(true);
                return t;
            }
    );

    // ========== CONFIGURAÇÕES DE SEGURANÇA ==========

    // Limites por instância (evita ban)
    private static final int MAX_MESSAGES_PER_MINUTE = 10;
    private static final int MAX_MESSAGES_PER_HOUR = 100;

    // Delays entre envios (em segundos)
    private static final int MIN_DELAY_BETWEEN_MESSAGES = 30; // 30s mínimo
    private static final int MAX_DELAY_BETWEEN_MESSAGES = 90; // 90s máximo
    private static final int DELAY_BETWEEN_PHOTOS = 15; // 15s entre fotos
    private static final int DELAY_BETWEEN_VIDEOS = 30; // 30s entre vídeos
    private static final int DELAY_AFTER_TEXT = 25; // 25s após texto antes de mídia

    // Retry configuration
    private static final int MAX_RETRY_ATTEMPTS = 3;
    private static final int INITIAL_RETRY_DELAY_MS = 5000; // 5s

    // ========== CRON JOB - Executa a cada minuto em horário comercial ==========

    @Scheduled(cron = "0 * 8-20 * * MON-FRI", zone = "America/Sao_Paulo")
    public void processRoutineAutomation() {
        log.info("🤖 Iniciando processamento de rotinas automáticas");

        try {
            List<User> users = userRepository.findAll();

            for (User user : users) {
                // Processa cada usuário de forma assíncrona
                CompletableFuture.runAsync(
                        () -> processUserRoutines(user),
                        messageExecutor
                ).exceptionally(ex -> {
                    log.error("❌ Erro ao processar usuário {}", user.getId(), ex);
                    return null;
                });
            }

            log.info("✅ Processamento de rotinas iniciado para {} usuários", users.size());
        } catch (Exception e) {
            log.error("❌ Erro crítico ao processar rotinas automáticas", e);
        }
    }

    // ========== PROCESSAMENTO POR USUÁRIO ==========

    void processUserRoutines(User user) {
        List<RoutineText> routines = routineTextRepository.findByUserIdOrderBySequenceNumberAsc(user.getId());

        if (routines.isEmpty()) {
            return;
        }

        RoutineText firstRoutine = routines.stream()
                .filter(r -> r.getSequenceNumber() == 1)
                .findFirst()
                .orElse(null);

        if (firstRoutine == null) {
            log.warn("⚠️ [USER: {}] Primeira rotina (sequence=1) não configurada", user.getId());
            return;
        }

        // PASSO 1: Processar chats já em repescagem
        List<Chat> repescagemChats = chatRepository
                .findByUserIdAndColumnAndNotGroup(user.getId(), REPESCAGEM_COLUMN)
                .stream()
                .filter(chat -> Boolean.TRUE.equals(chat.getActiveInZapi()))
                .toList();

        for (Chat chat : repescagemChats) {
            scheduleMessageWithDelay(
                    () -> checkAndSendNextRoutineMessage(chat, user, routines),
                    getRandomDelay()
            );
        }

        // PASSO 2: Processar chats que precisam entrar em repescagem
        List<Chat> monitoredChats = chatRepository
                .findByUserIdAndColumnIn(user.getId(), Arrays.asList("hot_lead", "inbox"))
                .stream()
                .filter(chat -> Boolean.TRUE.equals(chat.getActiveInZapi()))
                .toList();

        for (Chat chat : monitoredChats) {
            scheduleMessageWithDelay(
                    () -> checkAndMoveToRepescagem(chat, user, firstRoutine, routines),
                    getRandomDelay()
            );
        }
    }

    // ========== RATE LIMITING HELPERS ==========

    /**
     * Obtém ou cria um rate limiter para uma instância
     */
    private RateLimiter getRateLimiter(String webInstanceId) {
        return rateLimiters.computeIfAbsent(
                webInstanceId,
                id -> new RateLimiter(MAX_MESSAGES_PER_MINUTE)
        );
    }

    /**
     * Obtém ou cria um contador horário para uma instância
     */
    private HourlyMessageCounter getHourlyCounter(String webInstanceId) {
        return hourlyCounters.computeIfAbsent(
                webInstanceId,
                id -> new HourlyMessageCounter(MAX_MESSAGES_PER_HOUR)
        );
    }

    /**
     * Verifica se pode enviar mensagem (rate limiting)
     */
    private boolean canSendMessage(String webInstanceId) {
        RateLimiter limiter = getRateLimiter(webInstanceId);
        HourlyMessageCounter counter = getHourlyCounter(webInstanceId);

        if (!counter.canSend()) {
            log.warn("⏸️ [INSTANCE: {}] Limite horário atingido ({}/hora)",
                    webInstanceId, MAX_MESSAGES_PER_HOUR);
            return false;
        }

        if (!limiter.tryAcquire()) {
            log.warn("⏸️ [INSTANCE: {}] Limite por minuto atingido ({}/min)",
                    webInstanceId, MAX_MESSAGES_PER_MINUTE);
            return false;
        }

        return true;
    }

    /**
     * Registra envio de mensagem nos contadores
     */
    private void recordMessageSent(String webInstanceId) {
        getHourlyCounter(webInstanceId).recordMessage();
    }

    /**
     * Calcula delay aleatório entre mensagens (evita padrões detectáveis)
     */
    private int getRandomDelay() {
        Random random = new Random();
        return MIN_DELAY_BETWEEN_MESSAGES +
                random.nextInt(MAX_DELAY_BETWEEN_MESSAGES - MIN_DELAY_BETWEEN_MESSAGES + 1);
    }

    /**
     * Agenda execução com delay
     */
    private void scheduleMessageWithDelay(Runnable task, int delaySeconds) {
        CompletableFuture.runAsync(() -> {
            try {
                TimeUnit.SECONDS.sleep(delaySeconds);
                task.run();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("⚠️ Tarefa interrompida");
            }
        }, messageExecutor);
    }

    // ========== LÓGICA PRINCIPAL (MODIFICADA) ==========

    @Async
    private void checkAndMoveToRepescagem(Chat chat, User user, RoutineText firstRoutine, List<RoutineText> routines) {
        Optional<ChatRoutineState> stateOpt = chatRoutineStateRepository.findByChatId(chat.getId());
        if (stateOpt.isPresent() && Boolean.TRUE.equals(stateOpt.get().getRepescagemCompleted())) {
            log.info("✋ [CHAT: {}] Repescagem já concluída", chat.getId());
            return;
        }

        Optional<Message> lastAnyMessageOpt = messageRepository
                .findTopByChatIdOrderByTimestampDesc(chat.getId());

        if (lastAnyMessageOpt.isEmpty()) {
            return;
        }

        Message lastAnyMessage = lastAnyMessageOpt.get();
        if (!lastAnyMessage.getFromMe()) {
            return; // Cliente respondeu
        }

        Optional<Message> lastUserMessageOpt = messageRepository
                .findFirstByChatIdAndFromMeTrueOrderByTimestampDesc(chat.getId());

        if (lastUserMessageOpt.isEmpty()) {
            return;
        }

        Message lastUserMessage = lastUserMessageOpt.get();
        LocalDateTime lastMessageTime = lastUserMessage.getTimestamp();
        LocalDateTime now = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));
        long hoursSinceLastMessage = Duration.between(lastMessageTime, now).toHours();

        if (hoursSinceLastMessage >= firstRoutine.getHoursDelay()) {
            moveToRepescagemAndSendFirstMessage(chat, user, routines);
        }
    }

    private void moveToRepescagemAndSendFirstMessage(Chat chat, User user, List<RoutineText> routines) {
        try {
            ChatRoutineState state = chatRoutineStateRepository.findByChatId(chat.getId())
                    .orElse(new ChatRoutineState());

            int nextSequence = state.getLastRoutineSent() + 1;

            Optional<RoutineText> routineToSendOpt = routines.stream()
                    .filter(r -> r.getSequenceNumber() == nextSequence)
                    .findFirst();

            if (routineToSendOpt.isEmpty()) {
                log.warn("⚠️ [CHAT: {}] Rotina #{} não configurada", chat.getId(), nextSequence);
                state.setChat(chat);
                state.setUser(user);
                state.setInRepescagem(false);
                chatRoutineStateRepository.save(state);
                moveToLeadFrio(chat, state, user);
                return;
            }

            RoutineText routineToSend = routineToSendOpt.get();

            if (routineToSend.getTextContent() == null || routineToSend.getTextContent().trim().isEmpty()) {
                log.warn("⚠️ [CHAT: {}] Rotina #{} vazia", chat.getId(), nextSequence);
                state.setChat(chat);
                state.setUser(user);
                state.setInRepescagem(false);
                chatRoutineStateRepository.save(state);
                moveToLeadFrio(chat, state, user);
                return;
            }

            String previousColumn = chat.getColumn();
            chat.setColumn(REPESCAGEM_COLUMN);
            chatRepository.save(chat);

            state.setChat(chat);
            state.setUser(user);
            state.setPreviousColumn(previousColumn);
            state.setLastRoutineSent(nextSequence);
            state.setInRepescagem(true);

            Optional<Message> lastUserMessageOpt = messageRepository
                    .findFirstByChatIdAndFromMeTrueOrderByTimestampDesc(chat.getId());
            lastUserMessageOpt.ifPresent(msg -> state.setLastUserMessageTime(msg.getTimestamp()));

            chatRoutineStateRepository.save(state);

            Optional<WebInstance> webInstanceOpt = webInstanceRepository.findByUserId(user.getId()).stream()
                    .filter(wi -> "ACTIVE".equals(wi.getStatus()))
                    .findFirst();

            if (webInstanceOpt.isEmpty()) {
                log.error("❌ [CHAT: {}] Sem instância ativa", chat.getId());
                return;
            }

            WebInstance webInstance = webInstanceOpt.get();

            state.setLastAutomatedMessageSent(LocalDateTime.now(ZoneId.of("America/Sao_Paulo")));
            chatRoutineStateRepository.save(state);

            LocalDateTime now = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));

            if (state.getScheduledSendTime() != null && now.isBefore(state.getScheduledSendTime())) {
                return;
            }

            if (!isBusinessHours(now)) {
                LocalDateTime scheduled = nextBusinessWindow(now);
                state.setScheduledSendTime(scheduled);
                chatRoutineStateRepository.save(state);
                log.info("⏳ Mensagem reagendada para {}", scheduled);
                return;
            }

            state.setScheduledSendTime(null);

            // ✅ ENVIO COM RATE LIMITING E RETRY
            sendRoutineWithMediaSafe(
                    chat,
                    webInstance,
                    routineToSend,
                    "Rotina #" + routineToSend.getSequenceNumber()
            );

        } catch (Exception e) {
            log.error("❌ [CHAT: {}] Erro ao mover para repescagem", chat.getId(), e);
        }
    }

    @Async
    private void checkAndSendNextRoutineMessage(Chat chat, User user, List<RoutineText> routines) {
        Optional<ChatRoutineState> stateOpt = chatRoutineStateRepository.findByChatId(chat.getId());

        if (stateOpt.isEmpty()) {
            return;
        }

        ChatRoutineState state = stateOpt.get();

        Optional<Message> lastMessageOpt = messageRepository
                .findTopByChatIdOrderByTimestampDesc(chat.getId());

        if (lastMessageOpt.isPresent()) {
            Message lastMessage = lastMessageOpt.get();

            if (!lastMessage.getFromMe()) {
                log.info("📨 [CHAT: {}] Cliente respondeu", chat.getId());
                removeFromRepescagem(chat, state, user);
                return;
            }
        }

        if (state.getLastRoutineSent() >= 7) {
            if (state.getLastAutomatedMessageSent() != null) {
                Optional<RoutineText> lastRoutineOpt = routines.stream()
                        .filter(r -> r.getSequenceNumber() == 7)
                        .findFirst();

                if (lastRoutineOpt.isPresent()) {
                    RoutineText lastRoutine = lastRoutineOpt.get();
                    LocalDateTime now = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));

                    long hoursSinceLastAutomated = Duration.between(
                            state.getLastAutomatedMessageSent(),
                            now
                    ).toHours();

                    if (hoursSinceLastAutomated >= lastRoutine.getHoursDelay()) {
                        moveToLeadFrio(chat, state, user);
                    }
                }
            }
            return;
        }

        if (state.getLastAutomatedMessageSent() != null) {
            LocalDateTime now = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));

            long hoursSinceLastAutomated = Duration.between(
                    state.getLastAutomatedMessageSent(),
                    now
            ).toHours();

            int nextSequence = state.getLastRoutineSent() + 1;

            Optional<RoutineText> nextRoutineOpt = routines.stream()
                    .filter(r -> r.getSequenceNumber() == nextSequence)
                    .findFirst();

            if (nextRoutineOpt.isEmpty()) {
                log.warn("⚠️ [CHAT: {}] Rotina #{} não configurada", chat.getId(), nextSequence);
                moveToLeadFrio(chat, state, user);
                return;
            }

            RoutineText nextRoutine = nextRoutineOpt.get();

            if (nextRoutine.getTextContent() == null || nextRoutine.getTextContent().trim().isEmpty()) {
                log.warn("⚠️ [CHAT: {}] Rotina #{} vazia", chat.getId(), nextSequence);
                moveToLeadFrio(chat, state, user);
                return;
            }

            if (hoursSinceLastAutomated >= nextRoutine.getHoursDelay()) {
                state.setLastRoutineSent(nextSequence);
                chatRoutineStateRepository.save(state);

                if (state.getScheduledSendTime() != null && now.isBefore(state.getScheduledSendTime())) {
                    return;
                }

                if (!isBusinessHours(now)) {
                    LocalDateTime scheduled = nextBusinessWindow(now);
                    state.setScheduledSendTime(scheduled);
                    chatRoutineStateRepository.save(state);
                    log.info("⏳ Mensagem reagendada para {}", scheduled);
                    return;
                }

                state.setScheduledSendTime(null);
                sendNextRoutineMessage(chat, user, state, nextRoutine);
            }
        }
    }

    private void sendNextRoutineMessage(Chat chat, User user, ChatRoutineState state, RoutineText routine) {
        try {
            Optional<WebInstance> webInstanceOpt = webInstanceRepository.findByUserId(user.getId()).stream()
                    .filter(wi -> "ACTIVE".equals(wi.getStatus()))
                    .findFirst();

            if (webInstanceOpt.isEmpty()) {
                log.error("❌ [CHAT: {}] Sem instância ativa", chat.getId());
                return;
            }

            WebInstance webInstance = webInstanceOpt.get();
            state.setInRepescagem(true);

            LocalDateTime now = LocalDateTime.now(ZoneId.of("America/Sao_Paulo"));

            if (state.getScheduledSendTime() != null && now.isBefore(state.getScheduledSendTime())) {
                return;
            }

            if (!isBusinessHours(now)) {
                LocalDateTime scheduled = nextBusinessWindow(now);
                state.setScheduledSendTime(scheduled);
                chatRoutineStateRepository.save(state);
                log.info("⏳ Mensagem reagendada para {}", scheduled);
                return;
            }

            state.setScheduledSendTime(null);

            // ✅ ENVIO COM RATE LIMITING E RETRY
            sendRoutineWithMediaSafe(
                    chat,
                    webInstance,
                    routine,
                    "Rotina #" + routine.getSequenceNumber()
            );

            state.setLastAutomatedMessageSent(LocalDateTime.now(ZoneId.of("America/Sao_Paulo")));
            chatRoutineStateRepository.save(state);

        } catch (Exception e) {
            log.error("❌ [CHAT: {}] Erro ao enviar rotina #{}", chat.getId(), routine.getSequenceNumber(), e);
        }
    }

    // ========== ENVIO COM PROTEÇÃO E RETRY ==========

    /**
     * Versão SEGURA do sendRoutineWithMedia
     * Implementa rate limiting e retry
     */
    private void sendRoutineWithMediaSafe(
            Chat chat,
            WebInstance webInstance,
            RoutineText routine,
            String messagePrefix
    ) {
        // Verifica rate limiting ANTES de tentar enviar
        if (!canSendMessage(webInstance.getId())) {
            log.warn("⏸️ [CHAT: {}] Rate limit atingido, reagendando...", chat.getId());

            // Reagenda para daqui 2 minutos
            scheduleMessageWithDelay(
                    () -> sendRoutineWithMediaSafe(chat, webInstance, routine, messagePrefix),
                    120
            );
            return;
        }

        try {
            sendRoutineWithMediaWithRetry(chat, webInstance, routine, messagePrefix, 0);
        } catch (Exception e) {
            log.error("❌ [CHAT: {}] Falha total ao enviar {}", chat.getId(), messagePrefix, e);
        }
    }

    /**
     * Envio com retry automático e backoff exponencial
     */
    private void sendRoutineWithMediaWithRetry(
            Chat chat,
            WebInstance webInstance,
            RoutineText routine,
            String messagePrefix,
            int attempt
    ) throws InterruptedException {

        if (attempt >= MAX_RETRY_ATTEMPTS) {
            log.error("❌ [CHAT: {}] Máximo de tentativas atingido para {}", chat.getId(), messagePrefix);
            return;
        }

        try {
            String greeting = randomGreeting();
            String fallbackGreeting = randomFallbackGreeting();
            String chatName = chat.getName();
            String receiverName = chatName == null || chatName.isBlank()
                    ? fallbackGreeting
                    : greeting + chat.getName();

            String messageToSend = receiverName + ", " + routine.getTextContent();

            // ===== TEXTO =====
            Map<String, Object> result = zapiMessageService.sendTextMessage(
                    webInstance,
                    chat.getPhone(),
                    messageToSend
            );

            boolean textSent = result != null && Boolean.TRUE.equals(result.get("success"));

            if (textSent) {
                log.info("✅ [CHAT: {}] {} enviada", chat.getId(), messagePrefix);
                recordMessageSent(webInstance.getId());
            } else {
                log.error("❌ [CHAT: {}] Falha ao enviar {}", chat.getId(), messagePrefix);

                // RETRY com backoff exponencial
                int retryDelay = INITIAL_RETRY_DELAY_MS * (int) Math.pow(2, attempt);
                log.info("🔄 Tentativa {} de {} em {}ms", attempt + 1, MAX_RETRY_ATTEMPTS, retryDelay);

                Thread.sleep(retryDelay);
                sendRoutineWithMediaWithRetry(chat, webInstance, routine, messagePrefix, attempt + 1);
                return;
            }

            // Delay antes de enviar mídia
            Thread.sleep(DELAY_AFTER_TEXT * 1000L);

            // ===== FOTOS =====
            List<Photo> photos = getRoutinePhotos(routine);
            if (!photos.isEmpty()) {
                log.info("📷 [CHAT: {}] Enviando {} foto(s)", chat.getId(), photos.size());
                for (Photo photo : photos) {
                    sendPhotoWithRateLimit(chat, webInstance, photo);
                    Thread.sleep(DELAY_BETWEEN_PHOTOS * 1000L);
                }
            }

            // ===== VÍDEOS =====
            List<Video> videos = getRoutineVideos(routine);
            if (!videos.isEmpty()) {
                log.info("🎥 [CHAT: {}] Enviando {} vídeo(s)", chat.getId(), videos.size());
                for (Video video : videos) {
                    sendVideoWithRateLimit(chat, webInstance, video);
                    Thread.sleep(DELAY_BETWEEN_VIDEOS * 1000L);
                }
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw e;
        } catch (Exception e) {
            log.error("❌ [CHAT: {}] Erro na tentativa {} de envio", chat.getId(), attempt + 1, e);

            if (attempt < MAX_RETRY_ATTEMPTS - 1) {
                int retryDelay = INITIAL_RETRY_DELAY_MS * (int) Math.pow(2, attempt);
                Thread.sleep(retryDelay);
                sendRoutineWithMediaWithRetry(chat, webInstance, routine, messagePrefix, attempt + 1);
            }
        }
    }

    /**
     * Envia foto com rate limiting
     */
    private void sendPhotoWithRateLimit(Chat chat, WebInstance webInstance, Photo photo) {
        if (!canSendMessage(webInstance.getId())) {
            log.warn("⏸️ Aguardando rate limit para enviar foto...");
            try {
                Thread.sleep(60000); // Aguarda 1 minuto
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        try {
            PhotoDTO savedPhoto = null;
            try {
                savedPhoto = photoService.saveOutgoingPhoto(
                        chat.getId(),
                        chat.getPhone(),
                        photo.getImageUrl(),
                        webInstance.getId(),
                        null
                );
            } catch (DataIntegrityViolationException e) {
                log.warn("⚠️ Erro de duplicação ao salvar foto");
            }

            Map<String, Object> photoResult = zapiMessageService.sendImage(
                    webInstance,
                    chat.getPhone(),
                    photo.getImageUrl()
            );

            if (photoResult != null && photoResult.containsKey("messageId")) {
                String photoMessageId = (String) photoResult.get("messageId");
                log.info("✅ Foto enviada - MessageId: {}", photoMessageId);
                recordMessageSent(webInstance.getId());

                if (savedPhoto != null) {
                    try {
                        photoService.updatePhotoIdAfterSend(savedPhoto.getMessageId(), photoMessageId, "SENT");
                    } catch (DataIntegrityViolationException e) {
                        log.warn("⚠️ Erro ao atualizar photo messageId");
                    }
                }
            }
        } catch (Exception e) {
            log.error("❌ Erro ao enviar foto: {}", e.getMessage());
        }
    }

    /**
     * Envia vídeo com rate limiting
     */
    private void sendVideoWithRateLimit(Chat chat, WebInstance webInstance, Video video) {
        if (!canSendMessage(webInstance.getId())) {
            log.warn("⏸️ Aguardando rate limit para enviar vídeo...");
            try {
                Thread.sleep(60000); // Aguarda 1 minuto
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        try {
            VideoDTO savedVideo = null;
            try {
                savedVideo = videoService.saveOutgoingVideo(
                        chat.getId(),
                        chat.getPhone(),
                        video.getVideoUrl(),
                        webInstance.getId(),
                        null
                );
            } catch (DataIntegrityViolationException e) {
                log.warn("⚠️ Erro de duplicação ao salvar vídeo");
            }

            Map<String, Object> videoResult = zapiMessageService.sendVideo(
                    webInstance,
                    chat.getPhone(),
                    video.getVideoUrl()
            );

            if (videoResult != null && videoResult.containsKey("messageId")) {
                String videoMessageId = (String) videoResult.get("messageId");
                log.info("✅ Vídeo enviado - MessageId: {}", videoMessageId);
                recordMessageSent(webInstance.getId());

                if (savedVideo != null) {
                    try {
                        videoService.updateVideoIdAfterSend(savedVideo.getMessageId(), videoMessageId, "SENT");
                    } catch (DataIntegrityViolationException e) {
                        log.warn("⚠️ Erro ao atualizar video messageId");
                    }
                }
            }
        } catch (Exception e) {
            log.error("❌ Erro ao enviar vídeo: {}", e.getMessage());
        }
    }

    // ========== MÉTODOS AUXILIARES (mantidos) ==========

    private void moveToLeadFrio(Chat chat, ChatRoutineState state, User user) {
        try {
            chat.setColumn(LEAD_FRIO_COLUMN);
            chatRepository.save(chat);
            log.info("❄️ [CHAT: {}] Movido para Lead Frio", chat.getId());

            state.setInRepescagem(false);
            state.setRepescagemCompleted(true);
            chatRoutineStateRepository.save(state);

            notificationService.sendTaskCompletedNotification(
                    user.getId(),
                    Map.of(
                            "chatId", chat.getId(),
                            "chatName", chat.getName(),
                            "chatColumn", chat.getColumn(),
                            "type", "repescagem-completed"
                    )
            );

            log.info("📡 [CHAT: {}] Notificação SSE enviada", chat.getId());

        } catch (Exception e) {
            log.error("❌ [CHAT: {}] Erro ao mover para Lead Frio", chat.getId(), e);
        }
    }

    @Transactional
    public void removeFromRepescagem(Chat chat, ChatRoutineState state, User user) {
        try {
            String previousColumn = state.getPreviousColumn();

            if (previousColumn == null || previousColumn.isEmpty()) {
                previousColumn = "inbox";
                log.warn("⚠️ [CHAT: {}] previousColumn vazio, usando 'inbox'", chat.getId());
            }

            chat.setColumn(previousColumn);
            chatRepository.save(chat);

            log.info("✅ [CHAT: {}] Removido da Repescagem → {}", chat.getId(), previousColumn);

            state.setInRepescagem(false);
            chatRoutineStateRepository.save(state);

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

            log.info("📡 [CHAT: {}] Notificação SSE enviada", chat.getId());

        } catch (Exception e) {
            log.error("❌ [CHAT: {}] Erro ao remover da repescagem", chat.getId(), e);
        }
    }

    @Transactional
    public void resetChatRoutineState(String chatId) {
        try {
            Optional<Chat> chatOpt = chatRepository.findById(chatId);
            if (chatOpt.isEmpty()) {
                log.warn("⚠️ [CHAT: {}] Chat não encontrado", chatId);
                return;
            }

            Chat chat = chatOpt.get();
            boolean wasInRepescagem = REPESCAGEM_COLUMN.equals(chat.getColumn());

            chatRoutineStateRepository.findByChatId(chatId).ifPresent(state -> {
                state.setLastRoutineSent(0);
                state.setLastAutomatedMessageSent(null);
                state.setInRepescagem(false);
                state.setRepescagemCompleted(false);
                chatRoutineStateRepository.save(state);

                log.info("✅ [CHAT: {}] Estado resetado", chatId);

                if (wasInRepescagem) {
                    removeFromRepescagem(chat, state, chat.getWebInstance().getUser());
                }
            });

        } catch (Exception e) {
            log.error("❌ [CHAT: {}] Erro ao resetar estado", chatId, e);
        }
    }

    private boolean isBusinessHours(LocalDateTime dateTime) {
        ZonedDateTime brtTime = dateTime.atZone(ZoneId.of("America/Sao_Paulo"));
        DayOfWeek dow = brtTime.getDayOfWeek();
        int hour = brtTime.getHour();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY && hour >= 8 && hour < 18;
    }

    private LocalDateTime nextBusinessWindow(LocalDateTime now) {
        ZonedDateTime brt = now.atZone(ZoneId.of("America/Sao_Paulo"));
        if (isBusinessHours(now)) return now;

        ZonedDateTime next = brt.withHour(8).withMinute(0).withSecond(0).plusDays(1);
        while (next.getDayOfWeek() == DayOfWeek.SATURDAY || next.getDayOfWeek() == DayOfWeek.SUNDAY) {
            next = next.plusDays(1);
        }
        return next.withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime();
    }

    private List<Photo> getRoutinePhotos(RoutineText routine) {
        if (routine.getPhotoIds() == null || routine.getPhotoIds().isEmpty()) {
            return new ArrayList<>();
        }
        List<String> photoIds = Arrays.asList(routine.getPhotoIds().split(","));
        return photoRepository.findAllById(photoIds);
    }

    private List<Video> getRoutineVideos(RoutineText routine) {
        if (routine.getVideoIds() == null || routine.getVideoIds().isEmpty()) {
            return new ArrayList<>();
        }
        List<String> videoIds = Arrays.asList(routine.getVideoIds().split(","));
        return videoRepository.findAllById(videoIds);
    }

    private static final List<String> GREETINGS = List.of(
            "Olá ", "Oi ", "Oii ", "Oiii ", "Oie ", "Oiee ", "Oieeê "
    );

    private static final List<String> FALLBACK_GREETINGS = List.of(
            "Oii querid@", "Olá! Espero que esteja bem", "Oiê! Como vai?"
    );

    private static final Random RANDOM = new Random();

    private static String randomGreeting() {
        return GREETINGS.get(RANDOM.nextInt(GREETINGS.size()));
    }

    private static String randomFallbackGreeting() {
        return FALLBACK_GREETINGS.get(RANDOM.nextInt(FALLBACK_GREETINGS.size()));
    }

    // ========== CLASSES AUXILIARES ==========

    /**
     * Rate Limiter simples usando Token Bucket
     */
    private static class RateLimiter {
        private final int maxTokens;
        private int tokens;
        private long lastRefillTime;
        private final long refillIntervalMs = 60000; // 1 minuto

        public RateLimiter(int maxTokens) {
            this.maxTokens = maxTokens;
            this.tokens = maxTokens;
            this.lastRefillTime = System.currentTimeMillis();
        }

        public synchronized boolean tryAcquire() {
            refillTokens();
            if (tokens > 0) {
                tokens--;
                return true;
            }
            return false;
        }

        private void refillTokens() {
            long now = System.currentTimeMillis();
            long timePassed = now - lastRefillTime;

            if (timePassed >= refillIntervalMs) {
                tokens = maxTokens;
                lastRefillTime = now;
            }
        }
    }

    /**
     * Contador de mensagens por hora
     */
    private static class HourlyMessageCounter {
        private final int maxMessagesPerHour;
        private final Queue<Long> timestamps = new ConcurrentLinkedQueue<>();

        public HourlyMessageCounter(int maxMessagesPerHour) {
            this.maxMessagesPerHour = maxMessagesPerHour;
        }

        public synchronized boolean canSend() {
            cleanOldTimestamps();
            return timestamps.size() < maxMessagesPerHour;
        }

        public synchronized void recordMessage() {
            timestamps.offer(System.currentTimeMillis());
        }

        private void cleanOldTimestamps() {
            long oneHourAgo = System.currentTimeMillis() - 3600000;
            timestamps.removeIf(ts -> ts < oneHourAgo);
        }
    }
}