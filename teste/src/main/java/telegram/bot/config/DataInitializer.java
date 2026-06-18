package telegram.bot.config;

import java.time.LocalDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import telegram.bot.domain.ChatConfig;
import telegram.bot.domain.FiltroAssunto;
import telegram.bot.repository.ChatConfigRepository;
import telegram.bot.repository.FiltroAssuntoRepository;

@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final ChatConfigRepository chatConfigRepo;
    private final FiltroAssuntoRepository filtroRepo;

    @Value("${telegram.chat.ids:${telegram.chat.id:0}}")
    private String chatIds;

    @Value("${gmail.subject.filter:}")
    private String defaultSubjectFilter;

    public DataInitializer(ChatConfigRepository chatConfigRepo,
                           FiltroAssuntoRepository filtroRepo) {
        this.chatConfigRepo = chatConfigRepo;
        this.filtroRepo = filtroRepo;
    }

    @Override
    public void run(ApplicationArguments args) {
        seedChats();
        seedFiltroPadrao();
    }

    private void seedChats() {
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

    private void seedFiltroPadrao() {
        if (filtroRepo.count() > 0) return;
        if (defaultSubjectFilter == null || defaultSubjectFilter.isBlank()) return;
        FiltroAssunto seed = FiltroAssunto.builder()
                .nome("Urgência Renal (padrão)")
                .padrao(defaultSubjectFilter)
                .ativo(true)
                .dataCadastro(LocalDateTime.now())
                .build();
        filtroRepo.save(seed);
        log.info("Filtro de assunto padrão criado: '{}'", defaultSubjectFilter);
    }
}
