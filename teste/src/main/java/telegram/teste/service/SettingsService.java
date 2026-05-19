package telegram.teste.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SettingsService {
    private static final Logger logger = LoggerFactory.getLogger(SettingsService.class);
    private final Path CONFIG_PATH = Paths.get(".env");

    public Map<String, String> readConfig() {
        Map<String, String> map = new HashMap<>();
        if (Files.exists(CONFIG_PATH)) {
            try {
                List<String> lines = Files.readAllLines(CONFIG_PATH, StandardCharsets.UTF_8);
                for (String l : lines) {
                    String line = l.trim();
                    if (line.isEmpty() || line.startsWith("#") || !line.contains("=")) continue;
                    String[] pts = line.split("=", 2);
                    map.put(pts[0].trim(), pts[1].trim());
                }
            } catch (IOException e) {
                logger.error("Não foi possível ler arquivo de configuração: {}", e.getMessage());
            }
        }
        // Compatibilidade: se variáveis em estilo SHELL estiverem presentes, mapeie para as chaves usadas pela aplicação
        if (map.containsKey("TELEGRAM_BOT_TOKEN") && !map.containsKey("telegram.bot.token")) {
            map.put("telegram.bot.token", map.get("TELEGRAM_BOT_TOKEN"));
        }
        if (map.containsKey("TELEGRAM_CHAT_ID") && !map.containsKey("telegram.chat.id")) {
            map.put("telegram.chat.id", map.get("TELEGRAM_CHAT_ID"));
        }
        return map;
    }

    public void saveConfig(String token, String chatId) {
        try {
            StringBuilder sb = new StringBuilder();
            if (token != null) sb.append("telegram.bot.token=").append(token).append("\n");
            if (chatId != null) sb.append("telegram.chat.id=").append(chatId).append("\n");
            Files.writeString(CONFIG_PATH, sb.toString(), StandardCharsets.UTF_8);
            logger.info("Configurações salvas em {}", CONFIG_PATH.toAbsolutePath());
        } catch (IOException e) {
            logger.error("Falha ao salvar config: {}", e.getMessage());
        }
    }

    public void saveSettings(Map<String, String> updates) {
        try {
            Map<String, String> cfg = readConfig();
            cfg.putAll(updates);
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, String> e : cfg.entrySet()) {
                sb.append(e.getKey()).append("=").append(e.getValue()).append("\n");
            }
            Files.writeString(CONFIG_PATH, sb.toString(), StandardCharsets.UTF_8);
            logger.info("Configurações atualizadas em {}", CONFIG_PATH.toAbsolutePath());
        } catch (IOException e) {
            logger.error("Falha ao salvar configurações: {}", e.getMessage());
        }
    }
}
