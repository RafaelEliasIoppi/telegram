// Main específico para GitHub Actions
package telegram.teste;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

import telegram.teste.service.GmailMonitor;

@SpringBootApplication
public class TesteActionApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(TesteActionApplication.class);
        app.setAdditionalProfiles("ci"); // ativa o profile "ci"
        ApplicationContext ctx = app.run(args);

        System.out.println("⚙️ Execução para GitHub Actions iniciada!");
        GmailMonitor gmailMonitor = ctx.getBean(GmailMonitor.class);
        gmailMonitor.verificarEmailsUltimoAssunto();

        int exitCode = SpringApplication.exit(ctx);
        System.exit(exitCode);
    }
}
