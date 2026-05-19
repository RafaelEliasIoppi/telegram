package telegram.teste.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class DefesaCivilMonitor {
    private static final Logger logger = LoggerFactory.getLogger(DefesaCivilMonitor.class);

    @Autowired
    private TelegramService telegramService;

    @Autowired
    private SettingsService settingsService;

    private final Path LAST_PATH = Paths.get("teste/defesacivil_last.txt");

    // Execução agendada a cada 10 minutos por padrão
    @Scheduled(fixedRateString = "${defesacivil.fixedRate:600000}")
    public void verificarAlertas() {
        logger.info("Iniciando verificação periódica da Defesa Civil...");
        try {
            String msg = coletarConteudoSite();
            if (msg == null || msg.isBlank()) {
                logger.info("Nenhum conteúdo novo encontrado.");
                return;
            }

            String anterior = readLast();
            if (!msg.equals(anterior)) {
                logger.info("Novo alerta detectado — enviando mensagem.");
                telegramService.sendMessage("⚠️ *Alerta Defesa Civil*\n\n" + msg, null);
                writeLast(msg);
            } else {
                logger.info("Sem mudanças desde a última verificação.");
            }
        } catch (Exception e) {
            logger.error("Erro ao verificar Defesa Civil: {}", e.getMessage());
        }
    }

    // Método público para execução manual imediata
    public void verificarAgora() {
        verificarAlertas();
    }

    private String coletarConteudoSite() {
        try {
            String url = settingsService.readConfig().getOrDefault("defesacivil.url", "https://www.defesacivil.rs.gov.br/");
            Document doc = Jsoup.connect(url).userAgent("Mozilla/5.0").timeout(15000).get();
            String texto = doc.body().text();
            // Limita o tamanho da mensagem para evitar payloads muito grandes
            if (texto.length() > 800) texto = texto.substring(0, 800) + "...";
            return texto.trim();
        } catch (IOException e) {
            logger.error("Falha ao conectar no site da Defesa Civil: {}", e.getMessage());
            return null;
        }
    }

    private String readLast() {
        try {
            if (Files.exists(LAST_PATH)) return Files.readString(LAST_PATH, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            logger.error("Não foi possível ler arquivo de histórico Defesa Civil: {}", e.getMessage());
        }
        return "";
    }

    private void writeLast(String txt) {
        try {
            Files.createDirectories(LAST_PATH.getParent());
            Files.writeString(LAST_PATH, txt, StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Erro ao salvar histórico Defesa Civil: {}", e.getMessage());
        }
    }
}
