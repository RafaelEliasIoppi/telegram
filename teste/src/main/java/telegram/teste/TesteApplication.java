package telegram.teste;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Classe principal da aplicação Spring Boot.
 * 
 * @author Rafael
 */
@SpringBootApplication

public class TesteApplication {

    public static void main(String[] args) {
        SpringApplication.run(TesteApplication.class, args);
        System.out.println("🚀 Aplicação iniciada com sucesso!");
    }
    @Bean
public CommandLineRunner run(ApplicationContext ctx) {
    return args -> {
        if (args.length > 0 && args[0].equals("buscar-defesa-civil")) {
            DefesaCivilMonitor monitor = ctx.getBean(DefesaCivilMonitor.class);
            monitor.buscarAvisos(); // aqui chama o método novo
        }
    };
}

}
