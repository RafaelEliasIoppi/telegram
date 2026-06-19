package telegram.bot.service.monitor;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import telegram.bot.domain.FiltroAssunto;
import telegram.bot.domain.GmailConfig;
import telegram.bot.repository.FiltroAssuntoRepository;
import telegram.bot.service.GmailConfigService;

import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.search.AndTerm;
import jakarta.mail.search.ComparisonTerm;
import jakarta.mail.search.FlagTerm;
import jakarta.mail.search.ReceivedDateTerm;
import jakarta.mail.search.SearchTerm;
import lombok.extern.slf4j.Slf4j;
import telegram.bot.domain.Alerta;

/**
 * Monitor de caixa Gmail (via IMAP) para e-mails com assunto contendo
 * a string configurada em {@code gmail.subject.filter} (default:
 * "Notificação de novo e-mail – Urgência Renal").
 *
 * <p>Estratégia:
 * <ol>
 *   <li>Conecta via IMAPS (SSL/993) usando senha de app do Gmail;</li>
 *   <li>Busca mensagens UNREAD recebidas nas últimas 24h em READ_ONLY
 *       (não marca como lida);</li>
 *   <li>Filtra em Java por assunto normalizado (sem diacríticos, lowercase)
 *       contendo o filtro configurado;</li>
 *   <li>Dedup em memória por {@code Message-ID} + dedup secundária via
 *       {@code AlertaService} (hash SHA-256).</li>
 * </ol>
 * </p>
 *
 * <p>Em caso de credenciais ausentes ou falha de IMAP, retorna lista vazia
 * e registra log; nunca lança exceção a partir de {@link #verificar()}.</p>
 */
@Component
@Slf4j
public class UrgenciaRenalGmailMonitor implements FonteMonitor {

    private static final String FONTE = "GMAIL_URGENCIA_RENAL";
    private static final String DESCRICAO = "Gmail — Urgência Renal";

    private static final long ONE_DAY_MS = 24L * 60L * 60L * 1000L;
    private static final int MAX_BODY_CHARS = 1500;
    /** Teto de mensagens varridas no fallback (busca IMAP indisponível). */
    private static final int MAX_FALLBACK_MENSAGENS = 200;
    /** Teto do set de dedup in-memory; ao exceder, o set é limpo. */
    private static final int MAX_SEEN_IDS = 5000;
    private static final Path STATE_FILE = Paths.get("ultimo_assunto.txt");

    private static final java.util.regex.Pattern REMETENTE_RE =
            java.util.regex.Pattern.compile("(?im)^\\s*Remetente\\s*:\\s*(.+)$");
    private static final java.util.regex.Pattern ASSUNTO_RE =
            java.util.regex.Pattern.compile("(?im)^\\s*Assunto\\s*:\\s*(.+)$");
    private static final java.util.regex.Pattern CAIXA_RE =
            java.util.regex.Pattern.compile("(?im)caixa de entrada da\\s+(.+?)\\s*:");
    private static final java.util.regex.Pattern UNSUBSCRIBE_RE =
            java.util.regex.Pattern.compile("(?is)\\s*If you want to unsubscribe.*$");

    @Value("${gmail.subject.filter:Notificação de novo e-mail – Urgência Renal}")
    private String subjectFilter;

    @Autowired(required = false)
    private FiltroAssuntoRepository filtroRepo;

    @Autowired(required = false)
    private GmailConfigService gmailConfigService;

