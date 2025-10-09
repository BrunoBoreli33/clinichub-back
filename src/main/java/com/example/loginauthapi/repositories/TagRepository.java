package com.example.loginauthapi.repositories;

import com.example.loginauthapi.entities.Tag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TagRepository extends JpaRepository<Tag, String> {

    /**
     * Buscar todas as tags de um usuário
     */
    List<Tag> findByUserId(String userId);

    /**
     * Buscar tag por nome e usuário (para validação de duplicatas)
     */
    Optional<Tag> findByNameAndUserId(String name, String userId);

    /**
     * Verificar se uma tag com determinado nome já existe para o usuário
     */
    boolean existsByNameAndUserId(String name, String userId);

    /**
     * Deletar todas as tags de um usuário
     */
    void deleteByUserId(String userId);
}