package telegram.bot.controller;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
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

    /**
     * Token secreto esperado no header X-Telegram-Bot-Api-Secret-Token.
     * Se vazio, a validação fica desativada (comportamento compatível com o atual).
     */
    @Value("${telegram.webhook.secret:}")
    private String webhookSecret;

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
    public ResponseEntity<String> receberUpdate(
            @RequestBody Map<String, Object> update,
            @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secretToken) {
        // Se um secret estiver configurado, exigimos que o header bata exatamente.
        // Com secret vazio, mantemos o comportamento atual (sem validação).
        if (webhookSecret != null && !webhookSecret.isBlank()
                && !webhookSecret.equals(secretToken)) {
            logger.warn("Update do webhook rejeitado: secret token ausente ou inválido.");
            return ResponseEntity.status(401).body("unauthorized");
        }
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
