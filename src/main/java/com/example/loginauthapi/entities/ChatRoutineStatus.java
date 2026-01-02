package com.example.loginauthapi.entities;

public enum ChatRoutineStatus {
    NONE,       // Estado inicial, nao entrou/esta em repescagem
    PENDING,    // Aguardando o horário de envio
    PROCESSING, // Sendo enviado agora (bloqueado para outros processos)
    SENT,       // Mensagem enviada com sucesso
    ERROR       // Falha no envio (API fora, número desconectado, etc)
}
