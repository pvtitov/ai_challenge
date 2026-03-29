package com.github.pvtitov.aichat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class AichatApplication {

	public static void main(String[] args) {
		SpringApplication.run(AichatApplication.class, args);
	}

	@Bean
	public AichatManager aichatManager() {
		return new AichatManager();
	}
}
