package com.example.loginauthapi.dto.zapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZapiChatDetailResponseDTO {
    private String phone;
    private String name;
    private String unread;

    @JsonProperty("lastMessageTime")
    private String lastMessageTime;

    @JsonProperty("isMuted")
    private String isMuted;

    @JsonProperty("isMarkedSpam")
    private String isMarkedSpam;

    @JsonProperty("profileThumbnail")
    private String profileThumbnail; // Este é o campo principal que a Z-API retorna

    @JsonProperty("messagesUnread")
    private Integer messagesUnread;

    private String about;
}