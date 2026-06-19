package telegram.bot.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@Configuration
public class TelegramBotConfig {

    @Bean
    public RestTemplate restTemplate() {
        // Timeouts curtos para que NENHUMA chamada ao Telegram trave o startup.
        // O webhook é registrado em TelegramBotService.init() (@PostConstruct),
        // ou seja, durante o boot — sem teto de tempo, uma rede ruim seguraria o
        // contexto por dezenas de segundos. connect 5s / read 10s limita o pior caso.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(10));
        return new RestTemplate(factory);
    }
}
