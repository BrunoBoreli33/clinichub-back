package com.example.loginauthapi.infra.security;

import com.example.loginauthapi.entities.User;
import com.example.loginauthapi.repositories.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
@Slf4j
public class SecurityFilter extends OncePerRequestFilter {

    @Autowired
    TokenService tokenService;

    @Autowired
    UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String path = request.getServletPath();
        log.info("Method: {}", request.getMethod());
        log.info("URI: {}", request.getRequestURI());
        log.info("Path: {}", request.getServletPath());

        request.getHeaderNames().asIterator()
                .forEachRemaining(name -> log.info("Header: {} = {}", name, request.getHeader(name)));

        if (request.getCookies() != null) {
            for (var cookie : request.getCookies()) {
                log.info("Cookie: {} = {}", cookie.getName(), cookie.getValue());
            }
        }

        if (path.startsWith("/webhook/")) {
            filterChain.doFilter(request, response);
            return;
        }

        // Pula a validação de token para endpoints públicos
        if (isPublicEndpoint(path)) {
            log.debug("Endpoint público detectado: {}", path);
            filterChain.doFilter(request, response);
            return;
        }

        var token = this.recoverToken(request);

        log.debug("URI: {}", request.getRequestURI());
        log.debug("Token recebido: {}", token != null ? "Presente" : "Ausente");

        if (token != null) {
            var login = tokenService.validateToken(token);
            log.debug("Token validado para: {}", login);

            if (login != null) {
                try {
                    User user = userRepository.findByEmail(login)
                            .orElseThrow(() -> new RuntimeException("User Not Found"));

                    var authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));
                    var authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    log.debug("Usuário autenticado: {}", user.getEmail());
                } catch (Exception e) {
                    log.error("Erro ao autenticar usuário: {}", e.getMessage());
                }
            } else {
                log.warn("Token inválido ou expirado");
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Verifica se o endpoint é público e não requer autenticação
     */
    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/webhook/") ||
                path.startsWith("/auth/login") ||
                path.startsWith("/auth/register") ||
                path.startsWith("/auth/confirm") ||
                path.startsWith("/h2-console/") ||
                path.startsWith("/webjars/") ||
                path.startsWith("/css/") ||
                path.startsWith("/js/") ||
                path.startsWith("/images/") ||
                path.startsWith("/static/") ||
                path.equals("/favicon.ico");
    }

    private String recoverToken(HttpServletRequest request) {
        var authHeader = request.getHeader("Authorization");
        if (authHeader == null) {
            log.debug("Header Authorization não encontrado");
            return null;
        }
        return authHeader.replace("Bearer ", "");
    }
}