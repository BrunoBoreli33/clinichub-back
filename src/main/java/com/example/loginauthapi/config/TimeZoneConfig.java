package com.example.loginauthapi.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

/**
 * Configuração de TimeZone para garantir que as datas sejam salvas corretamente
 * Resolve o Bug 1: Hora sendo salva incorretamente no banco de dados
 */
@Configuration
public class TimeZoneConfig {

    /**
     * Define o timezone padrão da aplicação como America/Sao_Paulo (BRT/BRST)
     */
    @PostConstruct
    public void init() {
        // Define o timezone padrão da JVM
        TimeZone.setDefault(TimeZone.getTimeZone("America/Sao_Paulo"));
    }

    /**
     * Configura o ObjectMapper para deserializar corretamente datas ISO 8601 com timezone
     * para LocalDateTime considerando o fuso horário brasileiro
     */
    @Bean
    public Jackson2ObjectMapperBuilder jackson2ObjectMapperBuilder() {
        return new Jackson2ObjectMapperBuilder()
                .timeZone(TimeZone.getTimeZone("America/Sao_Paulo"))
                .modules(new JavaTimeModule());
    }
}