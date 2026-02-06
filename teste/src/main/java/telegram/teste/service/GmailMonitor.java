package telegram.teste.service;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
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

@Component
public class GmailMonitor {
    private static final Logger logger = LoggerFactory.getLogger(GmailMonitor.class);
    private static final String ARQUIVO_ASSUNTO = "ultimo_assunto.txt";

    @Autowired
    private TelegramService telegramService;

    @Value("${telegram.chat.id}")
    private String destinatario;

    @Value("${gmail.username}")
    private String username;

    @Value("${gmail.app.password}")
    private String appPassword;

    private String ultimoEmailId = "";

    /**
     * Salva o último assunto em arquivo.
     */
    public void salvarUltimoAssunto(String assunto) {
        try (FileWriter fw = new FileWriter(ARQUIVO_ASSUNTO)) {
            fw.write(assunto.trim().toLowerCase());
        } catch (IOException e) {
            logger.error("Erro ao salvar assunto", e);
        }
    }

    /**
     * Carrega o último assunto salvo em arquivo.
     */
    public String carregarUltimoAssunto() {
        try (BufferedReader br = new BufferedReader(new FileReader(ARQUIVO_ASSUNTO))) {
            String linha = br.readLine();
            return (linha != null) ? linha.trim().toLowerCase() : "alerta";
        } catch (IOException e) {
            logger.warn("Nenhum assunto salvo, usando fallback 'alerta'");
            return "alerta";
        }
    }

    /**
     * Busca e-mails por assunto flexível e retorna dados completos (inclui corpo).
     */
    public List<Map<String, String>> buscarEmailsPorAssunto(String assuntoNormalizado) throws Exception {
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        Session session = Session.getInstance(props);
        List<Map<String, String>> resultado = new ArrayList<>();

        try (Store store = session.getStore("imaps")) {
            store.connect("imap.gmail.com", username, appPassword);
            try (Folder inbox = store.getFolder("INBOX")) {
                inbox.open(Folder.READ_ONLY);
                Message[] messages = inbox.getMessages();

                for (Message msg : messages) {
                    String subject = msg.getSubject();
                    if (subject != null && subject.toLowerCase().contains(assuntoNormalizado)) {
                        Map<String, String> dados = new HashMap<>();
                        String[] ids = msg.getHeader("Message-ID");
                        dados.put("id", (ids != null && ids.length > 0) ? ids[0] : subject);
                        dados.put("assunto", subject);
                        dados.put("remetente", msg.getFrom()[0].toString());
                        dados.put("data", msg.getReceivedDate() != null ? msg.getReceivedDate().toString() : "");

                        // 🔹 pega o corpo do e-mail
                        Object content = msg.getContent();
                        if (content instanceof String) {
                            dados.put("conteudo", (String) content);
                        } else {
                            dados.put("conteudo", "(conteúdo não textual)");
                        }

                        resultado.add(dados);
                    }
                }
            }
        }
        return resultado;
    }

    /**
     * Verifica se há novos e-mails para o último assunto salvo e envia alerta ao Telegram.
     */
    public void verificarEmailsUltimoAssunto() {
        try {
            String assunto = carregarUltimoAssunto();
            List<Map<String, String>> emails = buscarEmailsPorAssunto(assunto);

            if (!emails.isEmpty()) {
                Map<String, String> ultimo = emails.get(emails.size() - 1);
                String id = ultimo.get("id");

                if (!id.equals(ultimoEmailId)) {
                    ultimoEmailId = id;
                    telegramService.sendMessage(
                        "📧 Novo e-mail:\nDe: " + ultimo.get("remetente") +
                        "\nAssunto: " + ultimo.get("assunto"),
                        destinatario
                    );
                } else {
                    logger.info("Nenhum e-mail novo para o assunto '{}'.", assunto);
                }
            } else {
                logger.info("Nenhum e-mail encontrado para o assunto '{}'.", assunto);
            }
        } catch (Exception e) {
            telegramService.sendMessage("❌ Erro ao consultar Gmail: " + e.getMessage(), destinatario);
        }
    }
}
