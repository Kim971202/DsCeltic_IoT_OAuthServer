package com.oauth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(scanBasePackages = {"com.oauth.*"})
public class OAuthServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(OAuthServerApplication.class, args);
	}

}
