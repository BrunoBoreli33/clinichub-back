package com.example.loginauthapi.repositories;

import com.example.loginauthapi.entities.WebInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface WebInstanceRepository extends JpaRepository<WebInstance, String> {

    /**
     * Buscar todas as instâncias de um usuário pelo ID do user
     * IMPORTANTE: O Spring Data JPA cria automaticamente este método
     * baseado no relacionamento @ManyToOne com User
     */
    List<WebInstance> findByUserId(String userId);

    /**
     * Buscar uma instância pelo clientToken
     */
    Optional<WebInstance> findByClientToken(String clientToken);

    /**
     * Buscar uma instância pelo seuToken
     */
    Optional<WebInstance> findBySeuToken(String seuToken);

    /**
     * Buscar uma instância pela suaInstancia
     */
    Optional<WebInstance> findBySuaInstancia(String suaInstancia);

    // NOVOS MÉTODOS para o controller de dev
    List<WebInstance> findByStatus(String status);

    List<WebInstance> findByUserIdAndStatus(String userId, String status);

}