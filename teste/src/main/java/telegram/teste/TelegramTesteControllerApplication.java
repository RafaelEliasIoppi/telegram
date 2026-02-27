package telegram.teste;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;



@SpringBootApplication
public class TelegramTesteControllerApplication {
    public static void main(String[] args) {
        System.setProperty("spring.config.name", "application-testecontroller");
        SpringApplication.run(TelegramTesteControllerApplication.class, args);
    }
}
