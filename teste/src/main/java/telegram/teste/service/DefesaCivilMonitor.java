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

    // Retorna uma lista de candidatos encontrados na página (não grava/nenhuma ação)
    public java.util.List<String> listarCandidatos(int limit) {
        java.util.List<String> out = new java.util.ArrayList<>();
        try {
            String url = settingsService.readConfig().getOrDefault("defesacivil.url", "https://www.defesacivil.rs.gov.br/");
            Document doc = Jsoup.connect(url).userAgent("Mozilla/5.0").timeout(15000).get();

            // 1) tentar feed
            String feedUrl = findRssLink(doc, url);
            if (feedUrl != null) {
                try {
                    Document feed = Jsoup.connect(feedUrl).userAgent("Mozilla/5.0").ignoreContentType(true).timeout(10000).get();
                    for (org.jsoup.nodes.Element item : feed.select("item, entry")) {
                        String title = item.selectFirst("title") != null ? item.selectFirst("title").text() : null;
                        String link = "";
                        if (item.selectFirst("link") != null) link = item.selectFirst("link").text();
                        if (link.isBlank() && item.selectFirst("link[href]") != null) link = item.selectFirst("link[href]").attr("href");
                        String v = (title == null ? "(sem título)" : title) + (link.isBlank() ? "" : "\n" + link);
                        out.add(v);
                        if (out.size() >= limit) return out;
                    }
                } catch (Exception e) {
                    // ignora
                }
            }

            // 2) headlines
            for (int i = 1; i <= 3 && out.size() < limit; i++) {
                for (org.jsoup.nodes.Element h : doc.select("h" + i)) {
                    String cand = extractTitleAndLink(h);
                    if (cand != null) {
                        out.add(cand);
                        if (out.size() >= limit) return out;
                    }
                }
            }

            // 3) keywords
            String kwConf = settingsService.readConfig().getOrDefault("defesacivil.keywords", "alerta,aviso,atenção,emergência,bol");
            String[] keywords = kwConf.split("\\s*,\\s*");
            for (String kw : keywords) {
                org.jsoup.select.Elements matches = doc.getElementsContainingOwnText(kw);
                for (org.jsoup.nodes.Element el : matches) {
                    String cand = extractTitleAndLink(el);
                    if (cand != null) {
                        if (!out.contains(cand)) out.add(cand);
                        if (out.size() >= limit) return out;
                    }
                }
            }

            // 4) fallback: some paragraphs
            for (org.jsoup.nodes.Element p : doc.select("p")) {
                String txt = p.text();
                if (txt != null && txt.length() > 40) {
                    String snippet = txt.length() > 300 ? txt.substring(0, 300) + "..." : txt;
                    out.add(snippet);
                    if (out.size() >= limit) return out;
                }
            }

        } catch (Exception e) {
            logger.debug("Erro listarCandidatos: {}", e.getMessage());
        }
        return out;
    }

    public void salvarAlerta(String txt) {
        writeLast(txt);
    }

    private String coletarConteudoSite() {
        try {
            String url = settingsService.readConfig().getOrDefault("defesacivil.url", "https://www.defesacivil.rs.gov.br/");
            Document doc = Jsoup.connect(url).userAgent("Mozilla/5.0").timeout(15000).get();

            // 1) tentar RSS/Atom
            String feedUrl = findRssLink(doc, url);
            if (feedUrl != null) {
                String fromFeed = parseFirstFeedItem(feedUrl);
                if (fromFeed != null) return fromFeed;
            }

            // 2) procurar por elementos que contenham palavras-chave (configuráveis)
            String kwConf = settingsService.readConfig().getOrDefault("defesacivil.keywords", "alerta,aviso,atenção,emergência,bol");
            String[] keywords = kwConf.split("\\s*,\\s*");
            for (String kw : keywords) {
                org.jsoup.select.Elements matches = doc.getElementsContainingOwnText(kw);
                for (org.jsoup.nodes.Element el : matches) {
                    String cand = extractTitleAndLink(el);
                    if (cand != null) return cand;
                }
            }

            // 3) procurar por headlines (h1..h3) com links
            for (int i = 1; i <= 3; i++) {
                for (org.jsoup.nodes.Element h : doc.select("h" + i)) {
                    String cand = extractTitleAndLink(h);
                    if (cand != null) return cand;
                }
            }

            // 4) fallback: resumo do corpo
            String texto = doc.body().text();
            if (texto.length() > 800) texto = texto.substring(0, 800) + "...";
            return texto.trim();
        } catch (IOException e) {
            logger.error("Falha ao conectar no site da Defesa Civil: {}", e.getMessage());
            return null;
        }
    }

    private String findRssLink(Document doc, String baseUrl) {
        try {
            org.jsoup.select.Elements links = doc.select("link[type=application/rss+xml], link[type=application/atom+xml]");
            if (!links.isEmpty()) {
                String href = links.first().attr("abs:href");
                if (!href.isBlank()) return href;
            }
            for (org.jsoup.nodes.Element a : doc.select("a[href]")) {
                String href = a.attr("href");
                if (href.toLowerCase().contains("rss") || href.toLowerCase().contains("feed") || href.toLowerCase().contains("atom")) {
                    return a.attr("abs:href");
                }
            }
            if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length()-1);
            String[] candidates = new String[] {baseUrl + "/feed", baseUrl + "/rss", baseUrl + "/rss.xml"};
            for (String c : candidates) {
                try {
                    org.jsoup.Connection.Response resp = Jsoup.connect(c).ignoreContentType(true).userAgent("Mozilla/5.0").timeout(8000).execute();
                    if (resp.contentType() != null && resp.contentType().toLowerCase().contains("xml")) return c;
                } catch (Exception ex) {
                    // ignora
                }
            }
        } catch (Exception e) {
            // ignora
        }
        return null;
    }

    private String parseFirstFeedItem(String feedUrl) {
        try {
            Document feed = Jsoup.connect(feedUrl).userAgent("Mozilla/5.0").ignoreContentType(true).timeout(10000).get();
            org.jsoup.nodes.Element item = feed.selectFirst("item, entry");
            if (item != null) {
                String title = item.selectFirst("title") != null ? item.selectFirst("title").text() : "(sem título)";
                String link = "";
                if (item.selectFirst("link") != null) link = item.selectFirst("link").text();
                if (link.isBlank() && item.selectFirst("link[href]") != null) link = item.selectFirst("link[href]").attr("href");
                String pub = item.selectFirst("pubDate") != null ? item.selectFirst("pubDate").text() : "";
                String out = title + (link.isBlank() ? "" : "\n" + link) + (pub.isBlank() ? "" : "\n" + pub);
                return out;
            }
        } catch (Exception e) {
            logger.debug("Falha ao parsear feed {}: {}", feedUrl, e.getMessage());
        }
        return null;
    }

    private String extractTitleAndLink(org.jsoup.nodes.Element el) {
        if (el == null) return null;
        String title = el.ownText();
        if (title == null || title.isBlank()) title = el.text();
        org.jsoup.nodes.Element link = el.selectFirst("a[href]");
        if (link == null) link = el.parent() != null ? el.parent().selectFirst("a[href]") : null;
        String href = null;
        if (link != null) href = link.attr("abs:href");
        if ((title != null && title.length() > 5) || (href != null && !href.isBlank())) {
            String out = "";
            if (title != null && !title.isBlank()) out += title.trim();
            if (href != null && !href.isBlank()) out += "\n" + href;
            return out.trim();
        }
        org.jsoup.nodes.Element p = el.parent();
        int depth = 0;
        while (p != null && depth++ < 6) {
            org.jsoup.nodes.Element pLink = p.selectFirst("a[href]");
            if (pLink != null) {
                String t = p.text();
                String h = pLink.attr("abs:href");
                if ((t != null && t.length() > 5) || (h != null && !h.isBlank())) return (t == null ? "" : t) + (h == null ? "" : "\n" + h);
            }
            p = p.parent();
        }
        return null;
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
