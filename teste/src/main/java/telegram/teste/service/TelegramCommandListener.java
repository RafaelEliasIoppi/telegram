package telegram.teste.service;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class TelegramCommandListener {
    private static final Logger logger = LoggerFactory.getLogger(TelegramCommandListener.class);
    private final RestTemplate rest = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private TelegramService telegramService;

    @Autowired
    private DefesaCivilMonitor defesaCivilMonitor;

    private long lastUpdateId = 0;

    // Polla o Telegram a cada 5s por padrão
    @Scheduled(fixedDelayString = "${telegram.poll.delay:5000}")
    public void poll() {
        try {
            String token = settingsService.readConfig().getOrDefault("telegram.bot.token", "");
            if (token == null || token.isBlank()) return;
            String url = "https://api.telegram.org/bot" + token + "/getUpdates?timeout=0";
            if (lastUpdateId > 0) url += "&offset=" + (lastUpdateId + 1);
            String resp = rest.getForObject(url, String.class);
            if (resp == null) return;
            JsonNode root = mapper.readTree(resp);
            if (!root.path("ok").asBoolean(false)) return;
            JsonNode results = root.path("result");
            if (!results.isArray()) return;
            for (JsonNode u : results) {
                long upd = u.path("update_id").asLong(0);
                if (upd > lastUpdateId) lastUpdateId = upd;
                // tratar mensagens comuns
                JsonNode msg = u.path("message");
                if (msg.isMissingNode()) msg = u.path("edited_message");
                if (!msg.isMissingNode()) {
                    String text = msg.path("text").asText("");
                    String chatId = msg.path("chat").path("id").asText("");
                    if (text != null && !text.isBlank()) handleCommand(text.trim(), chatId);
                }

                // tratar callbacks (botões inline)
                JsonNode callback = u.path("callback_query");
                if (!callback.isMissingNode()) {
                    String data = callback.path("data").asText("");
                    String callbackId = callback.path("id").asText("");
                    String fromChat = callback.path("message").path("chat").path("id").asText("");
                    processCallback(data, callbackId, fromChat);
                }
            }
        } catch (Exception e) {
            logger.debug("Erro ao poll Telegram: {}", e.getMessage());
        }
    }

    private void handleCommand(String text, String chatId) {
        try {
            logger.info("Comando recebido de {}: {}", chatId, text);
            String allowed = settingsService.readConfig().getOrDefault("telegram.chat.id", "");
            if (allowed != null && !allowed.isBlank() && !allowed.equals(chatId)) {
                telegramService.sendMessage("Acesso negado: este bot aceita comandos somente do chat autorizado.", chatId);
                return;
            }

            String lower = text.toLowerCase();
            if (lower.startsWith("/verificar")) {
                defesaCivilMonitor.verificarAgora();
                telegramService.sendMessage("🔎 Verificação executada com sucesso.", chatId);
                return;
            }
            if (lower.startsWith("/candidatos")) {
                List<String> cands = defesaCivilMonitor.listarCandidatos(8);
                if (cands.isEmpty()) {
                    telegramService.sendMessage("Nenhum candidato encontrado.", chatId);
                } else {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < cands.size(); i++) {
                        sb.append(i+1).append(". ").append(cands.get(i)).append("\n\n");
                    }
                    telegramService.sendMessage(sb.toString(), chatId);
                }
                return;
            }
            if (lower.startsWith("/enviar")) {
                String[] parts = text.split("\\s+", 2);
                int idx = 1;
                try { if (parts.length > 1) idx = Integer.parseInt(parts[1].trim()); } catch (Exception ex) { }
                List<String> cands = defesaCivilMonitor.listarCandidatos(12);
                if (idx < 1 || idx > cands.size()) {
                    telegramService.sendMessage("Índice inválido. Use /candidatos para ver a lista.", chatId);
                } else {
                    // envia confirmação por botão inline
                    String title = "Confirmar envio do candidato " + idx + "?";
                    java.util.Map<String, Object> kb = java.util.Map.of(
                        "inline_keyboard", List.of(
                            List.of(
                                java.util.Map.of("text", "Confirmar", "callback_data", "confirm_send:" + idx),
                                java.util.Map.of("text", "Cancelar", "callback_data", "cancel_send:" + idx)
                            )
                        )
                    );
                    telegramService.sendMessageWithReplyMarkup(title + "\n\n" + cands.get(idx-1), chatId, kb);
                }
                return;
            }
            if (lower.startsWith("/salvar")) {
                String[] parts = text.split("\\s+", 2);
                int idx = 1;
                try { if (parts.length > 1) idx = Integer.parseInt(parts[1].trim()); } catch (Exception ex) { }
                List<String> cands = defesaCivilMonitor.listarCandidatos(12);
                if (idx < 1 || idx > cands.size()) {
                    telegramService.sendMessage("Índice inválido. Use /candidatos para ver a lista.", chatId);
                } else {
                    String sel = cands.get(idx-1);
                    defesaCivilMonitor.salvarAlerta(sel);
                    telegramService.sendMessage("Candidato salvo como último alerta.", chatId);
                }
                return;
            }
            // default: help
            String help = "Comandos disponíveis:\n" +
                          "/verificar - Executa verificação agora\n" +
                          "/candidatos - Lista candidatos detectados\n" +
                          "/enviar N - Solicita envio do candidato N (confirmação por botão)\n" +
                          "/salvar N - Salva o candidato N como último alerta";
            telegramService.sendMessage(help, chatId);
        } catch (Exception e) {
            logger.error("Erro ao lidar com comando: {}", e.getMessage());
        }
    }

    private void processCallback(String data, String callbackId, String chatId) {
        try {
            if (data == null || data.isBlank()) return;
            if (data.startsWith("confirm_send:")) {
                String s = data.substring("confirm_send:".length());
                int idx = 1;
                try { idx = Integer.parseInt(s); } catch (Exception ex) { }
                List<String> cands = defesaCivilMonitor.listarCandidatos(12);
                if (idx < 1 || idx > cands.size()) {
                    telegramService.answerCallbackQuery(callbackId, "Índice inválido.");
                    return;
                }
                // envia para o chat configurado
                String sel = cands.get(idx-1);
                telegramService.sendMessage(sel, null);
                telegramService.answerCallbackQuery(callbackId, "Enviado com sucesso.");
                return;
            }
            if (data.startsWith("cancel_send:")) {
                telegramService.answerCallbackQuery(callbackId, "Envio cancelado.");
                return;
            }
        } catch (Exception e) {
            logger.error("Erro ao processar callback: {}", e.getMessage());
        }
    }
}
