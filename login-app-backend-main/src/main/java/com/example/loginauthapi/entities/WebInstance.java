package com.example.loginauthapi.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "web_instances")
public class WebInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)  // relação N:1 com User
    @JoinColumn(name = "user_id", nullable = false)      // FK para tabela users
    private User user;

    @Column(nullable = false)
    private String status;  // Ex: ACTIVE, DEACTIVATED, INACTIVE, PENDING...

    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @Column(name = "expira_em")
    private LocalDateTime expiraEm;

    @Column(name = "client_token", unique = true, nullable = false)
    private String clientToken;

    @Column(name = "seu_token", unique = true, nullable = false)
    private String seuToken;

    @Column(name = "sua_instancia", nullable = false)
    private String suaInstancia;

    // ⭐ NOVO CAMPO: Telefone conectado à instância
    @Column(name = "connected_phone", length = 20, nullable = false)
    private String connectedPhone;

    // Relacionamento 1:N -> uma WebInstance pode ter vários Chats
    @OneToMany(mappedBy = "webInstance", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Chat> chats = new ArrayList<>();

    @PrePersist
    public void prePersist() {
        this.criadoEm = LocalDateTime.now();

        // ⭐ Se expiraEm não foi definido, aplicar 30 dias automaticamente
        if (this.expiraEm == null) {
            this.expiraEm = LocalDateTime.now().plusDays(30);
        }
    }
}