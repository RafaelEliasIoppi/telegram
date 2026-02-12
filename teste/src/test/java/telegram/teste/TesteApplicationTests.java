package telegram.teste;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import telegram.teste.config.TelegramServiceMockConfig;

@SpringBootTest(classes = TesteApplication.class)
@Import(TelegramServiceMockConfig.class)
class TesteApplicationTests {

    @Test
    void contextLoads() {
        // Se o contexto subir sem erro, o teste passa
    }
}
