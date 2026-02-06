package telegram.teste;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

import telegram.teste.service.DefesaCivilMonitor;
import telegram.teste.service.TelegramService;

@SpringBootApplication
public class TesteApplication {

    public static void main(String[] args) {
        ApplicationContext ctx = SpringApplication.run(TesteApplication.class, args);
        System.out.println("🚀 Aplicação iniciada com sucesso!");

        DefesaCivilMonitor monitor = ctx.getBean(DefesaCivilMonitor.class);
        TelegramService telegram = ctx.getBean(TelegramService.class);

        String alerta = monitor.verificarAgora();
        if (!alerta.isEmpty()) {
            telegram.sendMessage("⚠️ Alerta Defesa Civil RS:\n" + alerta, telegram.chat.id);
        } else {
            telegram.sendMessage("ℹ️ Nenhum alerta novo encontrado na Defesa Civil RS.", telegram.chat.id);
        }

        // 🔹 Força o encerramento da aplicação após rodar
        System.exit(0);
    }
}
