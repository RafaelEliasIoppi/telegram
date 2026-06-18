package telegram.bot.service.monitor;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;
import telegram.bot.domain.Alerta;

/**
 * Monitor para alertas meteorológicos do INMET (Instituto Nacional de Meteorologia).
 *
 * <p>Faz scraping da página pública de alertas em
 * {@code https://alertas2.inmet.gov.br/}. Como o layout do INMET muda com
 * frequência, são testados vários seletores e os textos são filtrados por
 * presença de palavras meteorológicas ou nomes/UFs de estados brasileiros.</p>
 *
 * <p>Em caso de site indisponível, retorna lista vazia com log de warning.</p>
 */
@Service
@Slf4j
public class InmetMonitor implements FonteMonitor {

    private static final String FONTE = "INMET";
    private static final String DESCRICAO = "Instituto Nacional de Meteorologia";

    private static final String URL_BASE = "https://alertas2.inmet.gov.br/";
    private static final int TIMEOUT_MS = 15000;
    private static final String USER_AGENT = "Mozilla/5.0";

    private static final List<String> SELETORES = List.of(
            ".alert", ".alerta", "table tr",
            ".card-title", "h2", "h3"
    );

    private static final List<String> PALAVRAS_METEO = List.of(
            "chuva", "tempestade", "vendaval", "granizo", "tornado",
            "ciclone", "frente fria", "calor", "frio", "geada",
            "ressaca", "maré", "temporal", "rajada", "trovoada",
            "alagamento", "enchente", "deslizamento"
    );

    // Lista resumida de UFs e principais estados para filtro
    private static final List<String> UF_OU_ESTADO = List.of(
            "AC", "AL", "AP", "AM", "BA", "CE", "DF", "ES", "GO",
            "MA", "MT", "MS", "MG", "PA", "PB", "PR", "PE", "PI",
            "RJ", "RN", "RS", "RO", "RR", "SC", "SP", "SE", "TO",
            "rio grande do sul", "santa catarina", "paraná", "parana",
            "são paulo", "sao paulo", "rio de janeiro", "minas gerais",
            "bahia", "pernambuco", "ceará", "ceara", "amazonas", "pará", "para"
    );

    private static final List<String> NIVEL_CRITICO = List.of("vermelho", "perigo extremo", "extremo");
    private static final List<String> NIVEL_AVISO = List.of("laranja", "perigo");
    private static final List<String> NIVEL_INFO = List.of("amarelo", "atenção", "atencao");

    @Value("${inmet.enabled:true}")
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
        List<Alerta> alertas = new ArrayList<>();
        Document doc;
        try {
            doc = Jsoup.connect(URL_BASE)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .get();
        } catch (IOException e) {
            log.warn("INMET indisponível em {}: {}", URL_BASE, e.getMessage());
            return alertas;
        } catch (Exception e) {
            log.warn("Erro inesperado ao acessar INMET: {}", e.getMessage());
            return alertas;
        }

        Set<String> textosUnicos = new LinkedHashSet<>();
        for (String seletor : SELETORES) {
            for (Element el : doc.select(seletor)) {
                String texto = el.text();
                if (texto == null) {
                    continue;
                }
                texto = texto.trim();
                if (texto.length() < 20 || textosUnicos.contains(texto)) {
                    continue;
                }
                if (textoRelevante(texto)) {
                    textosUnicos.add(texto);
                }
                if (textosUnicos.size() >= 10) {
                    break;
                }
            }
            if (textosUnicos.size() >= 10) {
                break;
            }
        }

        String imagemPagina = ImagemExtractor.extrairDeHtml(doc, URL_BASE);
        for (String texto : textosUnicos) {
            Alerta a = montarAlerta(texto);
            if (imagemPagina != null) a.setImagemUrl(imagemPagina);
            alertas.add(a);
        }

        if (alertas.isEmpty()) {
            log.info("INMET: nenhum alerta relevante encontrado em {}.", URL_BASE);
        }
        return alertas;
    }

    private boolean textoRelevante(String texto) {
        String lower = texto.toLowerCase();
        if (PALAVRAS_METEO.stream().anyMatch(lower::contains)) {
            return true;
        }
        // Verifica UF: usa busca por token (palavra inteira) para evitar falsos positivos com 2 letras
        for (String token : UF_OU_ESTADO) {
            if (token.length() == 2) {
                // Sigla — checa cercada por separador ou limite
                String padrao = "(^|[^a-zA-Z])" + token + "([^a-zA-Z]|$)";
                if (texto.matches(".*" + padrao + ".*")) {
                    return true;
                }
            } else if (lower.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private Alerta montarAlerta(String texto) {
        String limpo = texto.trim();
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
     * Classifica em INFO / AVISO / CRITICO segundo cor/severidade INMET.
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
        if (NIVEL_INFO.stream().anyMatch(lower::contains)) {
            return "INFO";
        }
        return "INFO";
    }
}
