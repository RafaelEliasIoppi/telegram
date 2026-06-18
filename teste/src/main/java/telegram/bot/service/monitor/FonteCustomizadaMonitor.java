package telegram.bot.service.monitor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;
import telegram.bot.domain.Alerta;
import telegram.bot.domain.FonteCustomizada;
import telegram.bot.repository.FonteCustomizadaRepository;

/**
 * Monitor genérico para fontes cadastradas pelo usuário via UI
 * (entidade {@link FonteCustomizada}).
 *
 * <p>Para cada fonte ativa: baixa o conteúdo da URL com Jsoup, extrai
 * candidatos (parágrafos/headings/items de RSS), e gera alertas apenas
 * para itens que contenham alguma das palavras-chave configuradas
 * (busca lowercase, sem acento). Em caso de falha de rede, registra
 * log e segue para a próxima fonte — nunca lança.</p>
 */
@Component
@Slf4j
public class FonteCustomizadaMonitor implements FonteMonitor {

    private static final int TIMEOUT_MS = 12_000;
    private static final int MAX_ITENS_POR_FONTE = 10;

    @Autowired(required = false)
    private FonteCustomizadaRepository repo;

    /** Hashes (codigo+titulo) já vistos nesta JVM para evitar reprocesso curto. */
    private final Set<String> seen = Collections.synchronizedSet(new HashSet<>());

    @Override
    public String getNome() {
        return "FONTE_CUSTOMIZADA";
    }

    @Override
    public String getDescricao() {
        return "Fontes Customizadas (URL/RSS)";
    }

    @Override
    public boolean isAtivo() {
        if (repo == null) return false;
        try {
            return !repo.findByAtivoTrue().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public List<Alerta> verificar() {
        List<Alerta> alertas = new ArrayList<>();
        if (repo == null) return alertas;

        List<FonteCustomizada> fontes;
        try {
            fontes = repo.findByAtivoTrue();
        } catch (Exception e) {
            log.warn("FonteCustomizada: falha ao ler fontes do DB: {}", e.getMessage());
            return alertas;
        }

        for (FonteCustomizada f : fontes) {
            try {
                alertas.addAll(processarFonte(f));
            } catch (Exception e) {
                log.warn("FonteCustomizada[{}]: falha — {}", f.getCodigo(), e.getMessage());
            }
        }

        return alertas;
    }

    private List<Alerta> processarFonte(FonteCustomizada f) {
        List<Alerta> out = new ArrayList<>();
        if (f.getUrl() == null || f.getUrl().isBlank()) return out;

        boolean isRss = "RSS".equalsIgnoreCase(f.getTipo())
                || f.getUrl().toLowerCase().endsWith(".xml")
                || f.getUrl().toLowerCase().contains("/rss");

        Document doc;
        try {
            var conn = Jsoup.connect(f.getUrl())
                    .timeout(TIMEOUT_MS)
                    .userAgent("Mozilla/5.0 BotHub")
                    .ignoreContentType(true);
            if (isRss) {
                conn.parser(Parser.xmlParser());
            }
            doc = conn.get();
        } catch (Exception e) {
            log.warn("FonteCustomizada[{}]: GET {} falhou — {}", f.getCodigo(), f.getUrl(), e.getMessage());
            return out;
        }

        Elements candidatos;
        if (isRss) {
            candidatos = doc.select("item, entry");
        } else if (f.getSeletor() != null && !f.getSeletor().isBlank()) {
            candidatos = doc.select(f.getSeletor());
        } else {
            candidatos = doc.select("h1, h2, h3, .titulo, .headline, p");
        }

        String imagemPagina = isRss ? null : ImagemExtractor.extrairDeHtml(doc, f.getUrl());

        List<String> palavras = f.palavrasChaveList();
        int gerados = 0;
        for (Element el : candidatos) {
            if (gerados >= MAX_ITENS_POR_FONTE) break;

            String titulo = isRss ? textoDe(el, "title") : el.text();
            String conteudo = isRss
                    ? coalesce(textoDe(el, "description"), textoDe(el, "summary"), titulo)
                    : el.text();

            if (titulo == null || titulo.isBlank()) continue;

            if (!palavras.isEmpty()) {
                String tn = titulo.toLowerCase();
                String cn = (conteudo == null ? "" : conteudo).toLowerCase();
                boolean casa = palavras.stream().anyMatch(p -> tn.contains(p) || cn.contains(p));
                if (!casa) continue;
            }

            String hashSeen = f.getCodigo() + "::" + titulo.trim();
            if (!seen.add(hashSeen)) continue;

            String imagem = isRss ? ImagemExtractor.extrairDeItemRss(el) : imagemPagina;

            Alerta a = Alerta.builder()
                    .titulo(truncar(titulo.trim(), 200))
                    .conteudo(truncar((conteudo == null ? titulo : conteudo).trim(), 1200))
                    .fonte(f.getCodigo())
                    .nivel(f.getNivel())
                    .imagemUrl(imagem)
                    .dataHora(LocalDateTime.now())
                    .enviado(false)
                    .build();
            out.add(a);
            gerados++;
        }

        if (gerados > 0) {
            log.info("FonteCustomizada[{}]: {} novo(s) alerta(s).", f.getCodigo(), gerados);
        }
        return out;
    }

    private String textoDe(Element el, String tag) {
        Elements sel = el.select(tag);
        return sel.isEmpty() ? null : sel.first().text();
    }

    private String coalesce(String... vals) {
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private String truncar(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 3) + "...";
    }
}
