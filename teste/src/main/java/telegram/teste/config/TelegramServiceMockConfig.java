package telegram.teste.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import telegram.teste.service.TelegramService;

@TestConfiguration
public class TelegramServiceMockConfig {

    @Bean
    public TelegramService telegramService() {
        return new TelegramService() {
            @Override
            public void sendMessage(String msg, String chatId) {
                // Mock: apenas imprime no console em vez de chamar a API real
                System.out.println("Mock TelegramService -> Mensagem: " + msg + " | ChatId: " + chatId);
            }
        };
    }
}
