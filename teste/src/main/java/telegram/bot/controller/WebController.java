package telegram.bot.controller;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
import telegram.bot.repository.ChatConfigRepository;
import telegram.bot.service.AlertaService;
import telegram.bot.service.bot.TelegramBotService;

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

    private final AlertaService alertaService;
    private final TelegramBotService telegramService;
    private final ChatConfigRepository chatConfigRepo;

    @Value("${defesacivil.enabled:true}")
    private boolean defesaCivilEnabled;

    @Value("${inmet.enabled:true}")
    private boolean inmetEnabled;

    @Value("${gmail.enabled:false}")
    private boolean gmailEnabled;

    @Autowired
    public WebController(AlertaService alertaService,
                         TelegramBotService telegramService,
                         ChatConfigRepository chatConfigRepo) {
        this.alertaService = alertaService;
        this.telegramService = telegramService;
        this.chatConfigRepo = chatConfigRepo;
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

        // Status operacional: ao menos uma fonte ativa
        boolean operacional = defesaCivilEnabled || inmetEnabled || gmailEnabled;

        // Contagem por fonte nos últimos 7 dias (in-memory)
        Map<String, Long> contagemPorFonte = new LinkedHashMap<>();
        for (Alerta a : recentes) {
            if (a.getDataHora() == null || a.getDataHora().isBefore(inicio7Dias)) continue;
            String f = a.getFonte() == null ? "?" : a.getFonte();
            contagemPorFonte.merge(f, 1L, Long::sum);
        }

        // Fontes de monitoramento
        List<Map<String, Object>> fontesMonitoramento = new ArrayList<>();
        fontesMonitoramento.add(montarFonte(
                "Defesa Civil RS",
                "DEFESA_CIVIL_RS",
                "bi-shield-exclamation",
                defesaCivilEnabled,
                "10 min",
                contagemPorFonte.getOrDefault("DEFESA_CIVIL_RS", 0L)));
        fontesMonitoramento.add(montarFonte(
                "INMET",
                "INMET",
                "bi-cloud-rain-heavy",
                inmetEnabled,
                "30 min",
                contagemPorFonte.getOrDefault("INMET", 0L)));
        fontesMonitoramento.add(montarFonte(
                "Gmail — Urgência Renal",
                "GMAIL_URGENCIA_RENAL",
                "bi-envelope-exclamation",
                gmailEnabled,
                "5 min",
                contagemPorFonte.getOrDefault("GMAIL_URGENCIA_RENAL", 0L)));

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
        if (!model.containsAttribute("chatForm")) {
            ChatConfig form = new ChatConfig();
            form.setAtivo(true);
            form.setNivelMinimo("INFO");
            model.addAttribute("chatForm", form);
        }
        return "chats";
    }

    @PostMapping("/chats")
    public String salvarChat(ChatConfig chatForm, RedirectAttributes ra) {
        try {
            if (chatForm.getChatId() == null) {
                ra.addFlashAttribute("erro", "Chat ID é obrigatório.");
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
}
