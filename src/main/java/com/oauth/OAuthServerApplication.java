package com.oauth;

import com.oauth.config.MemoryMonitor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(scanBasePackages = {"com.oauth.*"})
public class OAuthServerApplication {

	public static void main(String[] args) throws Exception {
		SpringApplication.run(OAuthServerApplication.class, args);
	}

	// CommandLineRunner 사용하여 애플리케이션 시작 후 메모리 모니터링
	@Bean
	public CommandLineRunner memoryMonitorRunner() {
		return args -> {
			MemoryMonitor.logMemoryUsage();
		};
	}
}
