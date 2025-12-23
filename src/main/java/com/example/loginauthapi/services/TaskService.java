package com.example.loginauthapi.services;

import com.example.loginauthapi.dto.TaskDTO;
import com.example.loginauthapi.dto.TaskRequestDTO;
import com.example.loginauthapi.entities.Chat;
import com.example.loginauthapi.entities.Task;
import com.example.loginauthapi.entities.User;
import com.example.loginauthapi.entities.WebInstance;
import com.example.loginauthapi.repositories.ChatRepository;
import com.example.loginauthapi.repositories.TaskRepository;
import com.example.loginauthapi.repositories.WebInstanceRepository;
import com.example.loginauthapi.services.zapi.ZapiMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TaskService {

    private final TaskRepository taskRepository;
    private final ChatRepository chatRepository;
    private final WebInstanceRepository webInstanceRepository;
    private final ZapiMessageService zapiMessageService;
    private final NotificationService notificationService;

    /**
     * Criar nova tarefa
     */
    @Transactional
    public TaskDTO createTask(TaskRequestDTO request, User user) {
        log.info("🆕 Criando tarefa para chat {}", request.getChatId());

        // Buscar chat
        Chat chat = chatRepository.findById(request.getChatId())
                .orElseThrow(() -> new RuntimeException("Chat não encontrado"));

        // Verificar se chat pertence ao usuário
        if (!chat.getWebInstance().getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Chat não pertence ao usuário");
        }

        // ✅ CORREÇÃO: Removida verificação que impedia múltiplas tarefas
        // Agora permite criar quantas tarefas forem necessárias para o mesmo chat

        // ✅ Salvar a coluna atual ANTES de mover para task (apenas na primeira vez)
        if (!"task".equals(chat.getColumn())) {
            String currentColumn = chat.getColumn();
            chat.setPreviousColumn(currentColumn);

            // Mover chat para coluna "task" (tarefa)
            chat.setColumn("task");
            chatRepository.save(chat);

            log.info("📌 Chat movido de '{}' para 'task' | previousColumn='{}'", currentColumn, chat.getPreviousColumn());
        }

        // Criar tarefa
        Task task = new Task();
        task.setChat(chat);
        task.setMessage(request.getMessage());
        task.setScheduledDate(request.getScheduledDate());
        task.setExecuted(false);

        Task savedTask = taskRepository.save(task);

        log.info("✅ Tarefa criada: {}", savedTask.getId());

        return convertToDTO(savedTask);
    }

    /**
     * Buscar tarefa por ID
     */
    public TaskDTO getTaskById(String taskId, User user) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Tarefa não encontrada"));

        // Verificar se tarefa pertence ao usuário
        if (!task.getChat().getWebInstance().getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Tarefa não pertence ao usuário");
        }

        return convertToDTO(task);
    }

    /**
     * Buscar tarefa mais recente pendente por chatId
     */
    public TaskDTO getTaskByChatId(String chatId, User user) {
        List<Task> tasks = taskRepository.findByChatIdOrderByCreatedAtDesc(chatId);

        if (tasks.isEmpty()) {
            throw new RuntimeException("Nenhuma tarefa encontrada para este chat");
        }

        // Retornar a tarefa mais recente
        Task task = tasks.get(0);

        // Verificar se tarefa pertence ao usuário
        if (!task.getChat().getWebInstance().getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Tarefa não pertence ao usuário");
        }

        return convertToDTO(task);
    }

    /**
     * Listar todas as tarefas de um usuário
     */
    public List<TaskDTO> getAllTasksByUser(User user) {
        List<Task> tasks = taskRepository.findByUserId(user.getId());
        return tasks.stream()
                .map(this::convertToDTO)
                .toList();
    }

    /**
     * Listar todas as tarefas de um chat específico
     */
    public List<TaskDTO> getAllTasksByChat(String chatId, User user) {
        Chat chat = chatRepository.findById(chatId)
                .orElseThrow(() -> new RuntimeException("Chat não encontrado"));

        // Verificar se chat pertence ao usuário
        if (!chat.getWebInstance().getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Chat não pertence ao usuário");
        }

        List<Task> tasks = taskRepository.findByChatIdOrderByCreatedAtDesc(chatId);
        return tasks.stream()
                .map(this::convertToDTO)
                .toList();
    }

    /**
     * Atualizar tarefa
     */
    @Transactional
    public TaskDTO updateTask(String taskId, TaskRequestDTO request, User user) {
        log.info("📝 Atualizando tarefa {}", taskId);

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Tarefa não encontrada"));

        // Verificar se tarefa pertence ao usuário
        if (!task.getChat().getWebInstance().getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Tarefa não pertence ao usuário");
        }

        // Não permitir atualizar tarefa já executada
        if (task.getExecuted()) {
            throw new RuntimeException("Não é possível atualizar tarefa já executada");
        }

        task.setMessage(request.getMessage());
        task.setScheduledDate(request.getScheduledDate());

        Task updatedTask = taskRepository.save(task);

        log.info("✅ Tarefa atualizada: {}", taskId);

        return convertToDTO(updatedTask);
    }

    /**
     * Excluir tarefa
     */
    @Transactional
    public void deleteTask(String taskId, User user) {
        log.info("🗑️ Excluindo tarefa {}", taskId);

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Tarefa não encontrada"));

        // Verificar se tarefa pertence ao usuário
        if (!task.getChat().getWebInstance().getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Tarefa não pertence ao usuário");
        }

        Chat chat = task.getChat();

        // ✅ Verificar se ainda há tarefas pendentes para este chat
        List<Task> remainingTasks = taskRepository.findByChatIdAndExecuted(chat.getId(), false);
        remainingTasks.remove(task); // Remover a tarefa atual da lista

        // Se não houver mais tarefas pendentes, mover chat de volta
        if (remainingTasks.isEmpty()) {
            String targetColumn = chat.getPreviousColumn() != null ? chat.getPreviousColumn() : "inbox";
            chat.setColumn(targetColumn);
            chat.setPreviousColumn(null);
            chatRepository.save(chat);
            log.info("✅ Chat {} voltou para '{}' (nenhuma tarefa pendente)", chat.getId(), targetColumn);
        }

        taskRepository.delete(task);

        log.info("✅ Tarefa excluída: {}", taskId);
    }

    /**
     * Marcar tarefa como concluída manualmente
     */
    @Transactional
    public TaskDTO completeTask(String taskId, User user) {
        log.info("✔️ Marcando tarefa {} como concluída", taskId);

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Tarefa não encontrada"));

        // Verificar se tarefa pertence ao usuário
        if (!task.getChat().getWebInstance().getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Tarefa não pertence ao usuário");
        }

        task.setExecuted(true);
        task.setExecutedAt(LocalDateTime.now());

        Chat chat = task.getChat();

        // ✅ Verificar se ainda há tarefas pendentes para este chat
        List<Task> remainingTasks = taskRepository.findByChatIdAndExecuted(chat.getId(), false);

        // Se não houver mais tarefas pendentes, mover chat de volta
        if (remainingTasks.isEmpty()) {
            String targetColumn = chat.getPreviousColumn() != null ? chat.getPreviousColumn() : "inbox";
            chat.setColumn(targetColumn);
            chat.setPreviousColumn(null);
            chatRepository.save(chat);
            log.info("✅ Chat {} voltou para '{}' (nenhuma tarefa pendente)", chat.getId(), targetColumn);

            // Enviar notificação SSE para atualizar frontend
            notificationService.sendTaskCompletedNotification(
                    user.getId(),
                    Map.of(
                            "taskId", taskId,
                            "chatId", chat.getId(),
                            "chatName", chat.getName(),
                            "chatColumn", chat.getColumn()
                    )
            );
        }

        Task updatedTask = taskRepository.save(task);

        log.info("✅ Tarefa concluída: {}", taskId);

        return convertToDTO(updatedTask);
    }

    /**
     * Scheduler: Verificar e executar tarefas pendentes a cada 40 segundos
     */
    @Scheduled(fixedDelay = 40000) // 40 segundos
    @Transactional
    public void checkAndExecutePendingTasks() {
        log.debug("🔍 Verificando tarefas pendentes...");

        LocalDateTime now = LocalDateTime.now();
        List<Task> pendingTasks = taskRepository.findPendingTasksToExecute(now);

        if (pendingTasks.isEmpty()) {
            log.debug("✅ Nenhuma tarefa pendente para executar");
            return;
        }

        log.info("📋 Encontradas {} tarefa(s) pendente(s) para executar", pendingTasks.size());

        for (Task task : pendingTasks) {
            executeTask(task);
        }
    }

    /**
     * Executar tarefa: enviar mensagem e mover chat de volta
     */
    private void executeTask(Task task) {
        try {
            Chat chat = task.getChat();
            User user = chat.getWebInstance().getUser();

            log.info("📤 Executando tarefa {} para chat {}", task.getId(), chat.getId());

            // Buscar instância ativa do usuário
            Optional<WebInstance> instanceOpt = webInstanceRepository.findByUserId(user.getId())
                    .stream()
                    .filter(wi -> "ACTIVE".equals(wi.getStatus()))
                    .findFirst();

            if (instanceOpt.isEmpty()) {
                log.error("❌ Usuário {} não possui instância ativa", user.getId());
                return;
            }

            WebInstance instance = instanceOpt.get();

            // Enviar mensagem via Z-API
            Map<String, Object> result = zapiMessageService.sendTextMessageWithRetry(
                    instance,
                    chat.getPhone(),
                    task.getMessage(),
                    false
            );

            if (result != null && result.get("messageId") != null) {
                log.info("✅ Mensagem da tarefa enviada com sucesso: {}", result.get("messageId"));

                // Marcar tarefa como executada
                task.setExecuted(true);
                task.setExecutedAt(LocalDateTime.now());
                taskRepository.save(task);

                // ✅ Verificar se ainda há tarefas pendentes para este chat
                List<Task> remainingTasks = taskRepository.findByChatIdAndExecuted(chat.getId(), false);

                // Se não houver mais tarefas pendentes, mover chat de volta
                if (remainingTasks.isEmpty()) {
                    String targetColumn = chat.getPreviousColumn() != null ? chat.getPreviousColumn() : "inbox";
                    chat.setColumn(targetColumn);
                    chat.setPreviousColumn(null);
                    chatRepository.save(chat);

                    log.info("✅ Tarefa {} executada e chat {} voltou para '{}'",
                            task.getId(), chat.getId(), targetColumn);

                    // Enviar notificação SSE para atualizar frontend
                    notificationService.sendTaskCompletedNotification(
                            user.getId(),
                            Map.of(
                                    "taskId", task.getId(),
                                    "chatId", chat.getId(),
                                    "chatName", chat.getName(),
                                    "chatColumn", chat.getColumn()
                            )
                    );
                } else {
                    log.info("✅ Tarefa {} executada. Chat {} permanece em 'task' ({} tarefa(s) pendente(s))",
                            task.getId(), chat.getId(), remainingTasks.size());
                }
            } else {
                log.error("❌ Falha ao enviar mensagem da tarefa {}", task.getId());
            }

        } catch (Exception e) {
            log.error("❌ Erro ao executar tarefa {}: {}", task.getId(), e.getMessage(), e);
        }
    }

    /**
     * Converter Task para TaskDTO
     */
    private TaskDTO convertToDTO(Task task) {
        TaskDTO dto = new TaskDTO();
        dto.setId(task.getId());
        dto.setChatId(task.getChat().getId());
        dto.setChatName(task.getChat().getName());
        dto.setMessage(task.getMessage());
        dto.setScheduledDate(task.getScheduledDate());
        dto.setExecuted(task.getExecuted());
        dto.setExecutedAt(task.getExecutedAt());
        dto.setCreatedAt(task.getCreatedAt());
        dto.setUpdatedAt(task.getUpdatedAt());
        return dto;
    }
}