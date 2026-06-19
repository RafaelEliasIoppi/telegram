package telegram.bot.config;

import java.time.Instant;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import telegram.bot.service.GmailConfigService;
import telegram.bot.service.monitor.MonitorScheduler;

/**
 * Habilita o suporte a agendamento no contexto Spring do bot e registra a
 * verificação periódica de todas as fontes usando um <em>trigger dinâmico</em>.
 *
 * <p>Diferente de um {@code @Scheduled(fixedRate=...)} (que congela o intervalo
 * no boot a partir de uma propriedade), aqui o intervalo é relido do banco a
 * cada ciclo via {@link GmailConfigService#getIntervaloVerificacaoMs()}. Assim,
 * alterar o campo "Intervalo de verificação" na UI (/gmail) passa a ter efeito
 * imediato, sem reiniciar a aplicação.</p>
 */
@Configuration
@EnableScheduling
public class SchedulingConfig implements SchedulingConfigurer {

    /** Atraso antes do primeiro ciclo, para o contexto subir por completo. */
    private static final long INITIAL_DELAY_MS = 30_000L;

    private final MonitorScheduler monitorScheduler;
    private final GmailConfigService gmailConfigService;

    public SchedulingConfig(MonitorScheduler monitorScheduler,
                            GmailConfigService gmailConfigService) {
        this.monitorScheduler = monitorScheduler;
        this.gmailConfigService = gmailConfigService;
    }

    @Override
    public void configureTasks(ScheduledTaskRegistrar registrar) {
        registrar.addTriggerTask(
                monitorScheduler::executarVerificacao,
                triggerContext -> {
                    Instant ultimaConclusao = triggerContext.lastCompletion();
                    long intervaloMs = gmailConfigService.getIntervaloVerificacaoMs();
                    if (ultimaConclusao == null) {
                        return Instant.now().plusMillis(INITIAL_DELAY_MS);
                    }
                    return ultimaConclusao.plusMillis(intervaloMs);
                });
    }
}
