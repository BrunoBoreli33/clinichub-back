package com.example.loginauthapi.repositories;

import com.example.loginauthapi.entities.ChatRoutineState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRoutineStateRepository extends JpaRepository<ChatRoutineState, String> {

    Optional<ChatRoutineState> findByChatId(String chatId);

    List<ChatRoutineState> findByUserId(String userId);

    List<ChatRoutineState> findByUserIdAndInRepescagem(String userId, Boolean inRepescagem);

    void deleteByChatId(String chatId);
}