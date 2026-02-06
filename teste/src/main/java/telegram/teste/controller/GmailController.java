package telegram.teste.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import telegram.teste.service.GmailMonitor;

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
            // 🔹 Usa método buscarEmailsSNT() do GmailMonitor (já retorna dados prontos)
            emails = gmailMonitor.buscarEmailsSNT();

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
