package telegram.bot.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import telegram.bot.service.bot.CommandDispatcher;

/**
 * Endpoint HTTP que recebe updates do Telegram via webhook.
 *
 * <p>Aceita tanto mensagens normais quanto callback queries de botões
 * inline e delega o processamento ao {@link CommandDispatcher}.</p>
 */
@RestController
@RequestMapping("/webhook")
public class BotWebhookController {

    private static final Logger logger = LoggerFactory.getLogger(BotWebhookController.class);

    private final CommandDispatcher dispatcher;

    public BotWebhookController(CommandDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }

    /**
     * Recebe o update do Telegram. Sempre responde {@code 200 OK} para
     * evitar reentregas, mesmo em caso de erro interno.
     *
     * @param update payload JSON convertido em mapa
     * @return resposta {@code "ok"}
     */
    @PostMapping
    public ResponseEntity<String> receberUpdate(@RequestBody Map<String, Object> update) {
        try {
            if (update == null) {
                return ResponseEntity.ok("ok");
            }
            if (update.containsKey("message")) {
                dispatcher.processarComando(update);
            }
            if (update.containsKey("callback_query")) {
                dispatcher.processarCallback(update);
            }
        } catch (Exception e) {
            logger.error("Erro ao processar update do Telegram: {}", e.getMessage(), e);
        }
        return ResponseEntity.ok("ok");
    }
}
