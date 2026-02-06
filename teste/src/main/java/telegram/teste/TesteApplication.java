package telegram.teste;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
import telegram.teste.service.DefesaCivilMonitor;

@SpringBootApplication
public class TesteApplication {

    public static void main(String[] args) {
        ApplicationContext ctx = SpringApplication.run(TesteApplication.class, args);
        System.out.println("🚀 Aplicação iniciada com sucesso!");

        // Se rodar com argumento "buscar-defesa-civil", chama o método
        if (args.length > 0 && args[0].equals("buscar-defesa-civil")) {
            DefesaCivilMonitor monitor = ctx.getBean(DefesaCivilMonitor.class);
            monitor.buscarAvisos();
        }
    }
}
