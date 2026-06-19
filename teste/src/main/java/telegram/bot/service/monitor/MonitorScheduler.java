package telegram.bot.service.monitor;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import telegram.bot.domain.Alerta;
import telegram.bot.domain.NivelAlerta;
import telegram.bot.service.AlertaService;
import telegram.bot.service.bot.TelegramBotService;

/**
 * Orquestrador agendado que executa todos os {@link FonteMonitor}
 * registrados como bean, salva alertas novos via {@link AlertaService}
 * e dispara mensagens via {@link TelegramBotService} para os chats
 * configurados.
 *
 * <p>O Spring injeta automaticamente todas as implementações de
 * {@link FonteMonitor} encontradas no contexto. A frequência de execução
 * é controlada pela propriedade {@code defesacivil.fixedRate} (default
 * 600.000 ms = 10 minutos) e o {@code @EnableScheduling} é fornecido por
 * {@link telegram.bot.config.SchedulingConfig}.</p>
 */
@Service
@Slf4j
public class MonitorScheduler {

    private static final int LIMITE_TELEGRAM = 4096;
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    @Autowired
    private List<FonteMonitor> monitores;

    @Autowired
    private AlertaService alertaService;

    @Autowired
    private TelegramBotService telegramService;

    @PostConstruct
    public void logMonitores() {
        log.info("Monitores registrados: {}",
                monitores.stream().map(FonteMonitor::getNome).toList());
    }

    /**
     * Executa o ciclo completo: para cada monitor ativo coleta alertas,
     * registra os novos e envia notificações Telegram.
     *
     * <p>O agendamento (intervalo e disparo) é controlado por
     * {@link telegram.bot.config.SchedulingConfig}, que usa um trigger
     * dinâmico relendo o intervalo configurável da UI a cada ciclo.</p>
     */
    public void executarVerificacao() {
        log.info("Iniciando verificação de todas as fontes...");
        for (FonteMonitor monitor : monitores) {
            if (!monitor.isAtivo()) {
                log.debug("Monitor {} inativo, pulando", monitor.getNome());
                continue;
            }
            try {
                List<Alerta> alertas = monitor.verificar();
                log.info("Monitor {} encontrou {} alertas", monitor.getNome(), alertas.size());
                for (Alerta alerta : alertas) {
                    Optional<Alerta> salvo = alertaService.registrarSeNovo(alerta);
                    if (salvo.isPresent()) {
                        log.info("Novo alerta de {}: {}", alerta.getFonte(), alerta.getTitulo());
                        enviarParaChats(salvo.get());
                    } else {
                        log.debug("Alerta duplicado ignorado: {}", alerta.getTitulo());
                    }
                }
            } catch (Exception e) {
                log.error("Erro no monitor {}: {}", monitor.getNome(), e.getMessage());
            }
        }
    }

