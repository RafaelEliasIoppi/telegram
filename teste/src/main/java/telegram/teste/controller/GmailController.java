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
import telegram.teste.service.TelegramService;

@RestController
@RequestMapping("/gmail")
public class GmailController {

    @Autowired
    private GmailMonitor gmailMonitor;

    @Autowired
    private TelegramService telegramService;

    /**
     * Busca e-mails por assunto e ENVIA para o Telegram formatado.
     */
    @GetMapping("/check-assunto")
    public List<Map<String, String>> checkEmailsPorAssunto(@RequestParam String assunto) {
        List<Map<String, String>> emails = new ArrayList<>();
        try {
            String assuntoNormalizado = assunto.trim().toLowerCase();
            gmailMonitor.salvarUltimoAssunto(assuntoNormalizado);

            emails = gmailMonitor.buscarEmailsPorAssunto(assuntoNormalizado);
            
            if (!emails.isEmpty()) {
                // Monta a mensagem formatada para o Telegram (Markdown)
                StringBuilder sb = new StringBuilder();
                sb.append("📩 *RELATÓRIO VIA API*\n");
                sb.append("------------------------------------------\n");
                sb.append("🔍 _Filtro:_ `").append(assuntoNormalizado).append("`\n\n");

                for (int i = 0; i < emails.size(); i++) {
                    Map<String, String> email = emails.get(i);
                    sb.append(i + 1).append("️⃣ *De:* ").append(email.get("remetente")).append("\n");
                    sb.append("📌 *Assunto:* ").append(email.get("assunto")).append("\n");
                    sb.append("------------------------------------------\n");
                }
                
                // Envia ao Telegram usando o Service que configuramos com parse_mode
                telegramService.sendMessage(sb.toString(), null); 
            } else {
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
     * Aciona o monitoramento automático (reaproveitando a lógica do Service).
     */
    @GetMapping("/check-ultimo")
    public Map<String, String> checkEmailsUltimoAssunto() {
        Map<String, String> resposta = new HashMap<>();
        try {
            // Aqui chamamos o método que já criamos no Monitor, 
            // que já tem a lógica de não repetir IDs e formatar a mensagem.
            gmailMonitor.verificarEmailsUltimoAssunto();
            resposta.put("status", "Processamento de verificação concluído.");
        } catch (Exception e) {
            resposta.put("erro", e.getMessage());
        }
        return resposta;
    }

    /**
     * Retorna o conteúdo completo de um e-mail pelo ID.
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
