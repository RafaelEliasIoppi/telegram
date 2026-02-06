package telegram.teste.service;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
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
     * Método público para buscar aviso atual sem agendamento.
     * Pode ser chamado manualmente ou via outro mecanismo externo (ex: GitHub Actions).
     * @return O último aviso encontrado ou string vazia.
     */
    public String verificarAgora() {
        try {
            String avisoAtual = extrairAviso();

            if (!avisoAtual.isEmpty() && !avisoAtual.equals(ultimoAviso)) {
                ultimoAviso = avisoAtual;
                telegramService.sendMessage("⚠️ Alerta da Defesa Civil RS:\n" + avisoAtual, destinatario);
                logger.info("Novo alerta enviado ao Telegram: {}", avisoAtual);
                return avisoAtual;
            } else {
                logger.info("Nenhum alerta novo encontrado.");
                return "";
            }

        } catch (Exception e) {
            logger.error("Erro ao consultar Defesa Civil RS", e);
            return "";
        }
    }

    /**
     * Verifica alertas automaticamente a cada 10 minutos.
     */
       public void verificarAlertas() {
        logger.info("Verificando alertas da Defesa Civil RS (agendamento automático)...");
        verificarAgora();
    }
    /**
     * Extrai o aviso mais recente usando Jsoup.
     */
   private String extrairAviso() {
    try {
        // Primeira tentativa com Jsoup direto
        Document doc = Jsoup.connect(DEFESA_CIVIL_URL)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Accept-Language", "pt-BR,pt;q=0.9")
                .timeout(10000) // 10 segundos
                .get();

        return parseAviso(doc);

    } catch (Exception e) {
        logger.warn("Falha no Jsoup direto, tentando fallback com RestTemplate...", e);

        try {
            // Fallback: baixa HTML cru com RestTemplate
            String html = restTemplate.getForObject(DEFESA_CIVIL_URL, String.class);
            if (html != null) {
                Document doc = Jsoup.parse(html);
                return parseAviso(doc);
            }
        } catch (Exception ex) {
            logger.error("Erro ao consultar Defesa Civil RS via RestTemplate", ex);
        }
    }

    return "";
}

/**
 * Método auxiliar para extrair aviso do Document.
 */
private String parseAviso(Document doc) {
    Element aviso = doc.selectFirst(".view-content .views-row .field-content a");
    if (aviso != null) {
        return aviso.text();
    }

    aviso = doc.selectFirst(".view-content .views-row .field-content p");
    if (aviso != null) {
        return aviso.text();
    }

    return "";
}
public void buscarAvisos() {
    verificarAgora();
}

}
