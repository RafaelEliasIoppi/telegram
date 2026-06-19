package telegram.bot.controller;

import java.net.InetAddress;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import telegram.bot.domain.Alerta;
import telegram.bot.domain.ChatConfig;
import telegram.bot.domain.FiltroAssunto;
import telegram.bot.domain.FonteCustomizada;
import telegram.bot.domain.GmailConfig;
import telegram.bot.repository.ChatConfigRepository;
import telegram.bot.repository.FiltroAssuntoRepository;
import telegram.bot.repository.FonteCustomizadaRepository;
import telegram.bot.repository.GmailConfigRepository;
import telegram.bot.service.AlertaService;
import telegram.bot.service.GmailConfigService;
import telegram.bot.service.bot.TelegramBotService;
import telegram.bot.service.monitor.MonitorScheduler;

/**
 * Controller principal da UI Web (Thymeleaf).
 *
 * Rotas:
 *  - GET  /                              -> redireciona para /dashboard
 *  - GET  /dashboard                     -> visão geral
 *  - GET  /historico                     -> histórico de alertas
 *  - GET  /chats                         -> gerenciamento de chats
 *  - POST /chats                         -> salvar novo chat
 *  - POST /chats/{chatId}/remover        -> remover chat
 *  - GET  /bot                           -> gerenciamento do bot
 *  - POST /bot/webhook/registrar         -> registrar webhook
 *  - POST /bot/webhook/remover           -> remover webhook
 *  - GET  /login                         -> página de login
 */
@Controller
public class WebController {

    private static final Logger log = LoggerFactory.getLogger(WebController.class);

    /** Limite genérico de tamanho para campos de texto vindos de formulários. */
    private static final int MAX_TEXTO = 2000;
    private static final int MAX_CAMPO = 500;

    private final AlertaService alertaService;
    private final TelegramBotService telegramService;
    private final ChatConfigRepository chatConfigRepo;
    private final MonitorScheduler monitorScheduler;
    private final FiltroAssuntoRepository filtroRepo;
    private final FonteCustomizadaRepository fonteRepo;
    private final GmailConfigService gmailConfigService;

    @Value("${defesacivil.enabled:true}")
    private boolean defesaCivilEnabled;

    @Value("${inmet.enabled:true}")
    private boolean inmetEnabled;

    @Autowired
    public WebController(AlertaService alertaService,
                         TelegramBotService telegramService,
                         ChatConfigRepository chatConfigRepo,
                         MonitorScheduler monitorScheduler,
                         FiltroAssuntoRepository filtroRepo,
                         FonteCustomizadaRepository fonteRepo,
                         GmailConfigService gmailConfigService) {
        this.alertaService = alertaService;
        this.telegramService = telegramService;
        this.chatConfigRepo = chatConfigRepo;
        this.monitorScheduler = monitorScheduler;
        this.filtroRepo = filtroRepo;
        this.fonteRepo = fonteRepo;
        this.gmailConfigService = gmailConfigService;
    }

    @PostMapping("/bot/verificar-agora")
    public String verificarAgora(RedirectAttributes redirectAttributes) {
        try {
            monitorScheduler.executarVerificacao();
            redirectAttributes.addFlashAttribute("flashSuccess",
                    "Verificação manual disparada. Novos alertas aparecerão em segundos.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("flashError",
                    "Falha ao executar verificação: " + e.getMessage());
        }
        return "redirect:/dashboard";
    }

    // ------------------------------------------------------------------
    // Root + Login
    // ------------------------------------------------------------------

    @GetMapping("/")
    public String root() {
        return "redirect:/dashboard";
    }

    @GetMapping("/login")
    public String login(Model model) {
        model.addAttribute("pageTitle", "Login");
        return "login";
    }

    // ------------------------------------------------------------------
    // Dashboard
    // ------------------------------------------------------------------

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        List<Alerta> alertas = alertaService.ultimosAlertas(10);
        List<ChatConfig> chats = alertaService.listarChats();

        // Para a contagem "Hoje", busca uma janela maior:
        List<Alerta> recentes = alertaService.ultimosAlertas(200);
        LocalDateTime inicioHoje = LocalDate.now().atStartOfDay();
        LocalDateTime inicio7Dias = LocalDate.now().minusDays(7).atStartOfDay();
        long alertasHoje = recentes.stream()
                .filter(a -> a.getDataHora() != null && !a.getDataHora().isBefore(inicioHoje))
                .count();

