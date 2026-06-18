package telegram.bot.service.monitor;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import telegram.bot.domain.Alerta;

/**
 * Monitor para o portal da Defesa Civil do Rio Grande do Sul.
 *
 * <p>A coleta segue uma estratégia em cascata:
 * <ol>
 *   <li>Tenta um feed RSS/Atom em {@code <url>/feed} ou {@code <url>/rss};</li>
 *   <li>Caso falhe, faz scraping de headlines (h1, h2, h3 e variantes);</li>
 *   <li>Se ainda houver poucos resultados, busca parágrafos contendo
 *       palavras-chave relacionadas a alertas/emergências.</li>
 * </ol>
 * </p>
 */
@Service
@Slf4j
public class DefesaCivilRSMonitor implements FonteMonitor {

    private static final String FONTE = "DEFESA_CIVIL_RS";
    private static final String DESCRICAO = "Defesa Civil do Estado do Rio Grande do Sul";

    private static final int TIMEOUT_MS = 15000;
    private static final String USER_AGENT = "Mozilla/5.0";

    private static final List<String> HEADLINE_SELECTORS = List.of(
            "h1", "h2", "h3",
            ".titulo", ".headline",
            "article h2", ".noticia-titulo"
    );

    private static final List<String> KEYWORDS = List.of(
            "alerta", "aviso", "emergência", "enchente",
            "chuva", "vendaval", "granizo", "alagamento", "risco"
    );

    private static final List<String> NIVEL_CRITICO = List.of("crítico", "critico", "emergência", "emergencia", "perigo");
    private static final List<String> NIVEL_AVISO = List.of("alerta", "aviso", "atenção", "atencao");

    @Value("${defesacivil.url:https://www.defesacivil.rs.gov.br/}")
    private String url;

    @Value("${defesacivil.enabled:true}")
    private boolean enabled;

    @Override
    public String getNome() {
        return FONTE;
    }

    @Override
    public String getDescricao() {
        return DESCRICAO;
    }

    @Override
    public boolean isAtivo() {
        return enabled;
    }

    @Override
    public List<Alerta> verificar() {
        List<String> textos = coletarViaRss();
        if (textos.isEmpty()) {
            textos = coletarHeadlines();
        }
        if (textos.size() < 2) {
            List<String> porKeyword = coletarPorKeywords();
            for (String t : porKeyword) {
                if (!textos.contains(t)) {
                    textos.add(t);
                }
            }
        }

        List<Alerta> alertas = new ArrayList<>();
        for (String texto : textos) {
            alertas.add(montarAlerta(texto));
        }
        return alertas;
    }

    /** Estratégia 1: RSS/Atom. */
    private List<String> coletarViaRss() {
        List<String> out = new ArrayList<>();
        String base = url == null ? "" : url;
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        String[] candidatos = {base + "/feed", base + "/rss"};

        for (String feedUrl : candidatos) {
            try {
                Document feed = Jsoup.connect(feedUrl)
                        .userAgent(USER_AGENT)
                        .timeout(TIMEOUT_MS)
                        .ignoreContentType(true)
                        .get();
                Elements itens = feed.select("item, entry");
                if (itens.isEmpty()) {
                    continue;
                }
                for (Element item : itens) {
                    String titulo = textoOuVazio(item.selectFirst("title"));
                    String descricao = textoOuVazio(item.selectFirst("description, summary, content"));
                    String combinado = (titulo + (descricao.isBlank() ? "" : "\n" + descricao)).trim();
                    if (combinado.length() > 20) {
                        out.add(combinado);
                    }
                    if (out.size() >= 5) {
                        return out;
                    }
                }
                if (!out.isEmpty()) {
                    return out;
                }
            } catch (IOException e) {
                log.debug("Feed indisponível em {}: {}", feedUrl, e.getMessage());
            } catch (Exception e) {
                log.debug("Erro ao parsear feed {}: {}", feedUrl, e.getMessage());
            }
        }
        return out;
    }

    /** Estratégia 2: headlines. */
    private List<String> coletarHeadlines() {
        List<String> out = new ArrayList<>();
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();

            Set<String> unicos = new LinkedHashSet<>();
            for (String seletor : HEADLINE_SELECTORS) {
                for (Element el : doc.select(seletor)) {
                    String txt = el.text();
                    if (txt != null) {
                        txt = txt.trim();
                        if (txt.length() > 20) {
                            unicos.add(txt);
                        }
                    }
                    if (unicos.size() >= 5) {
                        break;
                    }
                }
                if (unicos.size() >= 5) {
                    break;
                }
            }
            out.addAll(unicos);
        } catch (IOException e) {
            log.warn("Falha ao acessar {} para headlines: {}", url, e.getMessage());
        } catch (Exception e) {
            log.warn("Erro inesperado em coletarHeadlines: {}", e.getMessage());
        }
        return out;
    }

    /** Estratégia 3: parágrafos contendo palavras-chave. */
    private List<String> coletarPorKeywords() {
        List<String> out = new ArrayList<>();
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();

            for (Element p : doc.select("p")) {
                String txt = p.text();
                if (txt == null) {
                    continue;
                }
                String lower = txt.toLowerCase();
                boolean possui = KEYWORDS.stream().anyMatch(lower::contains);
                if (possui && txt.length() > 20 && !out.contains(txt)) {
                    out.add(txt.trim());
                    if (out.size() >= 3) {
                        break;
                    }
                }
            }
        } catch (IOException e) {
            log.warn("Falha ao acessar {} para keywords: {}", url, e.getMessage());
        } catch (Exception e) {
            log.warn("Erro inesperado em coletarPorKeywords: {}", e.getMessage());
        }
        return out;
    }

    private Alerta montarAlerta(String texto) {
        String limpo = texto == null ? "" : texto.trim();
        String titulo = limpo.length() > 100 ? limpo.substring(0, 100) : limpo;
        return Alerta.builder()
                .titulo(titulo)
                .conteudo(limpo)
                .fonte(FONTE)
                .nivel(detectarNivel(limpo))
                .dataHora(LocalDateTime.now())
                .enviado(false)
                .build();
    }

    /**
     * Classifica o texto em INFO / AVISO / CRITICO segundo palavras-chave.
     */
    private String detectarNivel(String texto) {
        if (texto == null) {
            return "INFO";
        }
        String lower = texto.toLowerCase();
        if (NIVEL_CRITICO.stream().anyMatch(lower::contains)) {
            return "CRITICO";
        }
        if (NIVEL_AVISO.stream().anyMatch(lower::contains)) {
            return "AVISO";
        }
        return "INFO";
    }

    private String textoOuVazio(Element el) {
        return el == null ? "" : (el.text() == null ? "" : el.text().trim());
    }

    /** Apenas para facilitar testes futuros — expõe palavras-chave conhecidas. */
    @SuppressWarnings("unused")
    private static List<String> keywordsConhecidas() {
        return Arrays.asList(KEYWORDS.toArray(new String[0]));
    }
}
