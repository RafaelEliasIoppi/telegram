package telegram.teste;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

@SpringBootApplication
public class TesteApplication {

    public static void main(String[] args) {
        ApplicationContext ctx = SpringApplication.run(TesteApplication.class, args);
        System.out.println("🚀 Aplicação iniciada com sucesso!");

      

        
    }
}