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
    try {
        File file = new File(ARQUIVO_ASSUNTO);
        logger.info("Salvando assunto no arquivo: {}", file.getAbsolutePath());

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
  /**
 * Carrega o último assunto salvo em arquivo.
 */
public String carregarUltimoAssunto() {
    try {
        File file = new File(ARQUIVO_ASSUNTO);
        logger.info("Tentando carregar assunto do arquivo: {}", file.getAbsolutePath());

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String linha = br.readLine();
            if (linha != null && !linha.trim().isEmpty()) {
                logger.info("Assunto carregado: {}", linha.trim().toLowerCase());
                return linha.trim().toLowerCase();
            } else {
                logger.warn("Arquivo existe mas está vazio, usando fallback 'alerta'");
                return "alerta";
            }
        }
    } catch (IOException e) {
        logger.warn("Nenhum assunto salvo, usando fallback 'alerta'. Erro: {}", e.getMessage());
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
        logger.info("Conectando ao Gmail...");
        store.connect("imap.gmail.com", username, appPassword);
        logger.info("Conectado. Abrindo INBOX...");
        try (Folder inbox = store.getFolder("INBOX")) {
            inbox.open(Folder.READ_ONLY);

            int total = inbox.getMessageCount();
            int start = Math.max(1, total - 50); // 🔹 pega apenas os últimos 50
            logger.info("Total de mensagens: {}. Buscando da {} até {}", total, start, total);

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
                telegramService.sendMessage(
                    "ℹ️ Nenhum e-mail novo para o assunto '" + assunto + "'.",
                    destinatario
                );
            }
        } else {
            logger.info("Nenhum e-mail encontrado para o assunto '{}'.", assunto);
            telegramService.sendMessage(
                "🔍 Nenhum e-mail encontrado para o assunto '" + assunto + "'.",
                destinatario
            );
        }
    } catch (Exception e) {
        telegramService.sendMessage("❌ Erro ao consultar Gmail: " + e.getMessage(), destinatario);
    }
}

}
