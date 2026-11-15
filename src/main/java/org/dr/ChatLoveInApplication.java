package org.dr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ChatLoveInApplication {

	public static void main(String[] args) {
		SpringApplication.run(ChatLoveInApplication.class, args);
	}

}
