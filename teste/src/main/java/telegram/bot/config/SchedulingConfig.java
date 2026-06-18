package telegram.bot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Habilita o suporte a {@code @Scheduled} no contexto Spring do bot.
 *
 * <p>Mantida em uma classe própria para isolar a configuração de
 * scheduling do bootstrap principal, facilitando substituição em testes.</p>
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}
