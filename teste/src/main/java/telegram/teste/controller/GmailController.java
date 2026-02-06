package telegram.teste.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import telegram.teste.service.GmailMonitor;

import jakarta.mail.Message;
import java.util.*;

@RestController
@RequestMapping("/gmail")
public class GmailController {

    @Autowired
    private GmailMonitor gmailMonitor;

    /**
     * Endpoint para verificar e-mails do SNT e retornar em JSON.
     * Exemplo: GET /gmail/check
     */
    @GetMapping("/check")
    public List<Map<String, String>> checkEmails() {
        List<Map<String, String>> emails = new ArrayList<>();

        try {
            // 🔹 Usa método buscarEmailsSNT() do GmailMonitor
            Message[] messages = gmailMonitor.buscarEmailsSNT();

            // 🔹 Itera sobre os e-mails encontrados
            for (Message msg : messages) {
                Map<String, String> emailData = new HashMap<>();
                emailData.put("remetente", msg.getFrom()[0].toString());
                emailData.put("assunto", msg.getSubject());
                emailData.put("data", msg.getReceivedDate() != null
                        ? msg.getReceivedDate().toString()
                        : "sem data");
                emails.add(emailData);
            }

            // 🔹 Se não encontrou nada, retorna aviso
            if (emails.isEmpty()) {
                Map<String, String> aviso = new HashMap<>();
                aviso.put("info", "Nenhum e-mail do SNT encontrado.");
                emails.add(aviso);
            }

        } catch (Exception e) {
            Map<String, String> erro = new HashMap<>();
            erro.put("erro", e.getMessage());
            emails.add(erro);
        }

        return emails;
    }
}
