package com.example.loginauthapi.repositories;

import com.example.loginauthapi.entities.PreConfiguredText;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PreConfiguredTextRepository extends JpaRepository<PreConfiguredText, String> {

    /**
     * Buscar todos os textos pré-configurados de um usuário
     */
    List<PreConfiguredText> findByUserIdOrderByCreatedAtDesc(String userId);
}