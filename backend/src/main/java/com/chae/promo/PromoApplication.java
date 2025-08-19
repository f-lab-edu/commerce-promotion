package com.chae.promo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.retry.annotation.EnableRetry;

@EnableJpaAuditing
@EnableRetry
@SpringBootApplication
public class PromoApplication {

	public static void main(String[] args) {
		SpringApplication.run(PromoApplication.class, args);
	}

}
