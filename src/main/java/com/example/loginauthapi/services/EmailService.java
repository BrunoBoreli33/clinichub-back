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

    /**
     * Email de verificação para cadastro
     */
    public void sendVerificationEmail(String to, String code) {
        String subject = "Confirmação de cadastro";
        String body = "<h2>Seu código de confirmação é:</h2><p><strong>" + code + "</strong></p>";

        CreateEmailOptions params = CreateEmailOptions.builder()
                .from("noreply-clinichubcrm <contato@brunoboreli.com>")
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

    /**
     * Email customizado para alteração de perfil
     */
    public void sendProfileUpdateVerificationEmail(String to, String code, String updateType) {
        String subject = updateType.equals("EMAIL")
                ? "Confirmação de alteração de email"
                : "Confirmação de alteração de senha";

        String body = "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;'>" +
                "<h2 style='color: #16a34a;'>Confirmação de Alteração</h2>" +
                "<p>Você solicitou a alteração de " + (updateType.equals("EMAIL") ? "email" : "senha") + ".</p>" +
                "<p>Seu código de confirmação é:</p>" +
                "<div style='background: #f0fdf4; padding: 20px; border-radius: 8px; text-align: center; margin: 20px 0;'>" +
                "<p style='font-size: 32px; font-weight: bold; color: #16a34a; margin: 0;'>" + code + "</p>" +
                "</div>" +
                "<p style='color: #6b7280; font-size: 14px;'>Este código expira em 15 minutos.</p>" +
                "<p style='color: #6b7280; font-size: 14px;'>Se você não solicitou esta alteração, ignore este email.</p>" +
                "</div>";

        CreateEmailOptions params = CreateEmailOptions.builder()
                .from("noreply-clinichubcrm <contato@brunoboreli.com>")
                .to(to)
                .subject(subject)
                .html(body)
                .build();

        try {
            CreateEmailResponse data = resend.emails().send(params);
            System.out.println("Email de alteração enviado com ID: " + data.getId());
        } catch (ResendException e) {
            e.printStackTrace();
            throw new RuntimeException("Erro ao enviar email de verificação de alteração", e);
        }
    }

    /**
     * Email de recuperação de senha
     */
    public void sendPasswordResetCode(String to, String code) {
        String subject = "Recuperação de senha";
        String body = "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;'>" +
                "<h2 style='color: #16a34a;'>Recuperação de Senha</h2>" +
                "<p>Você solicitou a recuperação de senha da sua conta.</p>" +
                "<p>Seu código de verificação é:</p>" +
                "<div style='background: #f0fdf4; padding: 20px; border-radius: 8px; text-align: center; margin: 20px 0;'>" +
                "<p style='font-size: 32px; font-weight: bold; color: #16a34a; margin: 0;'>" + code + "</p>" +
                "</div>" +
                "<p style='color: #6b7280; font-size: 14px;'>Este código expira em 15 minutos.</p>" +
                "<p style='color: #6b7280; font-size: 14px;'>Você tem até 3 tentativas para inserir o código correto.</p>" +
                "<p style='color: #ef4444; font-size: 14px; font-weight: bold;'>Se você não solicitou esta recuperação, ignore este email e sua senha permanecerá inalterada.</p>" +
                "</div>";

        CreateEmailOptions params = CreateEmailOptions.builder()
                .from("noreply-clinichubcrm <contato@brunoboreli.com>")
                .to(to)
                .subject(subject)
                .html(body)
                .build();

        try {
            CreateEmailResponse data = resend.emails().send(params);
            System.out.println("Email de recuperação de senha enviado com ID: " + data.getId());
        } catch (ResendException e) {
            e.printStackTrace();
            throw new RuntimeException("Erro ao enviar email de recuperação de senha", e);
        }
    }

    /**
     * Email de confirmação de senha alterada
     */
    public void sendPasswordChangedConfirmation(String to) {
        String subject = "Senha alterada com sucesso";
        String body = "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto;'>" +
                "<h2 style='color: #16a34a;'>Senha Alterada com Sucesso</h2>" +
                "<p>Sua senha foi alterada com sucesso.</p>" +
                "<p>Se você não realizou esta alteração, entre em contato conosco imediatamente.</p>" +
                "<div style='background: #f0fdf4; padding: 20px; border-radius: 8px; margin: 20px 0;'>" +
                "<p style='color: #16a34a; margin: 0;'>✓ Sua conta está segura</p>" +
                "</div>" +
                "<p style='color: #6b7280; font-size: 14px;'>Atenciosamente,<br/>Equipe ClinicHub CRM</p>" +
                "</div>";

        CreateEmailOptions params = CreateEmailOptions.builder()
                .from("noreply-clinichubcrm <contato@brunoboreli.com>")
                .to(to)
                .subject(subject)
                .html(body)
                .build();

        try {
            CreateEmailResponse data = resend.emails().send(params);
            System.out.println("Email de confirmação de senha alterada enviado com ID: " + data.getId());
        } catch (ResendException e) {
            e.printStackTrace();
            throw new RuntimeException("Erro ao enviar email de confirmação", e);
        }
    }
}