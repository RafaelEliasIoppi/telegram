package telegram.bot.service.bot;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import telegram.bot.domain.Alerta;
import telegram.bot.domain.ChatConfig;
import telegram.bot.service.AlertaService;

/**
 * Roteador de comandos e callbacks do bot Telegram.
 *
 * <p>Recebe os updates já decodificados e despacha para os handlers de
 * cada comando suportado (/start, /ajuda, /status, /historico, /fontes,
 * /nivel, /verificar) bem como para o processamento de botões inline.</p>
 */
@Service
public class CommandDispatcher {

    private static final Logger logger = LoggerFactory.getLogger(CommandDispatcher.class);

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM HH:mm");

    /** Fontes conhecidas exibidas em {@code /fontes}. */
    private static final List<String> FONTES_DISPONIVEIS = List.of("DEFESA_CIVIL_RS", "INMET");

    /** Níveis suportados em {@code /nivel}. */
    private static final Set<String> NIVEIS_VALIDOS = new HashSet<>(Arrays.asList("INFO", "AVISO", "CRITICO"));

    private final TelegramBotService bot;
    private final AlertaService alertaService;

    public CommandDispatcher(TelegramBotService bot, AlertaService alertaService) {
        this.bot = bot;
        this.alertaService = alertaService;
    }

    // ---------------------------------------------------------------------
    // Entrada principal
    // ---------------------------------------------------------------------

    /**
     * Processa um update do tipo {@code message}.
     */
    @SuppressWarnings("unchecked")
    public void processarComando(Map<String, Object> update) {
        try {
            Map<String, Object> message = (Map<String, Object>) update.get("message");
            if (message == null) {
                return;
            }
            Map<String, Object> chat = (Map<String, Object>) message.get("chat");
            if (chat == null) {
                return;
            }
            long chatId = ((Number) chat.get("id")).longValue();
            String text = (String) message.getOrDefault("text", "");

            Map<String, Object> from = (Map<String, Object>) message.get("from");
            String firstName = from != null ? (String) from.getOrDefault("first_name", "") : "";

            if (text == null || !text.startsWith("/")) {
                return;
            }

            String[] parts = text.split("\\s+", 2);
            String comando = parts[0].toLowerCase().replaceAll("@.*", ""); // remove @botname
            String args = parts.length > 1 ? parts[1] : "";

            switch (comando) {
                case "/start" -> cmdStart(chatId, firstName);
                case "/ajuda", "/help" -> cmdAjuda(chatId);
                case "/status" -> cmdStatus(chatId);
                case "/historico" -> cmdHistorico(chatId, args);
                case "/fontes" -> cmdFontes(chatId);
                case "/nivel" -> cmdNivel(chatId, args, chatId);
                case "/verificar" -> cmdVerificar(chatId);
                default -> bot.enviarMensagem(chatId, "❓ Comando não reconhecido. Use /ajuda");
            }
        } catch (Exception e) {
            logger.error("Erro ao processar comando: {}", e.getMessage(), e);
        }
    }

