package com.example.loginauthapi.controllers;

import com.example.loginauthapi.dto.DashboardResponseDTO;
import com.example.loginauthapi.entities.User;
import com.example.loginauthapi.repositories.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final UserRepository userRepository;
    // Injete aqui os repositories de outras tabelas que você precisa buscar
    // private final MessageRepository messageRepository;
    // private final ContactRepository contactRepository;
    // private final ConversationRepository conversationRepository;

    @GetMapping
    public ResponseEntity<?> getDashboardData(@RequestParam String userId) {

        // 1. Obter o usuário autenticado do contexto de segurança
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Token inválido ou ausente");
        }

        // 2. O SecurityFilter já validou o token e colocou o User no contexto
        User authenticatedUser = (User) authentication.getPrincipal();

        // 3. Verificar se o userId do parâmetro corresponde ao usuário autenticado
        if (!authenticatedUser.getId().equals(userId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Você não tem permissão para acessar os dados deste usuário");
        }

        // 4. Buscar o usuário no banco (opcional, já temos do contexto)
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado"));

        // 5. Criar o DTO de resposta com informações do usuário
        DashboardResponseDTO.UserInfoDTO userInfo = new DashboardResponseDTO.UserInfoDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.isConfirmed()
        );

        // 6. Buscar dados de outras tabelas relacionadas ao usuário
        // Exemplo:
        // List<Message> messages = messageRepository.findByUserId(userId);
        // List<DashboardResponseDTO.MessageDTO> messageDTOs = messages.stream()
        //     .map(msg -> new DashboardResponseDTO.MessageDTO(
        //         msg.getId(),
        //         msg.getContent(),
        //         msg.getSender(),
        //         msg.getTimestamp()
        //     ))
        //     .collect(Collectors.toList());

        // 7. Montar a resposta completa
        DashboardResponseDTO response = new DashboardResponseDTO();
        response.setUser(userInfo);
        // response.setMessages(messageDTOs);
        // response.setContacts(contactDTOs);
        // response.setConversations(conversationDTOs);

        // 8. Retornar a resposta
        return ResponseEntity.ok(response);
    }
}