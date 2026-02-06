package telegram.teste.service;

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
     * Busca e-mails do SNT (remetente ou assunto).
     */
    public Message[] buscarEmailsSNT() throws Exception {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");

        Session session = Session.getInstance(props);
        Store store = null;
        Folder inbox = null;

        try {
            store = session.getStore("imaps");
            store.connect("imap.gmail.com", username, appPassword);

            inbox = store.getFolder("INBOX");
            inbox.open(Folder.READ_ONLY);

            SearchTerm filtro = new OrTerm(
                    new FromStringTerm("SNT"),
                    new SubjectTerm("SNT")
            );

            return inbox.search(filtro);

        } finally {
            if (inbox != null && inbox.isOpen()) {
                inbox.close(false);
            }
            if (store != null && store.isConnected()) {
                store.close();
            }
        }
    }

    /**
     * Verifica se há novos e-mails do SNT e envia alerta ao Telegram.
     */
    public void verificarEmailsSNT() {
        try {
            Message[] messages = buscarEmailsSNT();

            if (messages.length > 0) {
                Message ultimo = messages[messages.length - 1];
                String[] ids = ultimo.getHeader("Message-ID");
                String id = (ids != null && ids.length > 0) ? ids[0] : ultimo.getSubject();

                if (!id.equals(ultimoEmailId)) {
                    ultimoEmailId = id;
                    String assunto = ultimo.getSubject();
                    String remetente = ultimo.getFrom()[0].toString();

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
