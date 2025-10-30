package com.example.loginauthapi.controllers;

import com.example.loginauthapi.dto.*;
import com.example.loginauthapi.entities.User;
import com.example.loginauthapi.infra.security.TokenService;
import com.example.loginauthapi.repositories.UserRepository;
import com.example.loginauthapi.services.EmailService;
import com.example.loginauthapi.services.PasswordResetService;
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

import java.util.Map;
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
    private final PasswordResetService passwordResetService;

    @PostMapping("/login")
    @Transactional
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

        // sucesso → gera novo token
        String token = tokenService.generateToken(user);

        // Atualiza o token no banco de dados
        user.setConfirmationToken(token);
        repository.save(user);

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

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody RefreshTokenDTO body) {
        try {
            // Valida o token antigo
            String email = tokenService.validateToken(body.token());

            if (email == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of(
                                "success", false,
                                "message", "Token inválido ou expirado"
                        ));
            }

            // Busca o usuário no banco
            Optional<User> userOptional = repository.findByEmail(email);

            if (userOptional.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of(
                                "success", false,
                                "message", "Usuário não encontrado"
                        ));
            }

            User user = userOptional.get();

            // Gera novo token
            String newToken = tokenService.generateToken(user);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "token", newToken,
                    "userId", user.getId(),
                    "userName", user.getName(),
                    "message", "Token renovado com sucesso"
            ));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "message", "Erro ao renovar token: " + e.getMessage()
                    ));
        }
    }

    /**
     * Endpoint para solicitar recuperação de senha
     * Envia código de 6 dígitos para o email do usuário
     */
    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody @Valid ForgotPasswordRequestDTO body) {
        Optional<User> optionalUser = repository.findByEmail(body.email());

        if (optionalUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "success", false,
                            "message", "E-mail não cadastrado"
                    ));
        }

        String code = passwordResetService.createPasswordReset(body.email());

        try {
            emailService.sendPasswordResetCode(body.email(), code);
        } catch (Exception e) {
            passwordResetService.removePasswordReset(body.email());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of(
                            "success", false,
                            "message", "Erro ao enviar e-mail. Tente novamente."
                    ));
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Código de recuperação enviado para o seu e-mail",
                "email", body.email()
        ));
    }

    /**
     * Endpoint para verificar o código de recuperação
     * Máximo de 3 tentativas
     */
    @PostMapping("/verify-reset-code")
    public ResponseEntity<?> verifyResetCode(@RequestBody @Valid VerifyResetCodeDTO body) {
        PasswordResetService.PasswordReset reset = passwordResetService.getPasswordReset(body.email());

        if (reset == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "success", false,
                            "message", "Nenhuma solicitação encontrada ou código expirado"
                    ));
        }

        if (!passwordResetService.verifyCode(body.email(), body.code())) {
            int remainingAttempts = 3 - reset.getAttempts();

            if (remainingAttempts <= 0) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of(
                                "success", false,
                                "message", "Limite de tentativas excedido. Solicite um novo código."
                        ));
            }

            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "success", false,
                            "message", "Código inválido. Você tem " + remainingAttempts + " tentativa(s) restante(s)."
                    ));
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Código verificado com sucesso"
        ));
    }

    /**
     * Endpoint para resetar a senha
     * Só funciona se o código foi verificado anteriormente
     */
    @PostMapping("/reset-password")
    @Transactional
    public ResponseEntity<?> resetPassword(@RequestBody @Valid ResetPasswordDTO body) {
        if (!passwordResetService.isVerified(body.email())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                            "success", false,
                            "message", "Código não verificado ou expirado. Solicite um novo código."
                    ));
        }

        Optional<User> optionalUser = repository.findByEmail(body.email());

        if (optionalUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                            "success", false,
                            "message", "Usuário não encontrado"
                    ));
        }

        User user = optionalUser.get();
        user.setPassword(passwordEncoder.encode(body.newPassword()));
        repository.save(user);

        passwordResetService.removePasswordReset(body.email());

        try {
            emailService.sendPasswordChangedConfirmation(body.email());
        } catch (Exception e) {
            // Log do erro mas não falha a requisição
            System.err.println("Erro ao enviar email de confirmação: " + e.getMessage());
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Senha alterada com sucesso"
        ));
    }
}