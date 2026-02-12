package telegram.teste.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DefesaCivilService {

    @Autowired
    private TelegramService telegramService;

    @Value("${telegram.chat.id}")
    private String destinatario;

    private String ultimoAviso = "";

    /**
     * Busca avisos atuais no site da Defesa Civil RS.
     */
    public List<Map<String, String>> buscarAvisos() {
        List<Map<String, String>> avisos = new ArrayList<>();
        try {
            Document doc = Jsoup.connect("https://www.defesacivil.rs.gov.br/").get();

            // Seleciona os blocos de avisos (ajustado conforme estrutura real da página)
            for (Element aviso : doc.select("div.views-row, div.node__content")) {
                Map<String, String> dados = new HashMap<>();
                dados.put("titulo", aviso.text());
                avisos.add(dados);
            }
        } catch (Exception e) {
            Map<String, String> erro = new HashMap<>();
            erro.put("erro", e.getMessage());
            avisos.add(erro);
        }
        return avisos;
    }

    /**
     * Verifica se há novos avisos e envia alerta no Telegram.
     */
    public String verificarNovosAvisos() {
        try {
            List<Map<String, String>> avisos = buscarAvisos();
            if (!avisos.isEmpty()) {
                String titulo = avisos.get(0).get("titulo");
                if (!titulo.equals(ultimoAviso)) {
                    ultimoAviso = titulo;
                    telegramService.sendMessage("⚠️ Novo aviso da Defesa Civil: " + titulo, destinatario);
                    return "Novo aviso enviado ao Telegram.";
                } else {
                    return "Nenhum aviso novo.";
                }
            } else {
                return "Nenhum aviso encontrado.";
            }
        } catch (Exception e) {
            telegramService.sendMessage("❌ Erro ao consultar Defesa Civil: " + e.getMessage(), destinatario);
            return "Erro ao consultar Defesa Civil.";
        }
    }
}
