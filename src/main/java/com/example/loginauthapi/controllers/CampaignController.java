package com.example.loginauthapi.controllers;

import com.example.loginauthapi.dto.CampaignDTO;
import com.example.loginauthapi.dto.CampaignRequestDTO;
import com.example.loginauthapi.entities.User;
import com.example.loginauthapi.services.CampaignService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/dashboard/campaigns")
@RequiredArgsConstructor
@Slf4j
public class CampaignController {

    private final CampaignService campaignService;

    private User getAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || !(auth.getPrincipal() instanceof User)) {
            throw new RuntimeException("Usuário não autenticado");
        }
        return (User) auth.getPrincipal();
    }

    /**
     * GET /dashboard/campaigns - Listar todas as campanhas do usuário
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> getAllCampaigns() {
        try {
            User user = getAuthenticatedUser();
            List<CampaignDTO> campaigns = campaignService.getAllCampaigns(user);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "campaigns", campaigns
            ));
        } catch (Exception e) {
            log.error("❌ Erro ao buscar campanhas", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Erro ao buscar campanhas: " + e.getMessage()
            ));
        }
    }

    /**
     * GET /dashboard/campaigns/{id} - Buscar campanha específica
     */
    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getCampaign(@PathVariable String id) {
        try {
            User user = getAuthenticatedUser();
            CampaignDTO campaign = campaignService.getCampaignById(id, user);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "campaign", campaign
            ));
        } catch (RuntimeException e) {
            log.error("❌ Erro ao buscar campanha: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("❌ Erro ao buscar campanha", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Erro ao buscar campanha: " + e.getMessage()
            ));
        }
    }

    /**
     * POST /dashboard/campaigns - Criar nova campanha
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createCampaign(@Valid @RequestBody CampaignRequestDTO request) {
        try {
            User user = getAuthenticatedUser();
            CampaignDTO campaign = campaignService.createCampaign(request, user);

            return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                    "success", true,
                    "message", "Campanha criada com sucesso",
                    "campaign", campaign
            ));
        } catch (Exception e) {
            log.error("❌ Erro ao criar campanha", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Erro ao criar campanha: " + e.getMessage()
            ));
        }
    }

    /**
     * PUT /dashboard/campaigns/{id} - Atualizar campanha
     */
    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateCampaign(
            @PathVariable String id,
            @Valid @RequestBody CampaignRequestDTO request) {
        try {
            User user = getAuthenticatedUser();
            CampaignDTO campaign = campaignService.updateCampaign(id, request, user);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Campanha atualizada com sucesso",
                    "campaign", campaign
            ));
        } catch (RuntimeException e) {
            log.error("❌ Erro ao atualizar campanha: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("❌ Erro ao atualizar campanha", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Erro ao atualizar campanha: " + e.getMessage()
            ));
        }
    }

    /**
     * DELETE /dashboard/campaigns/{id} - Deletar campanha
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> deleteCampaign(@PathVariable String id) {
        try {
            User user = getAuthenticatedUser();
            campaignService.deleteCampaign(id, user);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Campanha deletada com sucesso"
            ));
        } catch (RuntimeException e) {
            log.error("❌ Erro ao deletar campanha: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("❌ Erro ao deletar campanha", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Erro ao deletar campanha: " + e.getMessage()
            ));
        }
    }

    /**
     * POST /dashboard/campaigns/{id}/start - Iniciar campanha
     */
    @PostMapping("/{id}/start")
    public ResponseEntity<Map<String, Object>> startCampaign(@PathVariable String id) {
        try {
            User user = getAuthenticatedUser();
            CampaignDTO campaign = campaignService.startCampaign(id, user);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Campanha iniciada com sucesso",
                    "campaign", campaign
            ));
        } catch (RuntimeException e) {
            log.error("❌ Erro ao iniciar campanha: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("❌ Erro ao iniciar campanha", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Erro ao iniciar campanha: " + e.getMessage()
            ));
        }
    }

    /**
     * POST /dashboard/campaigns/{id}/pause - Pausar campanha
     */
    @PostMapping("/{id}/pause")
    public ResponseEntity<Map<String, Object>> pauseCampaign(@PathVariable String id) {
        try {
            User user = getAuthenticatedUser();
            CampaignDTO campaign = campaignService.pauseCampaign(id, user);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Campanha pausada com sucesso",
                    "campaign", campaign
            ));
        } catch (RuntimeException e) {
            log.error("❌ Erro ao pausar campanha: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("❌ Erro ao pausar campanha", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Erro ao pausar campanha: " + e.getMessage()
            ));
        }
    }

    /**
     * POST /dashboard/campaigns/{id}/cancel - Cancelar campanha
     */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Map<String, Object>> cancelCampaign(@PathVariable String id) {
        try {
            User user = getAuthenticatedUser();
            CampaignDTO campaign = campaignService.cancelCampaign(id, user);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Campanha cancelada com sucesso",
                    "campaign", campaign
            ));
        } catch (RuntimeException e) {
            log.error("❌ Erro ao cancelar campanha: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("❌ Erro ao cancelar campanha", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "success", false,
                    "message", "Erro ao cancelar campanha: " + e.getMessage()
            ));
        }
    }
}