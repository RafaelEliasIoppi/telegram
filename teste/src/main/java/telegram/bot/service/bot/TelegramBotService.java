package telegram.bot.service.bot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Serviço responsável pela comunicação HTTP com a API do Telegram Bot.
 *
 * <p>Encapsula chamadas REST utilizando {@link RestTemplate} e {@link Map}
 * como corpo das requisições, sem dependência de bibliotecas externas de
 * Telegram. Suporta envio de mensagens, mensagens com botões inline,
 * resposta a callback queries e gerenciamento de webhook.</p>
 */
@Service
public class TelegramBotService {

    private static final Logger logger = LoggerFactory.getLogger(TelegramBotService.class);

    private static final String API_BASE = "https://api.telegram.org/bot%s/%s";

    @Value("${telegram.bot.token}")
    private String token;

    @Value("${telegram.webhook.url:}")
    private String webhookUrl;

    @Value("${telegram.webhook.enabled:false}")
    private boolean webhookEnabled;

    private final RestTemplate restTemplate;

    public TelegramBotService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Registra automaticamente o webhook configurado se a flag
     * {@code telegram.webhook.enabled} estiver ligada e {@code telegram.webhook.url}
     * estiver preenchida.
     */
    @PostConstruct
    public void init() {
        if (webhookEnabled && webhookUrl != null && !webhookUrl.isBlank()) {
            try {
                String resposta = registrarWebhook();
                logger.info("Webhook registrado em '{}'. Resposta: {}", webhookUrl, resposta);
            } catch (Exception e) {
                logger.error("Falha ao registrar webhook automaticamente: {}", e.getMessage());
            }
        } else {
            logger.info("Webhook não habilitado (telegram.webhook.enabled={}, url='{}'). Modo polling/manual.",
                    webhookEnabled, webhookUrl);
        }
    }

    /**
     * Envia uma mensagem de texto simples (sem parse mode) para o chat informado.
     *
     * @param chatId identificador do chat no Telegram
     * @param texto  conteúdo a enviar
     */
    public void enviarMensagem(long chatId, String texto) {
        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", texto);
        post("sendMessage", body);
    }

    /**
     * Envia uma mensagem formatada em Markdown para o chat informado.
     *
     * @param chatId identificador do chat no Telegram
     * @param texto  conteúdo Markdown (parse_mode "Markdown")
     */
    public void enviarMarkdown(long chatId, String texto) {
        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", texto);
        body.put("parse_mode", "Markdown");
        post("sendMessage", body);
    }

    /**
     * Envia uma mensagem com botões inline.
     *
     * <p>Cada elemento da lista externa representa uma linha de botões; cada
     * mapa interno deve conter ao menos as chaves {@code text} e
     * {@code callback_data}.</p>
     *
     * @param chatId identificador do chat
     * @param texto  texto da mensagem (formato Markdown)
     * @param botoes linhas de botões inline
     */
    public void enviarComBotoes(long chatId, String texto, List<List<Map<String, String>>> botoes) {
        Map<String, Object> replyMarkup = new HashMap<>();
        replyMarkup.put("inline_keyboard", botoes);

        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("text", texto);
        body.put("parse_mode", "Markdown");
        body.put("reply_markup", replyMarkup);

        post("sendMessage", body);
    }

    /**
     * Responde a um callback_query, removendo o spinner exibido no cliente.
     *
     * @param callbackQueryId identificador do callback recebido
     * @param texto           texto opcional exibido como toast (pode ser {@code null})
     */
    public void responderCallback(String callbackQueryId, String texto) {
        Map<String, Object> body = new HashMap<>();
        body.put("callback_query_id", callbackQueryId);
        if (texto != null) {
            body.put("text", texto);
        }
        post("answerCallbackQuery", body);
    }

    /**
     * Registra o webhook configurado na propriedade {@code telegram.webhook.url}.
     *
     * @return resposta crua da API do Telegram
     */
    public String registrarWebhook() {
        Map<String, Object> body = new HashMap<>();
        body.put("url", webhookUrl);
        body.put("allowed_updates", List.of("message", "callback_query"));
        return post("setWebhook", body);
    }

    /**
     * Remove o webhook ativo, voltando o bot ao modo polling.
     *
     * @return resposta crua da API do Telegram
     */
    public String removerWebhook() {
        return post("deleteWebhook", new HashMap<>());
    }

    /**
     * Consulta informações básicas do bot (método {@code getMe}).
     *
     * @return resposta crua da API do Telegram
     */
    public String getMe() {
        return post("getMe", new HashMap<>());
    }

    /**
     * Helper de POST genérico para a API do Telegram.
     */
    private String post(String method, Map<String, Object> body) {
        String url = String.format(API_BASE, token, method);
        try {
            String resposta = restTemplate.postForObject(url, body, String.class);
            if (logger.isDebugEnabled()) {
                logger.debug("Telegram {} -> {}", method, resposta);
            }
            return resposta;
        } catch (Exception e) {
            logger.error("Erro ao chamar Telegram method='{}': {}", method, e.getMessage());
            return null;
        }
    }

    /**
     * Helper utilitário para construir uma linha de botões inline.
     *
     * @param pares pares (texto, callback_data) que compõem a linha
     * @return linha de botões pronta para uso em {@link #enviarComBotoes}
     */
    public static List<Map<String, String>> linhaDeBotoes(String... pares) {
        if (pares.length % 2 != 0) {
            throw new IllegalArgumentException("Quantidade de argumentos deve ser par (texto, callback_data).");
        }
        List<Map<String, String>> linha = new ArrayList<>();
        for (int i = 0; i < pares.length; i += 2) {
            Map<String, String> botao = new HashMap<>();
            botao.put("text", pares[i]);
            botao.put("callback_data", pares[i + 1]);
            linha.add(botao);
        }
        return linha;
    }
}
