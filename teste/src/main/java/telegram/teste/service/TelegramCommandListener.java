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
                JsonNode msg = u.path("message");
                if (msg.isMissingNode()) msg = u.path("edited_message");
                if (msg.isMissingNode()) continue;
                String text = msg.path("text").asText("");
                String chatId = msg.path("chat").path("id").asText("");
                if (text == null || text.isBlank()) continue;
                handleCommand(text.trim(), chatId);
            }
        } catch (Exception e) {
            logger.debug("Erro ao poll Telegram: {}", e.getMessage());
        }
    }

    private void handleCommand(String text, String chatId) {
        try {
            logger.info("Comando recebido de {}: {}", chatId, text);
            String lower = text.toLowerCase();
            if (lower.startsWith("/verificar")) {
                defesaCivilMonitor.verificarAgora();
                telegramService.sendMessage("🔎 Verificação executada.", chatId);
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
                    String sel = cands.get(idx-1);
                    telegramService.sendMessage(sel, chatId);
                    telegramService.sendMessage("Candidato enviado (via comando).", chatId);
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
                          "/enviar N - Envia o candidato N ao chat\n" +
                          "/salvar N - Salva o candidato N como último alerta";
            telegramService.sendMessage(help, chatId);
        } catch (Exception e) {
            logger.error("Erro ao lidar com comando: {}", e.getMessage());
        }
    }
}
