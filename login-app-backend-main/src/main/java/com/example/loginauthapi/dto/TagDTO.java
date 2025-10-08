package com.example.loginauthapi.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TagDTO {
    private String id;
    private String name;
    private String color;
    private LocalDateTime criadoEm;
    private LocalDateTime atualizadoEm;
}