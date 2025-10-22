package com.example.loginauthapi.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PreConfiguredTextDTO {
    private String id;
    private String title;
    private String content;
    private String createdAt;
    private String updatedAt;
}