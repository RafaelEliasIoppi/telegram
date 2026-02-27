package telegram.teste.service;

import java.io.IOException;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TelegramService {

    private static final Logger logger = LoggerFactory.getLogger(TelegramService.class);

    @Value("${telegram.bot.token:}")
    private String token;

    @Value("${telegram.chat.id:}")
    private String defaultChatId;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Envia mensagem de texto simples.
     */
    public void sendMessage(String text, String destinatario) {
        sendToTelegram("sendMessage", Map.of(
                "chat_id", resolveChatId(destinatario),
                "text", text,
                "parse_mode", "Markdown"
        ), "mensagem");
    }

    /**
     * Envia uma foto com legenda usando upload de arquivo local.
     */
    public void sendPhotoFile(MultipartFile foto, String caption, String destinatario) throws IOException {
        if (token == null || token.isBlank()) {
            logger.warn("Token não configurado.");
            return;
        }

        String chatId = resolveChatId(destinatario);
        if (chatId == null) return;

        String url = "https://api.telegram.org/bot" + token + "/sendPhoto";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("chat_id", chatId);
        body.add("photo", new ByteArrayResource(foto.getBytes()) {
            @Override
            public String getFilename() {
                return foto.getOriginalFilename();
            }
        });
        if (caption != null && !caption.isBlank()) {
            body.add("caption", caption);
            body.add("parse_mode", "Markdown");
        }

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                logger.error("Erro ao enviar foto. Status: {} - Body: {}", response.getStatusCode(), response.getBody());
            } else {
                logger.info("Foto enviada com sucesso para o Telegram.");
            }
        } catch (Exception e) {
            logger.error("Falha ao enviar foto: {}", e.getMessage(), e);
            throw e;
        }
    }

    private String resolveChatId(String destinatario) {
        String chatId = (destinatario == null || destinatario.isBlank()) ? defaultChatId : destinatario;
        if (chatId == null || chatId.isBlank()) {
            logger.warn("ChatId não configurado.");
            return null;
        }
        return chatId;
    }

    private void sendToTelegram(String method, Map<String, String> body, String tipo) {
        if (token == null || token.isBlank()) {
            logger.warn("Token do Telegram não configurado.");
            return;
        }

        String url = "https://api.telegram.org/bot" + token + "/" + method;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
            if (!response.getStatusCode().is2xxSuccessful()) {
                logger.error("Erro ao enviar {}. Status: {} - Body: {}", tipo, response.getStatusCode(), response.getBody());
            } else {
                logger.info("{} enviada com sucesso para o Telegram.", tipo);
            }
        } catch (Exception e) {
            logger.error("Falha ao enviar {}: {}", tipo, e.getMessage(), e);
        }
    }
}
