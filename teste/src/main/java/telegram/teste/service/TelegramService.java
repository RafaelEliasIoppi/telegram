package telegram.teste.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class TelegramService {

    @Value("${telegram.bot.token}")
    private String token;

    // chatId padrão (caso não seja passado outro)
    @Value("${telegram.chat.id}")
    private String defaultChatId;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Envia mensagem para um chat específico.
     * Se destinatário for nulo ou vazio, usa o chatId padrão.
     */
    public void sendMessage(String text, String destinatario) {
        String url = "https://api.telegram.org/bot" + token + "/sendMessage";

        String chatId = (destinatario == null || destinatario.isBlank())
                ? defaultChatId
                : destinatario;

        Map<String, String> params = Map.of(
                "chat_id", chatId,
                "text", text
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, String>> request = new HttpEntity<>(params, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);

            if (response.getStatusCode() != HttpStatus.OK) {
                throw new RuntimeException("Erro ao enviar mensagem: " + response.getBody());
            }

            System.out.println("Mensagem enviada com sucesso: " + response.getBody());

        } catch (Exception e) {
            System.err.println("Falha ao enviar mensagem para Telegram: " + e.getMessage());
        }
    }
}
