package telegram.teste.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Component
public class DefesaCivilMonitor {

    private static final Logger logger = LoggerFactory.getLogger(DefesaCivilMonitor.class);

    @Autowired
    private TelegramService telegramService;

    @Value("${telegram.chat.id}")
    private String destinatario;

    private final RestTemplate restTemplate = new RestTemplate();

    private static final String DEFESA_CIVIL_URL =
            "https://www.defesacivil.rs.gov.br/avisos-e-boletins";

    private String ultimoAviso = "";

    /**
     * Verifica a cada 10 minutos se há novos alertas na Defesa Civil RS.
     */
    @Scheduled(fixedRate = 600000) // 10 minutos
    public void verificarAlertas() {
        try {
            String avisoAtual = extrairAviso();

            if (!avisoAtual.isEmpty() && !avisoAtual.equals(ultimoAviso)) {
                ultimoAviso = avisoAtual;
                telegramService.sendMessage("⚠️ Alerta da Defesa Civil RS:\n" + avisoAtual, destinatario);
                logger.info("Novo alerta enviado ao Telegram: {}", avisoAtual);
            } else {
                logger.info("Nenhum alerta novo encontrado.");
            }

        } catch (Exception e) {
            logger.error("Erro ao consultar Defesa Civil RS", e);
        }
    }

    /**
     * Método público para buscar aviso atual sem agendamento.
     */
    public String verificarAgora() {
        return extrairAviso();
    }

    /**
     * Extrai o aviso mais recente usando Jsoup, com fallback para RestTemplate.
     */
   private String extrairAviso() {
    try {
        Document doc = Jsoup.connect(DEFESA_CIVIL_URL)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .timeout(15000)
                .get();

        // Seleciona o primeiro título/link da lista de avisos/boletins
        Element aviso = doc.selectFirst(".view-content .views-row .field-content a");
        if (aviso != null) {
            return aviso.text();
        }

        // fallback: tenta pegar parágrafo dentro da lista
        aviso = doc.selectFirst(".view-content .views-row .field-content p");
        if (aviso != null) {
            return aviso.text();
        }

    } catch (Exception e) {
        logger.error("Erro ao parsear HTML", e);
    }

    return "";
}

}
