// Main específico para GitHub Actions - Defesa Civil
package telegram.teste;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

import telegram.teste.service.DefesaCivilService;

@SpringBootApplication
public class DefesaCivilActionApplication {

    public static void main(String[] args) {
        // Inicializa a aplicação Spring Boot com o profile "ci"
        SpringApplication app = new SpringApplication(DefesaCivilActionApplication.class);
        app.setAdditionalProfiles("defesacivil");
        ApplicationContext ctx = app.run(args);

        System.out.println("⚙️ Execução Defesa Civil para GitHub Actions iniciada!");

        // Executa apenas uma vez a verificação de avisos
        DefesaCivilService defesaCivilService = ctx.getBean(DefesaCivilService.class);
        defesaCivilService.verificarNovosAvisos();

        // Encerra a aplicação após a execução
        int exitCode = SpringApplication.exit(ctx);
        System.exit(exitCode);
    }
}
