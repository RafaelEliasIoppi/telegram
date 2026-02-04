package telegram.teste;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import telegram.teste.service.TelegramService;

@Component
public class ScheduledTasks {

    @Autowired
    private TelegramService telegramService;

    @Value("${telegram.chat.id}")
    private String destinatario;

    /**
     * Envia uma mensagem todos os dias às 9h da manhã.
     */
    @Scheduled(cron = "0 0 9 * * *")
    public void avisoDiario() {
        telegramService.sendMessage("☀️ Bom dia, Rafael! Aviso automático das 9h.", destinatario);
    }

    /**
     * Envia uma mensagem a cada 30 minutos.
     */
    @Scheduled(fixedRate = 1800000)
    public void avisoPeriodico() {
        telegramService.sendMessage("🔔 Lembrete periódico: verifique o sistema.", destinatario);
    }

    /**
     * Envia uma mensagem toda segunda-feira às 10h.
     */
    @Scheduled(cron = "0 0 10 * * MON")
    public void avisoSemanal() {
        telegramService.sendMessage("📅 Aviso semanal: reunião de alinhamento às 10h.", destinatario);
    }
}
