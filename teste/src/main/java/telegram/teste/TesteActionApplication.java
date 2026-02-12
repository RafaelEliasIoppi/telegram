// Main específico para GitHub Actions
package telegram.teste;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

import telegram.teste.service.GmailMonitor;

@SpringBootApplication
public class TesteActionApplication {

    public static void main(String[] args) {
        // Inicializa a aplicação Spring Boot com o profile "ci"
        SpringApplication app = new SpringApplication(TesteActionApplication.class);
        app.setAdditionalProfiles("ci");
        ApplicationContext ctx = app.run(args);

        System.out.println("⚙️ Execução para GitHub Actions iniciada!");

        // Executa apenas uma vez a verificação de e-mails
        GmailMonitor gmailMonitor = ctx.getBean(GmailMonitor.class);
        gmailMonitor.verificarEmailsUltimoAssunto();

        // Encerra a aplicação após a execução
        int exitCode = SpringApplication.exit(ctx);
        System.exit(exitCode);
    }
}
