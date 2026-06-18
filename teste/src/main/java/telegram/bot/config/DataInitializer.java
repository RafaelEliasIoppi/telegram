package telegram.bot.config;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import telegram.bot.domain.ChatConfig;
import telegram.bot.repository.ChatConfigRepository;

@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final ChatConfigRepository chatConfigRepo;

    @Value("${telegram.chat.ids:${telegram.chat.id:0}}")
    private String chatIds;

    public DataInitializer(ChatConfigRepository chatConfigRepo) {
        this.chatConfigRepo = chatConfigRepo;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (chatIds == null || chatIds.isBlank() || chatIds.equals("0")) return;

        for (String idStr : chatIds.split(",")) {
            idStr = idStr.trim();
            if (idStr.isEmpty()) continue;
            try {
                long chatId = Long.parseLong(idStr);
                if (chatConfigRepo.findById(chatId).isEmpty()) {
                    ChatConfig config = ChatConfig.builder()
                            .chatId(chatId)
                            .nome("Chat principal")
                            .ativo(true)
                            .nivelMinimo("INFO")
                            .fontesAtivas(null)
                            .dataCadastro(LocalDateTime.now())
                            .build();
                    chatConfigRepo.save(config);
                    log.info("Chat {} cadastrado automaticamente", chatId);
                }
            } catch (NumberFormatException e) {
                log.warn("Chat ID inválido no .env: '{}'", idStr);
            }
        }
    }
}
