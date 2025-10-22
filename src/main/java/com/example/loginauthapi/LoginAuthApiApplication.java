package com.example.loginauthapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling  // ✅ NOVO: Habilita agendamento de tarefas
public class LoginAuthApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(LoginAuthApiApplication.class, args);
	}

}