    /**
     * Conjunto de Message-IDs já vistos nesta JVM. Funciona como guarda
     * primária contra reprocessamento; a guarda secundária é o hash do
     * AlertaService.
     */
    private final Set<String> seenMessageIds = Collections.synchronizedSet(new java.util.HashSet<>());

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
        GmailConfig cfg = gmailConfigService != null ? gmailConfigService.getConfig() : null;
        if (cfg != null) return cfg.isEnabled();
        return false;
    }

    @Override
    public List<Alerta> verificar() {
        List<Alerta> alertas = new ArrayList<>();

        GmailConfig cfg = gmailConfigService != null ? gmailConfigService.getConfig() : null;

        String user = cfg != null ? cfg.getUser() : null;
        String appPassword = cfg != null ? cfg.getAppPassword() : null;
        String imapHost = cfg != null ? cfg.getImapHost() : "imap.gmail.com";
        int imapPort = cfg != null ? cfg.getImapPort() : 993;

        if (user == null || user.isBlank() || appPassword == null || appPassword.isBlank()) {
            log.warn("Gmail monitor: credenciais ausentes; configure em /gmail ou via env vars.");
            return alertas;
        }

        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.host", imapHost);
        props.put("mail.imaps.port", String.valueOf(imapPort));
        props.put("mail.imaps.ssl.enable", "true");
        props.put("mail.imaps.ssl.trust", imapHost);
        // Timeouts IMAP: sem eles a conexão pode travar indefinidamente e
        // congelar o scheduler. connect/leitura/escrita em milissegundos.
        props.put("mail.imaps.connectiontimeout", "15000");
        props.put("mail.imaps.timeout", "20000");
        props.put("mail.imaps.writetimeout", "20000");

        Session session = Session.getInstance(props);

        Store store = null;
        Folder inbox = null;
        try {
            store = session.getStore("imaps");
            store.connect(imapHost, imapPort, user, appPassword);

            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);

            Date since = new Date(System.currentTimeMillis() - ONE_DAY_MS);
            SearchTerm unread = new FlagTerm(new jakarta.mail.Flags(jakarta.mail.Flags.Flag.SEEN), false);
            SearchTerm recent = new ReceivedDateTerm(ComparisonTerm.GE, since);
            SearchTerm criteria = new AndTerm(unread, recent);

            Message[] mensagens;
            try {
                mensagens = inbox.search(criteria);
            } catch (Exception e) {
                log.warn("Gmail monitor: busca IMAP falhou ({}); usando varredura limitada", e.getMessage());
                // Varredura completa pode ser muito custosa em caixas grandes;
                // limita às últimas ~200 mensagens calculando o range pelo total.
                int total = inbox.getMessageCount();
                int start = Math.max(1, total - MAX_FALLBACK_MENSAGENS + 1);
                mensagens = total > 0 ? inbox.getMessages(start, total) : new Message[0];
            }

            List<String> filtrosNormalizados = carregarFiltrosAtivos();
            if (filtrosNormalizados.isEmpty()) {
                log.info("Gmail monitor: nenhum filtro de assunto configurado; pulando ciclo.");
                return alertas;
            }
            String ultimoAssunto = null;

            for (Message msg : mensagens) {
                try {
                    String subject = msg.getSubject();
                    if (subject == null || subject.isBlank()) {
                        continue;
                    }
                    String subjectNorm = normalizar(subject);
                    boolean casa = false;
                    for (String f : filtrosNormalizados) {
                        if (subjectNorm.contains(f)) { casa = true; break; }
                    }
                    if (!casa) {
                        continue;
                    }

                    String messageId = extrairMessageId(msg);
                    if (messageId != null) {
                        // Evita crescimento ilimitado do set em JVMs de vida longa:
                        // ao exceder o teto, descarta o histórico (a dedup secundária
                        // por hash do AlertaService continua protegendo contra repetição).
                        if (seenMessageIds.size() > MAX_SEEN_IDS) {
                            seenMessageIds.clear();
                        }
                        if (!seenMessageIds.add(messageId)) {
                            // já visto nesta JVM
                            continue;
                        }
                    }

                    String corpoBruto = extrairCorpo(msg);
                    String snippet = formatarParaTelegram(corpoBruto, subject);
                    if (snippet.length() > MAX_BODY_CHARS) {
                        snippet = snippet.substring(0, MAX_BODY_CHARS);
                    }

                    Alerta alerta = Alerta.builder()
                            .titulo(subject.trim())
                            .conteudo(snippet.trim())
                            .fonte(FONTE)
                            .nivel("CRITICO")
                            .dataHora(LocalDateTime.now())
                            .enviado(false)
                            .build();
                    alertas.add(alerta);
                    ultimoAssunto = subject.trim();
                } catch (Exception e) {
                    log.warn("Gmail monitor: falha processando mensagem: {}", e.getMessage());
                }
            }

            if (ultimoAssunto != null) {
                persistirUltimoAssunto(ultimoAssunto);
            }

            if (alertas.isEmpty()) {
                log.info("Gmail monitor: nenhum e-mail novo casando com os filtros ativos.");
            } else {
                log.info("Gmail monitor: {} novo(s) alerta(s) gerado(s).", alertas.size());
            }
        } catch (Exception e) {
            log.warn("Gmail monitor: falha IMAP em {}:{} — {}", imapHost, imapPort, e.getMessage());
        } finally {
            if (inbox != null && inbox.isOpen()) {
                try {
                    inbox.close(false);
                } catch (Exception e) {
                    log.debug("Gmail monitor: erro ao fechar INBOX: {}", e.getMessage());
                }
            }
            if (store != null && store.isConnected()) {
                try {
                    store.close();
                } catch (Exception e) {
                    log.debug("Gmail monitor: erro ao fechar Store: {}", e.getMessage());
                }
            }
        }

        return alertas;
    }

    /**
     * Carrega filtros ativos do banco; se o repositório não estiver
     * disponível ou estiver vazio, usa o env {@code gmail.subject.filter}
     * como fallback (preserva compat. para deploy sem DB).
     */
    private List<String> carregarFiltrosAtivos() {
        List<String> resultado = new ArrayList<>();
        if (filtroRepo != null) {
            try {
                for (FiltroAssunto f : filtroRepo.findByAtivoTrue()) {
                    if (f.getPadrao() != null && !f.getPadrao().isBlank()) {
                        resultado.add(normalizar(f.getPadrao()));
                    }
                }
            } catch (Exception e) {
                log.warn("Gmail monitor: falha ao ler filtros do DB ({}); usando env.", e.getMessage());
            }
        }
        if (resultado.isEmpty() && subjectFilter != null && !subjectFilter.isBlank()) {
            resultado.add(normalizar(subjectFilter));
        }
        return resultado;
    }

    /**
     * Normaliza texto removendo diacríticos e convertendo para lowercase
     * para tornar a comparação de assunto tolerante a acentos/caixa.
     */
    private String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String nfd = Normalizer.normalize(texto, Normalizer.Form.NFD);
        return nfd.replaceAll("\\p{InCombiningDiacriticalMarks}+", "")
                .toLowerCase()
                .trim();
    }

    /**
     * Extrai o cabeçalho Message-ID; se não houver, devolve {@code null}
     * (o que faz com que apenas a dedup do AlertaService atue).
     */
    private String extrairMessageId(Message msg) {
        try {
            String[] vals = msg.getHeader("Message-ID");
            if (vals != null && vals.length > 0 && vals[0] != null && !vals[0].isBlank()) {
                return vals[0].trim();
            }
        } catch (Exception e) {
            log.debug("Gmail monitor: erro ao ler Message-ID: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Extrai o corpo da mensagem como TEXTO. Prefere {@code text/plain};
     * quando o e-mail só tem {@code text/html}, converte o HTML em texto
     * legível (evita enviar HTML cru/quebrado no Telegram).
     */
    private String extrairCorpo(Message msg) {
        try {
            // Parte única text/plain
            if (msg.isMimeType("text/plain") && msg.getContent() instanceof String s) {
                return s;
            }
            // Parte única text/html -> converte para texto
            if (msg.isMimeType("text/html") && msg.getContent() instanceof String s) {
                return htmlParaTexto(s);
            }
            Object content = msg.getContent();
            if (content instanceof String s) {
                // Tipo não declarado: se parecer HTML, limpa as tags.
                return pareceHtml(s) ? htmlParaTexto(s) : s;
            }
            if (content instanceof Multipart mp) {
                return extrairTextoMultipart(mp);
            }
        } catch (IOException | jakarta.mail.MessagingException e) {
            log.debug("Gmail monitor: erro ao extrair corpo: {}", e.getMessage());
        } catch (Exception e) {
            log.debug("Gmail monitor: erro inesperado ao extrair corpo: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Procura primeiro uma parte {@code text/plain}; se não houver, usa a
     * {@code text/html} convertida para texto. Percorre partes aninhadas.
     */
    private String extrairTextoMultipart(Multipart mp) throws Exception {
        String plain = buscarParte(mp, "text/plain");
        if (plain != null && !plain.isBlank()) {
            return plain;
        }
        String html = buscarParte(mp, "text/html");
        if (html != null && !html.isBlank()) {
            return htmlParaTexto(html);
        }
        return null;
    }

    /** Busca recursiva pela primeira parte do MIME informado (texto cru). */
    private String buscarParte(Multipart mp, String mimeType) throws Exception {
        for (int i = 0; i < mp.getCount(); i++) {
            Part parte = mp.getBodyPart(i);
            if (parte.isMimeType(mimeType)) {
                Object c = parte.getContent();
                if (c instanceof String s) {
                    return s;
                }
                if (c instanceof InputStream is) {
                    return new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
            } else if (parte.isMimeType("multipart/*") && parte.getContent() instanceof Multipart sub) {
                String aninhado = buscarParte(sub, mimeType);
                if (aninhado != null && !aninhado.isBlank()) {
                    return aninhado;
                }
            }
        }
        return null;
    }

    /** Heurística simples para detectar conteúdo HTML em corpo sem MIME claro. */
    private boolean pareceHtml(String s) {
        if (s == null) return false;
        String low = s.toLowerCase();
        return low.contains("<html") || low.contains("<body") || low.contains("<div")
                || low.contains("<table") || low.contains("<p>") || low.contains("<br");
    }

    /**
     * Converte HTML em texto legível para o Telegram: preserva quebras de
     * linha de blocos comuns (br, p, div, tr, li, headings) e remove o resto
     * das tags. Em caso de erro, faz um strip básico de tags.
     */
    private String htmlParaTexto(String html) {
        if (html == null || html.isBlank()) {
            return "";
        }
        try {
            Document doc = Jsoup.parse(html);
            doc.outputSettings(new Document.OutputSettings().prettyPrint(false));
            doc.select("br").append("[[BR]]");
            doc.select("p, div, tr, li, h1, h2, h3, h4, h5, h6").prepend("[[BR]]");
            String texto = doc.text().replace("[[BR]]", "\n");
            // Normaliza espaços/quebras excessivas.
            texto = texto.replaceAll("[ \\t]+\n", "\n")
                    .replaceAll("\n{3,}", "\n\n")
                    .trim();
            return texto;
        } catch (Exception e) {
            return html.replaceAll("(?s)<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        }
    }

    /**
     * Formata o corpo bruto do e-mail para Markdown do Telegram, extraindo
     * Remetente / Assunto / Caixa quando o formato bate com o padrão da
     * notificação "Urgência Renal". Caso o padrão não case, devolve o
     * texto bruto limpo (sem rodapé de unsubscribe).
     */
    private String formatarParaTelegram(String corpoBruto, String subjectFallback) {
        String limpo = corpoBruto == null ? "" : UNSUBSCRIBE_RE.matcher(corpoBruto).replaceAll("").trim();

        String caixa = extrair(CAIXA_RE, limpo);
        String remetente = extrair(REMETENTE_RE, limpo);
        String assunto = extrair(ASSUNTO_RE, limpo);

        if (remetente == null && assunto == null) {
            if (limpo.isBlank()) {
                return subjectFallback == null ? "" : subjectFallback;
            }
            return limpo;
        }

        StringBuilder sb = new StringBuilder();
        if (caixa != null) {
            sb.append("📥 *Caixa:* ").append(escapeMarkdown(caixa)).append('\n');
        }
        if (remetente != null) {
            sb.append("👤 *Remetente:* `").append(remetente.replace("`", "'")).append("`\n");
        }
        if (assunto != null) {
            sb.append("📩 *Assunto:* ").append(escapeMarkdown(assunto)).append('\n');
        }
        return sb.toString().trim();
    }

    private String extrair(java.util.regex.Pattern p, String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        java.util.regex.Matcher m = p.matcher(texto);
        if (m.find()) {
            String v = m.group(1).trim();
            return v.isBlank() ? null : v;
        }
        return null;
    }

    /**
     * Escapa caracteres reservados do Markdown V1 do Telegram em conteúdo
     * dinâmico para evitar quebra de parsing (ex.: underscores em e-mails).
     */
    private String escapeMarkdown(String texto) {
        if (texto == null) {
            return "";
        }
        return texto.replace("\\", "\\\\")
                .replace("_", "\\_")
                .replace("*", "\\*")
                .replace("`", "'")
                .replace("[", "\\[");
    }

    /**
     * Persiste o assunto mais recente em {@code ultimo_assunto.txt} para
     * compatibilidade com CI / scripts legados.
     */
    private void persistirUltimoAssunto(String subject) {
        try {
            Files.writeString(STATE_FILE, subject, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.debug("Gmail monitor: não foi possível atualizar {}: {}", STATE_FILE, e.getMessage());
        }
    }
}
