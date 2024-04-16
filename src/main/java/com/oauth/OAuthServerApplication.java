package com.oauth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.oauth.*"})
public class OAuthServerApplication {

	public static void main(String[] args) throws Exception {
		SpringApplication.run(OAuthServerApplication.class, args);
	}

}
