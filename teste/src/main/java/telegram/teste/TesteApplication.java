package telegram.teste;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.github.cdimascio.dotenv.Dotenv;

@SpringBootApplication
public class TesteApplication {

    public static void main(String[] args) {

             // 🔹 Carrega variáveis do .env
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        System.setProperty("telegram.bot.token", dotenv.get("TELEGRAM_BOT_TOKEN", ""));
        System.setProperty("telegram.chat.id", dotenv.get("TELEGRAM_CHAT_ID", ""));
        System.setProperty("gmail.username", dotenv.get("GMAIL_USERNAME", ""));
        System.setProperty("gmail.app.password", dotenv.get("GMAIL_APP_PASSWORD", ""));

        SpringApplication.run(TesteApplication.class, args);
        System.out.println("🚀 Aplicação iniciada com sucesso!");

      

        
    }
}