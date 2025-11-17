package com.example.loginauthapi.repositories;

import com.example.loginauthapi.entities.Campaign;
import com.example.loginauthapi.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, String> {

    List<Campaign> findByUserOrderByAtualizadoEmDesc(User user);

    @Query("SELECT c FROM Campaign c WHERE c.status = 'EM_ANDAMENTO' AND c.nextDispatchTime <= :now")
    List<Campaign> findCampaignsReadyForDispatch(LocalDateTime now);

    List<Campaign> findByUserAndStatusIn(User user, List<String> statuses);
}