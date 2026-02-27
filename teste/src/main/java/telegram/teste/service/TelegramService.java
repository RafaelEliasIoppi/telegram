package telegram.teste.service;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;

@Service
public class TelegramService {

    private static final Logger logger = LoggerFactory.getLogger(TelegramService.class);

    @Value("${telegram.bot.token:}")
    private String token;

    @Value("${telegram.chat.id:}")
    private String defaultChatId;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Envia mensagem para um chat específico.
     * Se destinatario for nulo ou vazio, usa o chatId padrão.
     */
    public void sendMessage(String text, String destinatario) {

        if (token == null || token.isBlank()) {
            logger.warn("Telegram bot token não configurado.");
            return;
        }

        String chatId = (destinatario == null || destinatario.isBlank())
                ? defaultChatId
                : destinatario;

        if (chatId == null || chatId.isBlank()) {
            logger.warn("Telegram chat id não configurado.");
            return;
        }

        String url = "https://api.telegram.org/bot" + token + "/sendMessage";

        // Ajuste aqui: Usamos um HashMap para incluir o parse_mode
        Map<String, String> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", text);
        body.put("parse_mode", "Markdown"); // Permite negrito (*), itálico (_) e códigos (`)

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response =
                    restTemplate.postForEntity(url, request, String.class);

            HttpStatusCode status = response.getStatusCode();

            if (!status.is2xxSuccessful()) {
                logger.error("Erro ao enviar mensagem para Telegram. Status: {} - Body: {}",
                        status, response.getBody());
            } else {
                logger.info("Mensagem enviada com sucesso para o Telegram.");
            }

        } catch (Exception e) {
            logger.error("Falha ao enviar mensagem para Telegram: {}", e.getMessage(), e);
        }
    }

    @PostConstruct
        public void init() {
            logger.info("Token configurado? {}", token != null && !token.isBlank());
            logger.info("ChatId configurado? {}", defaultChatId != null && !defaultChatId.isBlank());
    }

}