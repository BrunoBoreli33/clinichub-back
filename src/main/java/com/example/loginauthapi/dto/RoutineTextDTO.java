package com.example.loginauthapi.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RoutineTextDTO {
    private String id;
    private Integer sequenceNumber;
    private String textContent;
    private Integer hoursDelay;
}