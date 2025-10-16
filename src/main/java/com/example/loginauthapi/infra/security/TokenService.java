package com.example.loginauthapi.infra.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.example.loginauthapi.entities.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class TokenService {
    @Value("${api.security.token.secret}")
    private String secret;

    // ✅ MODIFICADO: Aumentado de 2 para 4 horas
    private static final int TOKEN_EXPIRATION_HOURS = 4;

    public String generateToken(User user){
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.create()
                    .withIssuer("login-auth-api")
                    .withSubject(user.getEmail())
                    .withExpiresAt(this.generateExpirationDate())
                    .sign(algorithm);
        } catch (JWTCreationException exception){
            throw new RuntimeException("Error while authenticating");
        }
    }

    public String validateToken(String token){
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.require(algorithm)
                    .withIssuer("login-auth-api")
                    .build()
                    .verify(token)
                    .getSubject();
        } catch (JWTVerificationException exception) {
            return null;
        }
    }

    // ✅ NOVO: Método para verificar se o token está próximo de expirar
    public boolean isTokenNearExpiration(String token) {
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            DecodedJWT jwt = JWT.require(algorithm)
                    .withIssuer("login-auth-api")
                    .build()
                    .verify(token);

            Instant expiresAt = jwt.getExpiresAt().toInstant();
            Instant now = Instant.now();

            // Considera "próximo de expirar" se faltarem menos de 30 minutos
            long minutesUntilExpiration = (expiresAt.toEpochMilli() - now.toEpochMilli()) / (1000 * 60);

            return minutesUntilExpiration < 30;
        } catch (JWTVerificationException exception) {
            return true; // Se não conseguir verificar, considera que precisa renovar
        }
    }

    // ✅ NOVO: Método para renovar token
    public String refreshToken(String oldToken) {
        String email = validateToken(oldToken);
        if (email == null) {
            throw new RuntimeException("Token inválido para renovação");
        }

        // O usuário será buscado no controller e passado para generateToken
        return null; // Placeholder - o controller preencherá isso
    }

    private Instant generateExpirationDate(){
        return LocalDateTime.now()
                .plusHours(TOKEN_EXPIRATION_HOURS)
                .toInstant(ZoneOffset.of("-03:00"));
    }
}