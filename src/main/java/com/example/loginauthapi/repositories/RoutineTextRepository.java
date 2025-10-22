package com.example.loginauthapi.repositories;

import com.example.loginauthapi.entities.RoutineText;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RoutineTextRepository extends JpaRepository<RoutineText, String> {

    List<RoutineText> findByUserIdOrderBySequenceNumberAsc(String userId);

    Optional<RoutineText> findByUserIdAndSequenceNumber(String userId, Integer sequenceNumber);

    void deleteByUserIdAndSequenceNumber(String userId, Integer sequenceNumber);
}