    /**
     * Formata o alerta em Markdown e envia para todos os chats
     * elegíveis (definidos pela política de {@link AlertaService}).
     */
    private void enviarParaChats(Alerta alerta) {
        List<Long> chatIds = alertaService.chatIdsParaAlerta(alerta);
        if (chatIds.isEmpty()) {
            log.warn("Nenhum chat configurado para receber alerta de {}", alerta.getFonte());
            return;
        }

        NivelAlerta nivel = resolverNivel(alerta.getNivel());
        String dataFormatada = alerta.getDataHora() == null
                ? ""
                : alerta.getDataHora().format(FMT);

        // Layout do aviso (Markdown V1 do Telegram):
        //   {emoji} {NÍVEL} · {fonte}      ← cabeçalho de severidade
        //   *{título}*                     ← título em negrito
        //   {conteúdo}
        //   ━━━━━━━━━━━━━━━                ← divisor
        //   {bolinha} {nível}  ·  🕒 {data}
        // Título e fonte são escapados (V1); o conteúdo NÃO é escapado aqui
        // porque alguns monitores (ex.: Gmail) já produzem Markdown intencional.
        String mensagem = String.format("""
                %s *%s · %s*

                *%s*

                %s

                ━━━━━━━━━━━━━━━
                %s _%s_  ·  🕒 %s
                """,
                nivel.emoji(),
                nivel.rotulo(),
                escapeMarkdown(descricaoFonte(alerta.getFonte())),
                escapeMarkdown(nullSafe(alerta.getTitulo())),
                nullSafe(alerta.getConteudo()),
                nivel.bolinha(),
                nivel.rotulo().toLowerCase(),
                dataFormatada
        );

        if (mensagem.length() > LIMITE_TELEGRAM) {
            mensagem = mensagem.substring(0, LIMITE_TELEGRAM - 6);
            // Evita cortar no meio de uma sequência de escape ('\\*' -> '\\'),
            // o que deixaria uma barra solta e quebraria o Markdown.
            mensagem = removerEscapeIncompleto(mensagem) + "...";
        }

        String imagem = alerta.getImagemUrl();
        boolean temImagem = imagem != null && !imagem.isBlank()
                && imagem.matches("(?i)https?://.+");

        boolean algumEnviado = false;
        for (Long chatId : chatIds) {
            try {
                // A imagem vem PRIMEIRO (acima do texto), como mensagem própria e
                // best-effort: se a URL for inválida ou falhar, apenas registra log
                // e segue — o aviso de texto nunca é perdido por causa da imagem.
                if (temImagem) {
                    try {
                        telegramService.enviarFotoMarkdown(chatId, imagem, null);
                    } catch (Exception imgEx) {
                        log.warn("Imagem do alerta não pôde ser enviada para chat {}: {}",
                                chatId, imgEx.getMessage());
                    }
                }

                // O texto do aviso é SEMPRE enviado completo como mensagem própria
                // (logo após a imagem), sem depender dela. Assim a legenda da foto
                // não trunca o texto (limite de 1024 chars do Telegram).
                telegramService.enviarMarkdown(chatId, mensagem);
                algumEnviado = true;
            } catch (Exception e) {
                log.error("Erro ao enviar alerta para chat {}: {}", chatId, e.getMessage());
            }
        }

        // Só marca como enviado se pelo menos um chat recebeu, evitando alertas
        // marcados como entregues que nunca chegaram.
        if (algumEnviado) {
            alertaService.marcarEnviado(alerta.getId());
        }
    }

    /**
     * Resolve a string de nível para o enum, retornando {@link NivelAlerta#INFO}
     * em caso de valor desconhecido ou nulo.
     */
    private NivelAlerta resolverNivel(String nivel) {
        if (nivel == null || nivel.isBlank()) {
            return NivelAlerta.INFO;
        }
        try {
            return NivelAlerta.valueOf(nivel.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Nível desconhecido '{}', usando INFO", nivel);
            return NivelAlerta.INFO;
        }
    }

    /**
     * Tradução amigável da fonte para exibição na mensagem do Telegram.
     */
    private String descricaoFonte(String fonte) {
        if (fonte == null) {
            return "Desconhecida";
        }
        return switch (fonte) {
            case "DEFESA_CIVIL_RS" -> "Defesa Civil RS";
            case "INMET" -> "INMET";
            case "GMAIL_URGENCIA_RENAL" -> "Gmail — Urgência Renal";
            default -> fonte;
        };
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }

    /**
     * Escapa caracteres reservados do Markdown V1 do Telegram em texto puro
     * (usado no título e na fonte, que entram em contexto formatado).
     */
    private String escapeMarkdown(String texto) {
        if (texto == null || texto.isEmpty()) {
            return texto == null ? "" : texto;
        }
        return texto.replace("\\", "\\\\")
                .replace("_", "\\_")
                .replace("*", "\\*")
                .replace("`", "\\`")
                .replace("[", "\\[");
    }

    /**
     * Remove uma barra de escape solta no fim da string (resultado de um
     * truncamento que cortou '\\*' ou '\\_' ao meio), preservando barras
     * já completas ('\\\\').
     */
    private String removerEscapeIncompleto(String s) {
        if (s == null || s.isEmpty()) return s;
        int barras = 0;
        for (int i = s.length() - 1; i >= 0 && s.charAt(i) == '\\'; i--) {
            barras++;
        }
        // Número ímpar de barras no fim => a última está incompleta.
        if (barras % 2 != 0) {
            return s.substring(0, s.length() - 1);
        }
        return s;
    }
}
