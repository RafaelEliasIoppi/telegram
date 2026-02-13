package telegram.teste;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

import io.github.cdimascio.dotenv.Dotenv;
import telegram.teste.service.DefesaCivilService;

@SpringBootApplication
public class DefesaCivilActionApplication {

    public static void main(String[] args) {
        // 🔹 Carrega as variáveis do .env (automaticamente adiciona ao System.getenv)
       
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        System.setProperty("telegram.bot.token", dotenv.get("TELEGRAM_BOT_TOKEN", ""));
        System.setProperty("telegram.chat.id", dotenv.get("TELEGRAM_CHAT_ID", ""));
        System.setProperty("gmail.username", dotenv.get("GMAIL_USERNAME", ""));
        System.setProperty("gmail.app.password", dotenv.get("GMAIL_APP_PASSWORD", ""));


        SpringApplication app = new SpringApplication(DefesaCivilActionApplication.class);
        app.setAdditionalProfiles("defesacivil");
        ApplicationContext ctx = app.run(args);

        System.out.println("⚙️ Execução Defesa Civil para GitHub Actions iniciada!");

        // 🔹 Executa apenas uma vez a verificação de avisos
        DefesaCivilService defesaCivilService = ctx.getBean(DefesaCivilService.class);
        defesaCivilService.verificarNovosAvisos();

        // Encerra a aplicação após a execução
        int exitCode = SpringApplication.exit(ctx);
        System.exit(exitCode);
    }
}
