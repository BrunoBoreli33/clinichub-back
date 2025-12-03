package com.example.loginauthapi.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoutineTextRequestDTO {

    @NotNull(message = "Número da sequência é obrigatório")
    @Min(value = 1, message = "Sequência deve ser entre 1 e 7")
    @Max(value = 7, message = "Sequência deve ser entre 1 e 7")
    private Integer sequenceNumber;

    @NotBlank(message = "Texto não pode ser vazio")
    private String textContent;

    @NotNull(message = "Horas de delay é obrigatório")
    @Min(value = 0, message = "Horas devem ser entre 0 e 100")
    @Max(value = 100, message = "Horas devem ser entre 0 e 100")
    private Integer hoursDelay;

    private List<String> photoIds;  // ✅ NOVO: IDs das fotos (opcional)
    private List<String> videoIds;  // ✅ NOVO: IDs dos vídeos (opcional)
}