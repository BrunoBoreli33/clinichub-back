package com.example.loginauthapi.controllers;

import com.example.loginauthapi.dto.*;
import com.example.loginauthapi.entities.User;
import com.example.loginauthapi.infra.security.TokenService;
import com.example.loginauthapi.repositories.UserRepository;
import com.example.loginauthapi.services.EmailService;
import com.example.loginauthapi.services.TempUserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {
    private final UserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final TempUserService tempUserService;
    private final EmailService emailService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequestDTO body) {
        // tenta encontrar o usuário
        Optional<User> optionalUser = repository.findByEmail(body.email());

        if (optionalUser.isEmpty()) {
            // usuário não existe → retorna 404
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Usuário não cadastrado");
        }

        User user = optionalUser.get();

        // senha incorreta → retorna 401
        if (!passwordEncoder.matches(body.password(), user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Senha incorreta");
        }

        // sucesso → retorna token e nome
        String token = tokenService.generateToken(user);
        return ResponseEntity.ok(new TokenResponseDTO(user.getId(), user.getName(), token));
    }


    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody @Valid RegisterRequestDTO body) {
        if (repository.findByEmail(body.email()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body("Usuário já existe");
        }

        String code = tempUserService.createTempUser(
                body.name(),
                body.email(),
                passwordEncoder.encode(body.password())
        );

        try {
            emailService.sendVerificationEmail(body.email(), code);
        } catch (Exception e) {
            tempUserService.removeTempUser(body.email());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erro ao enviar e-mail. Tente novamente.");
        }

        EmailSendDTO.SendEmailDTO response = new EmailSendDTO.SendEmailDTO(
                "Código de confirmação enviado para o seu e-mail",
                body.email()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/confirm")
    @Transactional
    public ResponseEntity<?> confirm(@RequestBody @Valid ConfirmEmailCodeDTO body){
        TempUserService.TempUser tempUser = tempUserService.getTempUser(body.email());

        if(tempUser == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Nenhum cadastro encontrado ou código expirado");
        }

        if(!tempUserService.verifyCode(body.email(), body.code())){
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Código inválido ou limite de tentativas excedido");
        }

        tempUser = tempUserService.getTempUser(body.email());
        if(tempUser == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Código expirado");
        }

        User newUser = new User();
        newUser.setName(tempUser.getName());
        newUser.setEmail(tempUser.getEmail());
        newUser.setPassword(tempUser.getPassword());

        // Define os valores que você quer garantir
        newUser.setConfirmed(true);
        newUser.setRole("ADMIN");

        // Gera o token
        String token = tokenService.generateToken(newUser);

        // Atribui o token ao campo confirmationToken
        newUser.setConfirmationToken(token);

        // Salva no banco
        repository.save(newUser);

        // Remove o tempUser
        tempUserService.removeTempUser(body.email());

        // Retorna a resposta com o token
        return ResponseEntity.ok(new TokenResponseDTO(newUser.getId(), newUser.getName(), token));

    }
}