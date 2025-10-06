package com.example.loginauthapi.dto.zapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ZapiChatItemDTO {
    private String name;
    private String phone;

    @JsonProperty("lastMessageTime")
    private String lastMessageTime; // A Z-API retorna como String, não Long

    @JsonProperty("isGroup")
    private Boolean isGroup;

    @JsonProperty("unread")
    private String unread; // A Z-API retorna como String

    @JsonProperty("messagesUnread")
    private String messagesUnread; // Também disponível

    private String pinned;
    private String archived;
    private String isMuted;
    private String isMarkedSpam;
    private Boolean isGroupAnnouncement;
}