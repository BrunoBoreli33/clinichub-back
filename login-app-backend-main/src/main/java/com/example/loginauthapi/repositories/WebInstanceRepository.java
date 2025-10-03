package com.example.loginauthapi.repositories;

import com.example.loginauthapi.entities.WebInstance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WebInstanceRepository extends JpaRepository<WebInstance, String> {

    // Buscar todas as instâncias de um usuário pelo ID do user
    List<WebInstance> findByUserId(String userId);

    // Buscar uma instância pelo clientToken
    Optional<WebInstance> findByClientToken(String clientToken);

    // Buscar uma instância pelo seuToken
    Optional<WebInstance> findBySeuToken(String seuToken);

    // Se quiser, pode buscar também pela suaInstancia
    Optional<WebInstance> findBySuaInstancia(String suaInstancia);
}