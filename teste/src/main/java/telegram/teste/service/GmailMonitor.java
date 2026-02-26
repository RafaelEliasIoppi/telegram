package telegram.teste.service;

import java.io.BufferedReader;
import java.io.File;
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

    @Autowired
    private TelegramService telegramService;

    @Value("${gmail.subject.file}") 
    private String arquivoAssunto;

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
        try {
           File file = new File(arquivoAssunto);
            File parent = file.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }

            try (FileWriter fw = new FileWriter(file)) {
                fw.write(assunto.trim().toLowerCase());
                logger.info("Assunto '{}' salvo com sucesso.", assunto.trim().toLowerCase());
            }
        } catch (IOException e) {
            logger.error("Erro ao salvar assunto", e);
        }
    }

    /**
     * Carrega o último assunto salvo em arquivo.
     */
    public String carregarUltimoAssunto() {
        try {
            File file = new File(arquivoAssunto);
            if (!file.exists()) return "alerta";

            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String linha = br.readLine();
                return (linha != null && !linha.trim().isEmpty()) ? linha.trim().toLowerCase() : "alerta";
            }
        } catch (IOException e) {
            return "alerta";
        }
    }

    /**
     * Busca e-mails por assunto flexível.
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

                int total = inbox.getMessageCount();
                int start = Math.max(1, total - 50); 
                Message[] messages = inbox.getMessages(start, total);

                for (Message msg : messages) {
                    String subject = msg.getSubject();
                    if (subject != null && subject.toLowerCase().contains(assuntoNormalizado)) {
                        Map<String, String> dados = new HashMap<>();
                        String[] ids = msg.getHeader("Message-ID");
                        dados.put("id", (ids != null && ids.length > 0) ? ids[0] : subject);
                        dados.put("assunto", subject);
                        dados.put("remetente", msg.getFrom()[0].toString());
                        dados.put("data", msg.getReceivedDate() != null ? msg.getReceivedDate().toString() : "");

                        Object content = msg.getContent();
                        dados.put("conteudo", (content instanceof String) ? (String) content : "(conteúdo não textual)");

                        resultado.add(dados);
                    }
                }
            }
        }
        return resultado;
    }

    /**
     * Verifica e envia alerta com formatação Markdown.
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
                    
                    // Formatação Markdown para o novo e-mail
                    String msgTelegram = "🔔 *NOVO E-MAIL DETECTADO*\n" +
                                         "------------------------------------------\n" +
                                         "👤 *De:* " + ultimo.get("remetente") + "\n" +
                                         "📌 *Assunto:* " + ultimo.get("assunto") + "\n" +
                                         "------------------------------------------";
                    
                    telegramService.sendMessage(msgTelegram, destinatario);
                } else {
                    logger.info("Nenhum e-mail novo para o assunto '{}'.", assunto);
                    // Opcional: remover este envio se não quiser spam de "nada novo"
                    telegramService.sendMessage("ℹ️ _Sem novas atualizações para:_ `" + assunto + "`", destinatario);
                }
            } else {
                telegramService.sendMessage("🔍 _Nenhum e-mail encontrado para o termo:_ `" + assunto + "`", destinatario);
            }
        } catch (Exception e) {
            telegramService.sendMessage("❌ *Erro ao consultar Gmail:* " + e.getMessage(), destinatario);
        }
    }
}