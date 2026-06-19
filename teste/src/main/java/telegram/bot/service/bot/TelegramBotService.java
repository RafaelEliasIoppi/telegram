package telegram.bot.service.bot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.annotation.PostConstruct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
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

    /** Token secreto opcional para validar updates recebidos no webhook. */
    @Value("${telegram.webhook.secret:}")
    private String webhookSecret;

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
     * Envia uma foto com legenda em Markdown.
     *
     * @param chatId   identificador do chat
     * @param photoUrl URL pública da imagem (jpg/png/webp)
     * @param caption  legenda Markdown (pode ser {@code null})
     */
    public void enviarFotoMarkdown(long chatId, String photoUrl, String caption) {
        Map<String, Object> body = new HashMap<>();
        body.put("chat_id", chatId);
        body.put("photo", photoUrl);
        if (caption != null && !caption.isBlank()) {
            // Telegram limita caption a 1024 chars
            String cap = caption.length() > 1024 ? caption.substring(0, 1020) + "..." : caption;
            body.put("caption", cap);
            body.put("parse_mode", "Markdown");
        }
        post("sendPhoto", body);
    }

    /**
     * Faz upload (multipart) de um arquivo para o Telegram.
     *
     * @param chatId        chat de destino
     * @param method        "sendPhoto" para foto ou "sendDocument" para arquivo qualquer
     * @param campo         "photo" ou "document" (deve casar com o {@code method})
     * @param nomeArquivo   nome original (com extensão)
     * @param bytes         conteúdo do arquivo
     * @param caption       legenda Markdown opcional (até 1024 chars)
     * @return resposta crua da API
     */
    public String enviarArquivoMultipart(long chatId, String method, String campo,
                                         String nomeArquivo, byte[] bytes, String caption) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("chat_id", String.valueOf(chatId));
        if (caption != null && !caption.isBlank()) {
            String cap = caption.length() > 1024 ? caption.substring(0, 1020) + "..." : caption;
            body.add("caption", cap);
            body.add("parse_mode", "Markdown");
        }
        ByteArrayResource res = new ByteArrayResource(bytes) {
            @Override public String getFilename() { return nomeArquivo; }
        };
        body.add(campo, res);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        HttpEntity<MultiValueMap<String, Object>> req = new HttpEntity<>(body, headers);
        String url = String.format(API_BASE, token, method);
        try {
            return restTemplate.postForObject(url, req, String.class);
        } catch (Exception e) {
            logger.error("Telegram {} upload falhou: {}", method, e.getMessage());
            throw new RuntimeException("Falha ao enviar para o Telegram: " + e.getMessage(), e);
        }
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
        // Se configurado, o Telegram passará a enviar este token no header
        // X-Telegram-Bot-Api-Secret-Token a cada update, permitindo validar a origem.
        if (webhookSecret != null && !webhookSecret.isBlank()) {
            body.put("secret_token", webhookSecret);
        }
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
        } catch (org.springframework.web.client.HttpStatusCodeException e) {
            // Erros HTTP da própria API do Telegram: logamos status + corpo para
            // facilitar o diagnóstico (Markdown inválido, bloqueio, rate limit, etc.).
            int status = e.getStatusCode().value();
            String corpo = e.getResponseBodyAsString();
            switch (status) {
                case 400 ->
                    logger.warn("Telegram {} retornou 400 (Bad Request — ex.: Markdown inválido/entidade malformada). Corpo: {}",
                            method, corpo);
                case 403 ->
                    logger.warn("Telegram {} retornou 403 (Forbidden — bot bloqueado/sem permissão no chat). Corpo: {}",
                            method, corpo);
                case 429 ->
                    logger.warn("Telegram {} retornou 429 (Too Many Requests — rate limit). Corpo: {}",
                            method, corpo);
                default ->
                    logger.warn("Telegram {} retornou HTTP {}. Corpo: {}", method, status, corpo);
            }
            return null;
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
