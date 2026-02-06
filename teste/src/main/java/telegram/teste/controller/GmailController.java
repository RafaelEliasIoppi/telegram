package telegram.teste.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import telegram.teste.service.GmailMonitor;

@RestController
@RequestMapping("/gmail")
public class GmailController {

    @Autowired
    private GmailMonitor gmailMonitor;

    /**
     * Endpoint para buscar e-mails por assunto informado.
     * Exemplo: GET /gmail/check-assunto?assunto=alerta
     */
    @GetMapping("/check-assunto")
    public List<Map<String, String>> checkEmailsPorAssunto(@RequestParam String assunto) {
        List<Map<String, String>> emails = new ArrayList<>();
        try {
            String assuntoNormalizado = assunto.trim().toLowerCase();
            gmailMonitor.salvarUltimoAssunto(assuntoNormalizado);

            emails = gmailMonitor.buscarEmailsPorAssunto(assuntoNormalizado);
            if (emails.isEmpty()) {
                Map<String, String> aviso = new HashMap<>();
                aviso.put("info", "Nenhum e-mail encontrado com assunto: " + assunto);
                emails.add(aviso);
            }
        } catch (Exception e) {
            Map<String, String> erro = new HashMap<>();
            erro.put("erro", e.getMessage());
            emails.add(erro);
        }
        return emails;
    }

    /**
     * Endpoint para buscar e-mails usando o último assunto salvo.
     * Exemplo: GET /gmail/check-ultimo
     */
    @GetMapping("/check-ultimo")
    public List<Map<String, String>> checkEmailsUltimoAssunto() {
        List<Map<String, String>> emails = new ArrayList<>();
        try {
            String ultimoAssunto = gmailMonitor.carregarUltimoAssunto();
            emails = gmailMonitor.buscarEmailsPorAssunto(ultimoAssunto);
            if (emails.isEmpty()) {
                Map<String, String> aviso = new HashMap<>();
                aviso.put("info", "Nenhum e-mail encontrado para o último assunto salvo: " + ultimoAssunto);
                emails.add(aviso);
            }
        } catch (Exception e) {
            Map<String, String> erro = new HashMap<>();
            erro.put("erro", e.getMessage());
            emails.add(erro);
        }
        return emails;
    }

    /**
     * Endpoint para retornar o conteúdo completo de um e-mail pelo ID.
     * Exemplo: GET /gmail/conteudo?id=<Message-ID>
     */
    @GetMapping("/conteudo")
    public Map<String, String> getEmailContent(@RequestParam String id) {
        Map<String, String> resultado = new HashMap<>();
        try {
            String ultimoAssunto = gmailMonitor.carregarUltimoAssunto();
            List<Map<String, String>> emails = gmailMonitor.buscarEmailsPorAssunto(ultimoAssunto);

            for (Map<String, String> email : emails) {
                if (email.get("id").equals(id)) {
                    resultado.putAll(email);
                    resultado.put("conteudo", email.getOrDefault("conteudo", "(sem corpo disponível)"));
                    return resultado;
                }
            }
            resultado.put("info", "Nenhum e-mail encontrado com ID: " + id);
        } catch (Exception e) {
            resultado.put("erro", e.getMessage());
        }
        return resultado;
    }
}
