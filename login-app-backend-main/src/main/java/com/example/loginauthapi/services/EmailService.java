package com.example.loginauthapi.services;

import com.resend.*;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.resend.services.emails.model.CreateEmailResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    private final Resend resend;

    public EmailService(@Value("${resend.api.key}") String apiKey) {
        this.resend = new Resend(apiKey);
    }

    public void sendVerificationEmail(String to, String code) {
        String subject = "Confirmação de cadastro";
        String body = "<h2>Seu código de confirmação é:</h2><p><strong>" + code + "</strong></p>";

        CreateEmailOptions params = CreateEmailOptions.builder()
                .from("noreply-clinichubcrm <contato@clinichubcrm.com>")
                .to(to)
                .subject(subject)
                .html(body)
                .build();

        try {
            CreateEmailResponse data = resend.emails().send(params);
            System.out.println("Email enviado com ID: " + data.getId());
        } catch (ResendException e) {
            e.printStackTrace();
            throw new RuntimeException("Erro ao enviar email de verificação", e);
        }
    }
}