    /**
     * Processa um update do tipo {@code callback_query} (botão inline).
     *
     * <p>Padrões aceitos:
     * <ul>
     *   <li>{@code nivel:INFO | nivel:AVISO | nivel:CRITICO}</li>
     *   <li>{@code fonte:toggle:NOME}</li>
     *   <li>{@code cmd:status | cmd:historico | cmd:fontes | cmd:nivel | cmd:verificar | cmd:ajuda}</li>
     * </ul>
     */
    @SuppressWarnings("unchecked")
    public void processarCallback(Map<String, Object> update) {
        try {
            Map<String, Object> callback = (Map<String, Object>) update.get("callback_query");
            if (callback == null) {
                return;
            }
            String callbackId = String.valueOf(callback.get("id"));
            String data = (String) callback.getOrDefault("data", "");

            Map<String, Object> message = (Map<String, Object>) callback.get("message");
            Map<String, Object> chat = message != null ? (Map<String, Object>) message.get("chat") : null;
            long chatId = chat != null ? ((Number) chat.get("id")).longValue() : 0L;

            Map<String, Object> from = (Map<String, Object>) callback.get("from");
            String firstName = from != null ? (String) from.getOrDefault("first_name", "") : "";

            if (data == null || data.isBlank()) {
                bot.responderCallback(callbackId, null);
                return;
            }

            if (data.startsWith("nivel:")) {
                String nivel = data.substring("nivel:".length()).toUpperCase();
                if (NIVEIS_VALIDOS.contains(nivel)) {
                    aplicarNivel(chatId, nivel);
                    bot.responderCallback(callbackId, "Nível atualizado para " + nivel);
                } else {
                    bot.responderCallback(callbackId, "Nível inválido");
                }
                return;
            }

            if (data.startsWith("fonte:toggle:")) {
                String fonte = data.substring("fonte:toggle:".length()).toUpperCase();
                toggleFonte(chatId, fonte);
                bot.responderCallback(callbackId, "Fonte '" + fonte + "' alternada");
                return;
            }

            if (data.startsWith("cmd:")) {
                String alvo = data.substring("cmd:".length()).toLowerCase();
                bot.responderCallback(callbackId, null);
                switch (alvo) {
                    case "status" -> cmdStatus(chatId);
                    case "historico" -> cmdHistorico(chatId, "");
                    case "fontes" -> cmdFontes(chatId);
                    case "nivel" -> cmdNivel(chatId, "", chatId);
                    case "verificar" -> cmdVerificar(chatId);
                    case "ajuda" -> cmdAjuda(chatId);
                    case "start" -> cmdStart(chatId, firstName);
                    default -> { /* no-op */ }
                }
                return;
            }

            bot.responderCallback(callbackId, null);
        } catch (Exception e) {
            logger.error("Erro ao processar callback: {}", e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------------
    // Handlers de comandos
    // ---------------------------------------------------------------------

    private void cmdStart(long chatId, String firstName) {
        String nome = (firstName == null || firstName.isBlank()) ? "" : " " + firstName;
        String texto = """
                🤖 *Telegram Bot Hub*
                Olá%s! Estou monitorando alertas para você.

                Use os botões abaixo ou digite um comando:""".formatted(nome);

        List<List<Map<String, String>>> botoes = new ArrayList<>();
        botoes.add(List.of(
                botao("📊 Status", "cmd:status"),
                botao("📋 Histórico", "cmd:historico")
        ));
        botoes.add(List.of(
                botao("🌊 Fontes", "cmd:fontes"),
                botao("⚙️ Nível", "cmd:nivel")
        ));
        botoes.add(List.of(
                botao("🔍 Verificar agora", "cmd:verificar")
        ));

        bot.enviarComBotoes(chatId, texto, botoes);
    }

    private void cmdAjuda(long chatId) {
        String texto = """
                📖 *Comandos disponíveis*

                /start - Mensagem inicial e menu rápido
                /ajuda - Esta lista de comandos
                /status - Status atual dos monitores
                /historico [N] - Últimos N alertas (padrão 5, máx 10)
                /fontes - Lista de fontes monitoradas
                /nivel [INFO|AVISO|CRITICO] - Define o nível mínimo de alertas
                /verificar - Dispara uma verificação imediata
                """;
        bot.enviarMarkdown(chatId, texto);
    }

    private void cmdStatus(long chatId) {
        StringBuilder sb = new StringBuilder();
        sb.append("📊 *Status dos Monitores*\n");
        sb.append("🟢 Defesa Civil RS — ativo\n");
        sb.append("🟢 INMET — ativo\n");

        try {
            List<Alerta> ultimos = alertaService.ultimosAlertas(1);
            if (ultimos != null && !ultimos.isEmpty()) {
                Alerta a = ultimos.get(0);
                String dataFmt = a.getDataHora() != null ? a.getDataHora().format(FMT) : "—";
                String fonte = a.getFonte() != null ? a.getFonte() : "—";
                sb.append("📅 Último alerta: ").append(dataFmt).append(" (").append(fonte).append(")");
            } else {
                sb.append("📅 Nenhum alerta registrado ainda.");
            }
        } catch (Exception e) {
            logger.warn("Falha ao obter último alerta: {}", e.getMessage());
            sb.append("📅 (não foi possível consultar o último alerta)");
        }
        bot.enviarMarkdown(chatId, sb.toString());
    }

    private void cmdHistorico(long chatId, String args) {
        int n = 5;
        if (args != null && !args.isBlank()) {
            try {
                n = Integer.parseInt(args.trim());
            } catch (NumberFormatException ignored) {
                // mantém padrão
            }
        }
        if (n < 1) {
            n = 1;
        }
        if (n > 10) {
            n = 10;
        }

        List<Alerta> alertas;
        try {
            alertas = alertaService.ultimosAlertas(n);
        } catch (Exception e) {
            logger.warn("Falha ao buscar histórico: {}", e.getMessage());
            bot.enviarMensagem(chatId, "⚠️ Não foi possível consultar o histórico agora.");
            return;
        }

        if (alertas == null || alertas.isEmpty()) {
            bot.enviarMarkdown(chatId, "📋 *Histórico de Alertas*\n\nNenhum alerta registrado.");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("📋 *Últimos ").append(alertas.size()).append(" alertas*\n\n");
        for (Alerta a : alertas) {
            String emoji = emojiNivel(a.getNivel());
            String titulo = a.getTitulo() != null ? a.getTitulo() : "(sem título)";
            String fonte = a.getFonte() != null ? a.getFonte() : "—";
            String data = a.getDataHora() != null ? a.getDataHora().format(FMT) : "—";
            sb.append(emoji).append(" ").append(titulo).append("\n");
            sb.append("   _").append(fonte).append("_ • ").append(data).append("\n\n");
        }
        bot.enviarMarkdown(chatId, sb.toString().trim());
    }

    private void cmdFontes(long chatId) {
        ChatConfig cfg = obterOuCriarConfig(chatId);
        Set<String> ativas = parseFontes(cfg.getFontesAtivas());

        StringBuilder sb = new StringBuilder();
        sb.append("🌊 *Fontes monitoradas*\n\n");
        List<List<Map<String, String>>> botoes = new ArrayList<>();
        for (String fonte : FONTES_DISPONIVEIS) {
            boolean ativa = ativas.isEmpty() || ativas.contains(fonte);
            sb.append(ativa ? "🟢 " : "⚪ ").append(fonte).append(ativa ? " — ativa\n" : " — inativa\n");
            String label = (ativa ? "🔕 Desativar " : "🔔 Ativar ") + fonte;
            botoes.add(List.of(botao(label, "fonte:toggle:" + fonte)));
        }
        sb.append("\nUse os botões para alternar.");
        bot.enviarComBotoes(chatId, sb.toString(), botoes);
    }

    private void cmdNivel(long chatId, String args, long chatAlvo) {
        if (args == null || args.isBlank()) {
            ChatConfig cfg = obterOuCriarConfig(chatAlvo);
            String atual = cfg.getNivelMinimo() != null ? cfg.getNivelMinimo() : "INFO";

            String texto = "⚙️ *Nível mínimo de alertas*\n\nAtual: *" + atual + "*\nEscolha o novo nível:";
            List<List<Map<String, String>>> botoes = new ArrayList<>();
            botoes.add(List.of(
                    botao("ℹ️ INFO", "nivel:INFO"),
                    botao("⚠️ AVISO", "nivel:AVISO"),
                    botao("🚨 CRITICO", "nivel:CRITICO")
            ));
            bot.enviarComBotoes(chatId, texto, botoes);
            return;
        }

        String nivel = args.trim().toUpperCase();
        if (!NIVEIS_VALIDOS.contains(nivel)) {
            bot.enviarMensagem(chatId, "❓ Nível inválido. Use: INFO, AVISO ou CRITICO.");
            return;
        }
        aplicarNivel(chatAlvo, nivel);
        bot.enviarMarkdown(chatId, "✅ Nível mínimo definido como *" + nivel + "*.");
    }

    private void cmdVerificar(long chatId) {
        bot.enviarMensagem(chatId,
                "🔍 Verificação iniciada... Você receberá um alerta se houver novidades.");
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private void aplicarNivel(long chatId, String nivel) {
        try {
            ChatConfig cfg = obterOuCriarConfig(chatId);
            cfg.setNivelMinimo(nivel);
            alertaService.salvarChat(cfg);
        } catch (Exception e) {
            logger.error("Falha ao salvar nível para chat {}: {}", chatId, e.getMessage());
        }
    }

    private void toggleFonte(long chatId, String fonte) {
        try {
            ChatConfig cfg = obterOuCriarConfig(chatId);
            Set<String> ativas = parseFontes(cfg.getFontesAtivas());
            if (ativas.isEmpty()) {
                // se nenhuma configuração explícita, considera todas ativas e desliga a alterada
                ativas = new HashSet<>(FONTES_DISPONIVEIS);
            }
            if (ativas.contains(fonte)) {
                ativas.remove(fonte);
            } else {
                ativas.add(fonte);
            }
            cfg.setFontesAtivas(String.join(",", ativas));
            alertaService.salvarChat(cfg);
        } catch (Exception e) {
            logger.error("Falha ao alternar fonte {} para chat {}: {}", fonte, chatId, e.getMessage());
        }
    }

    private ChatConfig obterOuCriarConfig(long chatId) {
        try {
            List<ChatConfig> chats = alertaService.listarChats();
            if (chats != null) {
                for (ChatConfig c : chats) {
                    if (c.getChatId() != null && c.getChatId() == chatId) {
                        return c;
                    }
                }
            }
        } catch (Exception e) {
            logger.warn("Falha ao listar chats: {}", e.getMessage());
        }
        ChatConfig novo = new ChatConfig();
        novo.setChatId(chatId);
        novo.setAtivo(true);
        novo.setNivelMinimo("INFO");
        novo.setFontesAtivas(String.join(",", FONTES_DISPONIVEIS));
        return novo;
    }

    private static Set<String> parseFontes(String csv) {
        Set<String> set = new HashSet<>();
        if (csv == null || csv.isBlank()) {
            return set;
        }
        for (String item : csv.split(",")) {
            String trimmed = item.trim().toUpperCase();
            if (!trimmed.isEmpty()) {
                set.add(trimmed);
            }
        }
        return set;
    }

    private static String emojiNivel(String nivel) {
        if (nivel == null) {
            return "ℹ️";
        }
        return switch (nivel.toUpperCase()) {
            case "CRITICO", "CRÍTICO" -> "🚨";
            case "AVISO" -> "⚠️";
            default -> "ℹ️";
        };
    }

    private static Map<String, String> botao(String texto, String callbackData) {
        return Map.of("text", texto, "callback_data", callbackData);
    }
}