        long chatsAtivos = chats.stream().filter(ChatConfig::isAtivo).count();

        // Última verificação = data/hora do alerta mais recente registrado
        LocalDateTime ultimaVerificacao = recentes.stream()
                .map(Alerta::getDataHora)
                .filter(d -> d != null)
                .max(Comparator.naturalOrder())
                .orElse(null);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        String ultimaVerificacaoFmt = ultimaVerificacao != null
                ? ultimaVerificacao.format(fmt)
                : "—";

        // Status Gmail e operacional
        GmailConfig gmailCfg = gmailConfigService.getConfig();
        boolean gmailAtivo = gmailCfg != null && gmailCfg.isEnabled();
        boolean operacional = defesaCivilEnabled || inmetEnabled || gmailAtivo;

        // Contagem por fonte nos últimos 7 dias (in-memory)
        Map<String, Long> contagemPorFonte = new LinkedHashMap<>();
        for (Alerta a : recentes) {
            if (a.getDataHora() == null || a.getDataHora().isBefore(inicio7Dias)) continue;
            String f = a.getFonte() == null ? "?" : a.getFonte();
            contagemPorFonte.merge(f, 1L, Long::sum);
        }

        // Fontes de monitoramento — intervalo efetivo (configurável na UI e
        // aplicado dinamicamente pelo agendador), não o valor estático de boot.
        String intervaloGlobal = formatarIntervalo(gmailConfigService.getIntervaloVerificacaoMs());
        List<Map<String, Object>> fontesMonitoramento = new ArrayList<>();
        fontesMonitoramento.add(montarFonte(
                "Defesa Civil RS", "DEFESA_CIVIL_RS", "bi-shield-exclamation",
                defesaCivilEnabled, intervaloGlobal,
                contagemPorFonte.getOrDefault("DEFESA_CIVIL_RS", 0L)));
        fontesMonitoramento.add(montarFonte(
                "INMET", "INMET", "bi-cloud-rain-heavy",
                inmetEnabled, intervaloGlobal,
                contagemPorFonte.getOrDefault("INMET", 0L)));
        fontesMonitoramento.add(montarFonte(
                "Gmail — Urgência Renal", "GMAIL_URGENCIA_RENAL", "bi-envelope-exclamation",
                gmailAtivo, intervaloGlobal,
                contagemPorFonte.getOrDefault("GMAIL_URGENCIA_RENAL", 0L)));
        try {
            for (FonteCustomizada fc : fonteRepo.findAll()) {
                fontesMonitoramento.add(montarFonte(
                        fc.getNome(), fc.getCodigo(), "bi-broadcast",
                        fc.isAtivo(), intervaloGlobal,
                        contagemPorFonte.getOrDefault(fc.getCodigo(), 0L)));
            }
        } catch (Exception e) {
            // se falhar, segue só com as nativas
            log.warn("Falha ao carregar fontes customizadas para o dashboard: {}", e.getMessage());
        }

