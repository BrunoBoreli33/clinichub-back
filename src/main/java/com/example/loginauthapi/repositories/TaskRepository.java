package com.example.loginauthapi.repositories;

import com.example.loginauthapi.entities.Task;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TaskRepository extends JpaRepository<Task, String> {

    // ✅ ALTERADO: Buscar todas as tarefas de um chat ordenadas por data de criação (mais recente primeiro)
    List<Task> findByChatIdOrderByCreatedAtDesc(String chatId);

    // ✅ NOVO: Buscar tarefas de um chat filtradas por status de execução
    List<Task> findByChatIdAndExecuted(String chatId, Boolean executed);

    // Buscar tarefas pendentes que devem ser executadas
    @Query("SELECT t FROM Task t WHERE t.executed = false AND t.scheduledDate <= :now")
    List<Task> findPendingTasksToExecute(@Param("now") LocalDateTime now);

    // Buscar todas as tarefas de um usuário através do relacionamento com Chat
    @Query("SELECT t FROM Task t WHERE t.chat.webInstance.user.id = :userId")
    List<Task> findByUserId(@Param("userId") String userId);

    // Deletar tarefas por chatId
    void deleteByChatId(String chatId);
}