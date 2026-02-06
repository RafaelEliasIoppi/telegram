package telegram.teste.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DefesaCivilMonitor {

    private static final Logger logger = LoggerFactory.getLogger(DefesaCivilMonitor.class);

    @Autowired
    private TelegramService telegramService;

    @Value("${telegram.chat.id}")
    private String destinatario;

    private static final String DEFESA_CIVIL_URL = "https://www.defesacivil.rs.gov.br/avisos-e-boletins";

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
                telegramService.sendMessage("ℹ️ Nenhum alerta novo encontrado na Defesa Civil RS.", destinatario);
                return "";
            }

        } catch (Exception e) {
            logger.error("Erro ao consultar Defesa Civil RS", e);
            telegramService.sendMessage("❌ Erro ao consultar Defesa Civil RS: " + e.getMessage(), destinatario);
            return "";
        }
    }

    /**
     * Verificação periódica (pode ser agendada).
     */
    public void verificarAlertas() {
        logger.info("Verificando alertas da Defesa Civil RS (agendamento automático)...");
        verificarAgora();
    }

    /**
     * Extrai o aviso mais recente usando Jsoup, com fallback HttpClient.
     */
    private String extrairAviso() {
        try {
            // Primeira tentativa com Jsoup direto
            Document doc = Jsoup.connect(DEFESA_CIVIL_URL)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .header("Accept-Language", "pt-BR,pt;q=0.9")
                    .timeout(15000)
                    .ignoreHttpErrors(true)
                    .get();

            return parseAviso(doc);

        } catch (Exception e) {
            logger.warn("Falha no Jsoup direto, tentando fallback com HttpClient...", e);

            try {
                HttpClient client = HttpClient.newBuilder()
                        .followRedirects(HttpClient.Redirect.ALWAYS)
                        .build();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(DEFESA_CIVIL_URL))
                        .header("User-Agent", "Mozilla/5.0")
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    Document doc = Jsoup.parse(response.body());
                    return parseAviso(doc);
                }
            } catch (Exception ex) {
                logger.error("Erro ao consultar Defesa Civil RS via HttpClient", ex);
            }
        }

        return "";
    }

    /**
     * Método auxiliar para extrair aviso do Document com busca recursiva.
     */
    private String parseAviso(Document doc) {
        // tenta pegar links
        Element aviso = doc.selectFirst(".view-content .views-row .field-content a");
        if (aviso != null && !aviso.text().isBlank()) {
            return aviso.text();
        }

        // tenta pegar parágrafos
        aviso = doc.selectFirst(".view-content .views-row .field-content p");
        if (aviso != null && !aviso.text().isBlank()) {
            return aviso.text();
        }

        // fallback: busca recursiva no bloco principal
        Element raiz = doc.selectFirst(".view-content .views-row");
        if (raiz != null) {
            String texto = buscarAvisoRecursivo(raiz);
            if (!texto.isBlank()) {
                return texto;
            }
        }

        return "";
    }

    /**
     * Busca recursiva para encontrar o primeiro texto relevante.
     */
    private String buscarAvisoRecursivo(Element elemento) {
        if (elemento == null) return "";

        String texto = elemento.ownText();
        if (texto != null && !texto.isBlank()) {
            return texto.trim();
        }

        for (Element filho : elemento.children()) {
            String resultado = buscarAvisoRecursivo(filho);
            if (!resultado.isBlank()) {
                return resultado;
            }
        }

        return "";
    }

    public void buscarAvisos() {
        verificarAgora();
    }
}
