package telegram.teste.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.search.FromStringTerm;
import jakarta.mail.search.OrTerm;
import jakarta.mail.search.SearchTerm;
import jakarta.mail.search.SubjectTerm;

@Component
public class GmailMonitor {

    private static final Logger logger = LoggerFactory.getLogger(GmailMonitor.class);

    @Autowired
    private TelegramService telegramService;

    @Value("${telegram.chat.id}")
    private String destinatario;

    @Value("${gmail.username}")
    private String username;

    @Value("${gmail.app.password}")
    private String appPassword; // senha de aplicativo gerada no Google

    private String ultimoEmailId = "";

    /**
     * Busca e-mails do SNT (remetente ou assunto) e retorna dados já extraídos.
     */
    public List<Map<String, String>> buscarEmailsSNT() throws Exception {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");

        Session session = Session.getInstance(props);
        List<Map<String, String>> resultado = new ArrayList<>();

        try (Store store = session.getStore("imaps")) {
            store.connect("imap.gmail.com", username, appPassword);

            try (Folder inbox = store.getFolder("INBOX")) {
                inbox.open(Folder.READ_ONLY);

                SearchTerm filtro = new OrTerm(
                        new FromStringTerm("SNT"),
                        new SubjectTerm("SNT")
                );

                Message[] messages = inbox.search(filtro);

                for (Message msg : messages) {
                    Map<String, String> dados = new HashMap<>();
                    String[] ids = msg.getHeader("Message-ID");
                    dados.put("id", (ids != null && ids.length > 0) ? ids[0] : msg.getSubject());
                    dados.put("assunto", msg.getSubject());
                    dados.put("remetente", msg.getFrom()[0].toString());
                    dados.put("data", msg.getReceivedDate() != null ? msg.getReceivedDate().toString() : "");
                    resultado.add(dados);
                }
            }
        }
        return resultado;
    }

    /**
     * Verifica se há novos e-mails do SNT e envia alerta ao Telegram.
     */
    public void verificarEmailsSNT() {
        try {
            List<Map<String, String>> emails = buscarEmailsSNT();

            if (!emails.isEmpty()) {
                Map<String, String> ultimo = emails.get(emails.size() - 1);
                String id = ultimo.get("id");

                if (!id.equals(ultimoEmailId)) {
                    ultimoEmailId = id;
                    String assunto = ultimo.get("assunto");
                    String remetente = ultimo.get("remetente");

                    telegramService.sendMessage(
                            "📧 Novo e-mail do SNT:\nDe: " + remetente + "\nAssunto: " + assunto,
                            destinatario
                    );
                    logger.info("Novo e-mail do SNT enviado ao Telegram: {}", assunto);
                } else {
                    logger.info("Nenhum e-mail novo do SNT.");
                }
            } else {
                logger.info("Nenhum e-mail do SNT encontrado.");
            }

        } catch (Exception e) {
            logger.error("Erro ao consultar Gmail", e);
            telegramService.sendMessage("❌ Erro ao consultar Gmail: " + e.getMessage(), destinatario);
        }
    }
}
