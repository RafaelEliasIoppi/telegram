package telegram.teste;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.context.annotation.Bean;

import telegram.teste.service.TelegramService;

@SpringBootApplication
@EnableScheduling
public class TesteApplication {

    public static void main(String[] args) {
        SpringApplication.run(TesteApplication.class, args);
    }

    @Bean
    public CommandLineRunner run(TelegramService telegramService) {
        return args -> {
            System.out.println("🚀 Iniciando execução automática via GitHub Actions...");
            
            // Executa a lógica de ler arquivo -> buscar CNN -> enviar Telegram
            telegramService.executarFluxoAutomatico();
            
            System.out.println("✅ Tarefa concluída. Encerrando processo...");
            // Opcional: System.exit(0); se quiser garantir que o runner pare imediatamente
        };
    }
}