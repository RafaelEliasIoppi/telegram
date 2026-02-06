package telegram.teste.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import telegram.teste.service.GmailMonitor;
import telegram.teste.service.TelegramService;

@Controller
public class MessageController {

    @Autowired
    private TelegramService telegramService;

    @Autowired
    private GmailMonitor gmailMonitor;

    /**
     * Envia uma mensagem manual para o Telegram.
     */
    @PostMapping("/enviar")
    public String enviarMensagem(@RequestParam String titulo,
                                 @RequestParam String conteudo,
                                 @RequestParam(required = false) String destinatario,
                                 Model model) {
        String mensagem = "📢 " + titulo + "\n\n" + conteudo;
        telegramService.sendMessage(mensagem, destinatario);

        model.addAttribute("mensagem", "Mensagem enviada com sucesso!");
        return "index";
    }

    /**
     * Página inicial.
     */
    @GetMapping("/")
    public String index() {
        return "index";
    }

    /**
     * Dispara a checagem de e-mails do Gmail (último assunto salvo).
     */
    @PostMapping("/buscar-gmail-ultimo")
    public String buscarGmailUltimo(Model model) {
        gmailMonitor.verificarEmailsUltimoAssunto();
        model.addAttribute("mensagem", "Checagem concluída usando o último assunto salvo.");
        return "index";
    }

    /**
     * Busca e-mails por assunto informado manualmente.
     */
    @PostMapping("/buscar-gmail-assunto")
    public String buscarPorAssunto(@RequestParam String assunto,
                                   @RequestParam(required = false) String destinatario,
                                   Model model) {
        String assuntoNormalizado = assunto.trim().toLowerCase();
        gmailMonitor.salvarUltimoAssunto(assuntoNormalizado);

        try {
            List<Map<String, String>> emails = gmailMonitor.buscarEmailsPorAssunto(assuntoNormalizado);

            if (!emails.isEmpty()) {
                StringBuilder sb = new StringBuilder("📧 E-mails encontrados:\n");
                for (Map<String, String> email : emails) {
                    sb.append("De: ").append(email.get("remetente"))
                      .append("\nAssunto: ").append(email.get("assunto"))
                      .append("\nData: ").append(email.get("data"))
                      .append("\n\n");
                }
                telegramService.sendMessage(sb.toString(), destinatario);
                model.addAttribute("mensagem", sb.toString());
            } else {
                String msg = "ℹ️ Nenhum e-mail encontrado com assunto: " + assunto;
                telegramService.sendMessage(msg, destinatario);
                model.addAttribute("mensagem", msg);
            }
        } catch (Exception e) {
            String erro = "❌ Erro ao consultar Gmail: " + e.getMessage();
            telegramService.sendMessage(erro, destinatario);
            model.addAttribute("mensagem", erro);
        }

        return "index";
    }
}
