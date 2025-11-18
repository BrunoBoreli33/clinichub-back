package com.example.loginauthapi.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "chat_routine_states")
public class ChatRoutineState {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne
    @JoinColumn(name = "chat_id", nullable = false)
    private Chat chat;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "scheduled_send_time")
    private LocalDateTime scheduledSendTime;

    // Coluna anterior antes de ir para Repescagem
    @Column(nullable = false)
    private String previousColumn;

    // Última sequência de rotina enviada (1-7)
    @Column(nullable = false)
    private Integer lastRoutineSent = 0;

    // Timestamp da última mensagem automática enviada
    @Column
    private LocalDateTime lastAutomatedMessageSent;

    // Timestamp da última mensagem fromMe=true antes de entrar na repescagem
    @Column
    private LocalDateTime lastUserMessageTime;

    // Flag indicando se está em processo de repescagem
    @Column(nullable = false)
    private Boolean inRepescagem = false;

    //NOVO: Flag indicando se a repescagem foi concluida (movido para Lead Frio)
    @Column(nullable = false)
    private Boolean repescagemCompleted = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}