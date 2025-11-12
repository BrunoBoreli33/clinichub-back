package com.example.loginauthapi.repositories;

import com.example.loginauthapi.entities.Reply;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReplyRepository extends JpaRepository<Reply, String> {

    Optional<Reply> findByMessageId(String messageId);

    List<Reply> findByChatIdOrderByTimestampAsc(String chatId);

    @Query("SELECT r FROM Reply r WHERE r.message.messageId = :messageId")
    Optional<Reply> findByMessageMessageId(String messageId);

    @Query("SELECT r FROM Reply r WHERE r.referenceMessageId = :referenceMessageId")
    List<Reply> findByReferenceMessageId(String referenceMessageId);
}