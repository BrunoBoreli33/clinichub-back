package com.example.loginauthapi.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoutineTextDTO {
    private String id;
    private Integer sequenceNumber;
    private String textContent;
    private Integer hoursDelay;
    private List<String> photoIds;  // ✅ NOVO: IDs das fotos
    private List<String> videoIds;  // ✅ NOVO: IDs dos vídeos
}