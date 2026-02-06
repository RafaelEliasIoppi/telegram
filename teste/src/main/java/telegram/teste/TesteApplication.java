package telegram.teste;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

import telegram.teste.service.GmailMonitor;

@SpringBootApplication
public class TesteApplication {

    public static void main(String[] args) {
        ApplicationContext ctx = SpringApplication.run(TesteApplication.class, args);
        System.out.println("🚀 Aplicação iniciada com sucesso!");

        // 🔹 Executa a última pesquisa usando o assunto salvo em arquivo
        GmailMonitor gmailMonitor = ctx.getBean(GmailMonitor.class);
        gmailMonitor.verificarEmailsUltimoAssunto();

        System.exit(0);
    }
}