        model.addAttribute("pageTitle", "Painel de Controle");
        model.addAttribute("alertas", alertas);
        model.addAttribute("chats", chats);
        model.addAttribute("totalAlertas", recentes.size());
        model.addAttribute("alertasHoje", alertasHoje);
        model.addAttribute("chatsAtivos", chatsAtivos);
        model.addAttribute("webhookStatus", "OK");
        model.addAttribute("ultimaVerificacao", ultimaVerificacaoFmt);
        model.addAttribute("operacional", operacional);
        model.addAttribute("fontesMonitoramento", fontesMonitoramento);
        model.addAttribute("serverTime", LocalDateTime.now().format(fmt));
        return "dashboard";
    }

    private String formatarIntervalo(long ms) {
        long min = ms / 60000L;
        if (min <= 0) return ms + " ms";
        if (min < 60) return min + " min";
        return (min / 60) + " h";
    }

    private Map<String, Object> montarFonte(String nome,
                                            String codigo,
                                            String icone,
                                            boolean ativo,
                                            String intervalo,
                                            long alertas7Dias) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("nome", nome);
        m.put("codigo", codigo);
        m.put("icone", icone);
        m.put("ativo", ativo);
        m.put("intervalo", intervalo);
        m.put("alertas7Dias", alertas7Dias);
        return m;
    }

    // ------------------------------------------------------------------
    // Histórico
    // ------------------------------------------------------------------

    @GetMapping("/historico")
    public String historico(Model model) {
        List<Alerta> alertas = alertaService.ultimosAlertas(50);
        model.addAttribute("pageTitle", "Histórico");
        model.addAttribute("alertas", alertas);
        return "historico";
    }

    // ------------------------------------------------------------------
    // Chats
    // ------------------------------------------------------------------

    @GetMapping("/chats")
    public String chats(Model model) {
        List<ChatConfig> chats = alertaService.listarChats();
        model.addAttribute("pageTitle", "Chats");
        model.addAttribute("chats", chats);
        model.addAttribute("fontesDisponiveis", listarFontesDisponiveis());
        if (!model.containsAttribute("chatForm")) {
            ChatConfig form = new ChatConfig();
            form.setAtivo(true);
            form.setNivelMinimo("INFO");
            model.addAttribute("chatForm", form);
        }
        return "chats";
    }

    /**
     * Monta a lista de todas as fontes que podem ser escolhidas em /chats:
     * nativas (Defesa Civil RS, INMET, Gmail Urgência Renal) + customizadas
     * cadastradas em /fontes.
     */
    private List<Map<String, String>> listarFontesDisponiveis() {
        List<Map<String, String>> out = new ArrayList<>();
        out.add(Map.of("codigo", "DEFESA_CIVIL_RS", "nome", "Defesa Civil RS"));
        out.add(Map.of("codigo", "INMET", "nome", "INMET"));
        out.add(Map.of("codigo", "GMAIL_URGENCIA_RENAL", "nome", "Gmail — Urgência Renal"));
        try {
            for (FonteCustomizada f : fonteRepo.findAll()) {
                out.add(Map.of("codigo", f.getCodigo(), "nome", f.getNome()));
            }
        } catch (Exception e) {
            // se o repo falhar, devolve só as nativas
            log.warn("Falha ao listar fontes customizadas disponíveis: {}", e.getMessage());
        }
        return out;
    }

    @PostMapping("/chats")
    public String salvarChat(ChatConfig chatForm, RedirectAttributes ra) {
        try {
            if (chatForm.getChatId() == null) {
                ra.addFlashAttribute("erro", "Chat ID é obrigatório.");
                return "redirect:/chats";
            }
            // Limites simples de tamanho para os campos textuais.
            if ((chatForm.getNome() != null && chatForm.getNome().length() > MAX_CAMPO)
                    || (chatForm.getNivelMinimo() != null && chatForm.getNivelMinimo().length() > MAX_CAMPO)
                    || (chatForm.getFontesAtivas() != null && chatForm.getFontesAtivas().length() > MAX_TEXTO)) {
                ra.addFlashAttribute("erro", "Algum campo excede o tamanho máximo permitido.");
                return "redirect:/chats";
            }
            // Defaults seguros
            if (chatForm.getNivelMinimo() == null || chatForm.getNivelMinimo().isBlank()) {
                chatForm.setNivelMinimo("INFO");
            }
            if (chatForm.getFontesAtivas() == null) {
                chatForm.setFontesAtivas("");
            }
            alertaService.salvarChat(chatForm);
            ra.addFlashAttribute("sucesso", "Chat salvo com sucesso.");
        } catch (Exception e) {
            ra.addFlashAttribute("erro", "Erro ao salvar chat: " + e.getMessage());
        }
        return "redirect:/chats";
    }

    @PostMapping("/chats/{chatId}/remover")
    public String removerChat(@PathVariable Long chatId, RedirectAttributes ra) {
        try {
            alertaService.removerChat(chatId);
            ra.addFlashAttribute("sucesso", "Chat removido com sucesso.");
        } catch (Exception e) {
            ra.addFlashAttribute("erro", "Erro ao remover chat: " + e.getMessage());
        }
        return "redirect:/chats";
    }

    // ------------------------------------------------------------------
    // Bot
    // ------------------------------------------------------------------

    @GetMapping("/bot")
    public String bot(Model model) {
        String botInfo;
        try {
            botInfo = telegramService.getMe();
        } catch (Exception e) {
            botInfo = "Erro ao obter informações do bot: " + e.getMessage();
        }
        model.addAttribute("pageTitle", "Bot");
        model.addAttribute("botInfo", botInfo);
        return "bot";
    }

    @PostMapping("/bot/webhook/registrar")
    public String registrarWebhook(RedirectAttributes ra) {
        try {
            String resp = telegramService.registrarWebhook();
            ra.addFlashAttribute("sucesso", "Webhook registrado: " + resp);
        } catch (Exception e) {
            ra.addFlashAttribute("erro", "Erro ao registrar webhook: " + e.getMessage());
        }
        return "redirect:/bot";
    }

    @PostMapping("/bot/webhook/remover")
    public String removerWebhook(RedirectAttributes ra) {
        try {
            String resp = telegramService.removerWebhook();
            ra.addFlashAttribute("sucesso", "Webhook removido: " + resp);
        } catch (Exception e) {
            ra.addFlashAttribute("erro", "Erro ao remover webhook: " + e.getMessage());
        }
        return "redirect:/bot";
    }

    // ------------------------------------------------------------------
    // Filtros de Assunto (Gmail)
    // ------------------------------------------------------------------

    @GetMapping("/filtros")
    public String filtros(Model model) {
        List<FiltroAssunto> lista = filtroRepo.findAll();
        lista.sort(Comparator.comparing(FiltroAssunto::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        long ativos = lista.stream().filter(FiltroAssunto::isAtivo).count();
        model.addAttribute("pageTitle", "Filtros de Assunto");
        model.addAttribute("filtros", lista);
        model.addAttribute("totalFiltros", lista.size());
        model.addAttribute("filtrosAtivos", ativos);
        return "filtros";
    }

    @PostMapping("/filtros")
    public String adicionarFiltro(@org.springframework.web.bind.annotation.RequestParam String nome,
                                  @org.springframework.web.bind.annotation.RequestParam String padrao,
                                  @org.springframework.web.bind.annotation.RequestParam(required = false) Boolean ativo,
                                  RedirectAttributes ra) {
        if (nome == null || nome.isBlank() || padrao == null || padrao.isBlank()) {
            ra.addFlashAttribute("erro", "Nome e Padrão são obrigatórios.");
            return "redirect:/filtros";
        }
        if (nome.length() > MAX_CAMPO || padrao.length() > MAX_CAMPO) {
            ra.addFlashAttribute("erro", "Nome e Padrão devem ter no máximo " + MAX_CAMPO + " caracteres.");
            return "redirect:/filtros";
        }
        FiltroAssunto f = FiltroAssunto.builder()
                .nome(nome.trim())
                .padrao(padrao.trim())
                .ativo(ativo != null && ativo)
                .build();
        filtroRepo.save(f);
        ra.addFlashAttribute("sucesso", "Filtro '" + f.getNome() + "' adicionado.");
        return "redirect:/filtros";
    }

    @PostMapping("/filtros/{id}/toggle")
    public String alternarFiltro(@PathVariable Long id, RedirectAttributes ra) {
        filtroRepo.findById(id).ifPresentOrElse(f -> {
            f.setAtivo(!f.isAtivo());
            filtroRepo.save(f);
            ra.addFlashAttribute("sucesso",
                    "Filtro '" + f.getNome() + "' " + (f.isAtivo() ? "ativado" : "desativado") + ".");
        }, () -> ra.addFlashAttribute("erro", "Filtro não encontrado."));
        return "redirect:/filtros";
    }

    @PostMapping("/filtros/{id}/remover")
    public String removerFiltro(@PathVariable Long id, RedirectAttributes ra) {
        filtroRepo.findById(id).ifPresentOrElse(f -> {
            filtroRepo.delete(f);
            ra.addFlashAttribute("sucesso", "Filtro '" + f.getNome() + "' removido.");
        }, () -> ra.addFlashAttribute("erro", "Filtro não encontrado."));
        return "redirect:/filtros";
    }

    // ------------------------------------------------------------------
    // Gmail
    // ------------------------------------------------------------------

    @GetMapping("/gmail")
    public String gmail(Model model) {
        GmailConfig cfg = gmailConfigService.getConfig();
        if (cfg == null) {
            cfg = GmailConfig.builder().id(1L).enabled(false).imapHost("imap.gmail.com").imapPort(993).fixedRate(300000L).build();
        }
        model.addAttribute("pageTitle", "Configuração Gmail");
        model.addAttribute("gmail", cfg);
        return "gmail";
    }

    @PostMapping("/gmail")
    public String salvarGmail(GmailConfig form,
                              @org.springframework.web.bind.annotation.RequestParam(required = false) Boolean enabled,
                              RedirectAttributes ra) {
        // Limites simples e proteção contra mass-assignment: nunca confiamos no id
        // vindo do form — usamos sempre a config existente (ou id fixo 1L).
        if ((form.getUser() != null && form.getUser().length() > MAX_CAMPO)
                || (form.getImapHost() != null && form.getImapHost().length() > MAX_CAMPO)) {
            ra.addFlashAttribute("erro", "Usuário ou host IMAP excede o tamanho máximo permitido.");
            return "redirect:/gmail";
        }
        GmailConfig cfg = gmailConfigService.getConfig();
        if (cfg == null) cfg = GmailConfig.builder().id(1L).build();
        cfg.setEnabled(enabled != null && enabled);
        cfg.setUser(form.getUser());
        if (form.getAppPassword() != null && !form.getAppPassword().isBlank()) {
            cfg.setAppPassword(form.getAppPassword());
        }
        cfg.setImapHost(form.getImapHost() != null && !form.getImapHost().isBlank() ? form.getImapHost() : "imap.gmail.com");
        cfg.setImapPort(form.getImapPort() > 0 ? form.getImapPort() : 993);
        cfg.setFixedRate(form.getFixedRate() > 0 ? form.getFixedRate() : 300000L);
        gmailConfigService.saveConfig(cfg);
        ra.addFlashAttribute("sucesso", "Configuração Gmail salva com sucesso.");
        return "redirect:/gmail";
    }

    // ------------------------------------------------------------------
    // Fontes Customizadas (URL / RSS)
    // ------------------------------------------------------------------

    @GetMapping("/fontes")
    public String fontes(Model model) {
        List<FonteCustomizada> lista = fonteRepo.findAll();
        lista.sort(Comparator.comparing(FonteCustomizada::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        model.addAttribute("pageTitle", "Fontes de Monitoramento");
        model.addAttribute("fontes", lista);
        model.addAttribute("totalFontes", lista.size());
        model.addAttribute("fontesAtivas", lista.stream().filter(FonteCustomizada::isAtivo).count());
        return "fontes";
    }

    @PostMapping("/fontes")
    public String adicionarFonte(@org.springframework.web.bind.annotation.RequestParam String nome,
                                 @org.springframework.web.bind.annotation.RequestParam String tipo,
                                 @org.springframework.web.bind.annotation.RequestParam String url,
                                 @org.springframework.web.bind.annotation.RequestParam(required = false) String seletor,
                                 @org.springframework.web.bind.annotation.RequestParam(required = false) String palavrasChave,
                                 @org.springframework.web.bind.annotation.RequestParam(required = false) String nivel,
                                 @org.springframework.web.bind.annotation.RequestParam(required = false) Boolean ativo,
                                 RedirectAttributes ra) {
        if (nome == null || nome.isBlank() || url == null || url.isBlank()) {
            ra.addFlashAttribute("erro", "Nome e URL são obrigatórios.");
            return "redirect:/fontes";
        }
        // Limites simples de tamanho para evitar payloads abusivos.
        if (nome.length() > MAX_CAMPO || url.length() > MAX_TEXTO
                || (seletor != null && seletor.length() > MAX_CAMPO)
                || (palavrasChave != null && palavrasChave.length() > MAX_TEXTO)) {
            ra.addFlashAttribute("erro", "Algum campo excede o tamanho máximo permitido.");
            return "redirect:/fontes";
        }
        if (!url.matches("(?i)https?://.+")) {
            ra.addFlashAttribute("erro", "URL precisa começar com http:// ou https://.");
            return "redirect:/fontes";
        }
        // Anti-SSRF: recusa URLs que apontem para hosts internos/privados.
        if (!urlSegura(url)) {
            ra.addFlashAttribute("erro", "URL não permitida (host interno/privado ou não resolvível).");
            return "redirect:/fontes";
        }
        String codigo = nome.trim().toUpperCase()
                .replaceAll("[ÁÀÂÃÄ]", "A").replaceAll("[ÉÈÊË]", "E")
                .replaceAll("[ÍÌÎÏ]", "I").replaceAll("[ÓÒÔÕÖ]", "O")
                .replaceAll("[ÚÙÛÜ]", "U").replaceAll("[Ç]", "C")
                .replaceAll("[^A-Z0-9]+", "_")
                .replaceAll("(^_+|_+$)", "");
        if (codigo.isBlank()) codigo = "FONTE_" + System.currentTimeMillis();

        FonteCustomizada f = FonteCustomizada.builder()
                .nome(nome.trim())
                .codigo(codigo)
                .tipo("RSS".equalsIgnoreCase(tipo) ? "RSS" : "URL")
                .url(url.trim())
                .seletor(seletor == null ? null : seletor.trim())
                .palavrasChave(palavrasChave == null ? null : palavrasChave.trim())
                .nivel(nivel == null || nivel.isBlank() ? "INFO" : nivel.trim().toUpperCase())
                .ativo(ativo != null && ativo)
                .build();
        fonteRepo.save(f);
        ra.addFlashAttribute("sucesso", "Fonte '" + f.getNome() + "' cadastrada (código " + codigo + ").");
        return "redirect:/fontes";
    }

    @PostMapping("/fontes/{id}/toggle")
    public String alternarFonte(@PathVariable Long id, RedirectAttributes ra) {
        fonteRepo.findById(id).ifPresentOrElse(f -> {
            f.setAtivo(!f.isAtivo());
            fonteRepo.save(f);
            ra.addFlashAttribute("sucesso",
                    "Fonte '" + f.getNome() + "' " + (f.isAtivo() ? "ativada" : "desativada") + ".");
        }, () -> ra.addFlashAttribute("erro", "Fonte não encontrada."));
        return "redirect:/fontes";
    }

    @PostMapping("/fontes/{id}/remover")
    public String removerFonte(@PathVariable Long id, RedirectAttributes ra) {
        fonteRepo.findById(id).ifPresentOrElse(f -> {
            fonteRepo.delete(f);
            ra.addFlashAttribute("sucesso", "Fonte '" + f.getNome() + "' removida.");
        }, () -> ra.addFlashAttribute("erro", "Fonte não encontrada."));
        return "redirect:/fontes";
    }

    // ------------------------------------------------------------------
    // Ajuda
    // ------------------------------------------------------------------

    @GetMapping("/ajuda")
    public String ajuda(Model model) {
        model.addAttribute("pageTitle", "Ajuda");
        return "ajuda";
    }

    // ------------------------------------------------------------------
    // Helpers de segurança / validação
    // ------------------------------------------------------------------

    /**
     * Verifica se uma URL é segura para a aplicação acessar (anti-SSRF).
     *
     * <p>Rejeita esquemas diferentes de http/https e hosts que resolvam para
     * endereços loopback, privados, link-local ou de metadados de cloud
     * (127.0.0.0/8, 10/8, 172.16/12, 192.168/16, 169.254/16, localhost, ::1,
     * 169.254.169.254). Falha de resolução é tratada como insegura.</p>
     */
    private boolean urlSegura(String url) {
        if (url == null || url.isBlank()) return false;
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            if (scheme == null
                    || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                return false;
            }
            String host = uri.getHost();
            if (host == null || host.isBlank()) return false;

            String hostLower = host.toLowerCase();
            if (hostLower.equals("localhost") || hostLower.equals("ip6-localhost")) {
                return false;
            }

            // Resolve todos os endereços do host: se QUALQUER um for inseguro, rejeita.
            InetAddress[] enderecos = InetAddress.getAllByName(host);
            if (enderecos.length == 0) return false;
            for (InetAddress addr : enderecos) {
                if (enderecoInseguro(addr)) return false;
            }
            return true;
        } catch (Exception e) {
            // URL malformada ou host que não resolve = inseguro.
            log.debug("urlSegura: rejeitando '{}' ({})", url, e.getMessage());
            return false;
        }
    }

    /** Classifica um endereço IP como interno/perigoso para SSRF. */
    private boolean enderecoInseguro(InetAddress addr) {
        if (addr.isLoopbackAddress()       // 127.0.0.0/8, ::1
                || addr.isAnyLocalAddress() // 0.0.0.0
                || addr.isLinkLocalAddress()// 169.254/16, fe80::/10
                || addr.isSiteLocalAddress()// 10/8, 172.16/12, 192.168/16
                || addr.isMulticastAddress()) {
            return true;
        }
        // Metadados de cloud (AWS/GCP/Azure) — coberto por link-local, mas reforçamos.
        return "169.254.169.254".equals(addr.getHostAddress());
    }

    /** Trunca um texto a {@code max} caracteres, preservando null. */
    private String limitar(String valor, int max) {
        if (valor == null) return null;
        return valor.length() > max ? valor.substring(0, max) : valor;
    }

    // ------------------------------------------------------------------
    // Teste rápido — envio direto pro Telegram
    // ------------------------------------------------------------------

    @GetMapping("/teste")
    public String teste(Model model) {
        model.addAttribute("pageTitle", "Teste de envio");
        model.addAttribute("chats", alertaService.listarChats());
        return "teste";
    }

    @PostMapping(value = "/teste", consumes = {"multipart/form-data", "application/x-www-form-urlencoded"})
    public String enviarTeste(@org.springframework.web.bind.annotation.RequestParam(required = false) Long chatId,
                              @org.springframework.web.bind.annotation.RequestParam(required = false) String chatIdCustom,
                              @org.springframework.web.bind.annotation.RequestParam(required = false) String texto,
                              @org.springframework.web.bind.annotation.RequestParam(required = false) String anexoUrl,
                              @org.springframework.web.bind.annotation.RequestParam(value = "anexoArquivo", required = false)
                                  org.springframework.web.multipart.MultipartFile anexoArquivo,
                              RedirectAttributes ra) {
        Long destino = chatId;
        if (chatIdCustom != null && !chatIdCustom.isBlank()) {
            try { destino = Long.parseLong(chatIdCustom.trim()); }
            catch (NumberFormatException e) {
                ra.addFlashAttribute("erro", "Chat ID customizado inválido.");
                return "redirect:/teste";
            }
        }
        if (destino == null) {
            ra.addFlashAttribute("erro", "Selecione um chat cadastrado ou informe um Chat ID.");
            return "redirect:/teste";
        }

        boolean temArquivo = anexoArquivo != null && !anexoArquivo.isEmpty();
        boolean temUrl = anexoUrl != null && !anexoUrl.isBlank();
        boolean temTexto = texto != null && !texto.isBlank();

        if (!temTexto && !temArquivo && !temUrl) {
            ra.addFlashAttribute("erro", "Escreva um texto, anexe um arquivo ou cole uma URL.");
            return "redirect:/teste";
        }
        // Limite simples de tamanho do texto (trunca para não exceder limites do Telegram).
        if (texto != null && texto.length() > MAX_TEXTO) {
            texto = limitar(texto, MAX_TEXTO);
        }

        try {
            if (temArquivo) {
                String nome = anexoArquivo.getOriginalFilename() == null
                        ? "arquivo.bin" : anexoArquivo.getOriginalFilename();
                String contentType = anexoArquivo.getContentType() == null
                        ? "" : anexoArquivo.getContentType().toLowerCase();
                boolean isFoto = contentType.startsWith("image/")
                        || nome.toLowerCase().matches(".*\\.(jpe?g|png|webp)$");
                String method = isFoto ? "sendPhoto" : "sendDocument";
                String campo = isFoto ? "photo" : "document";
                telegramService.enviarArquivoMultipart(destino, method, campo,
                        nome, anexoArquivo.getBytes(), texto);
                ra.addFlashAttribute("sucesso",
                        "Enviado para o chat " + destino + " com anexo '" + nome + "'.");
            } else if (temUrl) {
                // Anti-SSRF: a URL será buscada pelo Telegram (foto) ou divulgada;
                // recusamos hosts internos/privados de qualquer forma.
                if (!urlSegura(anexoUrl)) {
                    ra.addFlashAttribute("erro", "URL não permitida (host interno/privado ou não resolvível).");
                    return "redirect:/teste";
                }
                String lower = anexoUrl.toLowerCase();
                boolean isFoto = lower.matches(".*\\.(jpe?g|png|webp)(\\?.*)?$");
                if (isFoto) {
                    telegramService.enviarFotoMarkdown(destino, anexoUrl.trim(), texto);
                } else {
                    String msg = (temTexto ? texto + "\n\n" : "") + anexoUrl.trim();
                    telegramService.enviarMarkdown(destino, msg);
                }
                ra.addFlashAttribute("sucesso", "Enviado para o chat " + destino + " com link.");
            } else {
                telegramService.enviarMarkdown(destino, texto);
                ra.addFlashAttribute("sucesso", "Mensagem enviada para o chat " + destino + ".");
            }
        } catch (Exception e) {
            ra.addFlashAttribute("erro", "Falha no envio: " + e.getMessage());
        }
        return "redirect:/teste";
    }
}
