package telegram.bot.service.monitor;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
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
     */
    @Scheduled(fixedRateString = "${monitor.fixedRate:${defesacivil.fixedRate:1800000}}", initialDelay = 30000)
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

        String mensagem = String.format("""
                %s *%s*

                %s

                📡 Fonte: %s
                📅 %s
                """,
                nivel.emoji(),
                nullSafe(alerta.getTitulo()),
                nullSafe(alerta.getConteudo()),
                descricaoFonte(alerta.getFonte()),
                dataFormatada
        );

        if (mensagem.length() > LIMITE_TELEGRAM) {
            mensagem = mensagem.substring(0, LIMITE_TELEGRAM - 6) + "...";
        }

        String imagem = alerta.getImagemUrl();
        boolean temImagem = imagem != null && !imagem.isBlank()
                && imagem.matches("(?i)https?://.+");

        for (Long chatId : chatIds) {
            try {
                if (temImagem) {
                    telegramService.enviarFotoMarkdown(chatId, imagem, mensagem);
                } else {
                    telegramService.enviarMarkdown(chatId, mensagem);
                }
                alertaService.marcarEnviado(alerta.getId());
            } catch (Exception e) {
                log.error("Erro ao enviar alerta para chat {}: {}", chatId, e.getMessage());
            }
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
            default -> fonte;
        };
    }

    private String nullSafe(String s) {
        return s == null ? "" : s;
    }
}
