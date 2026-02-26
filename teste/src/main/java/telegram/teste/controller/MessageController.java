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
        // Formatação melhorada para mensagens manuais
        String mensagem = "📢 *NOVA MENSAGEM*\n" +
                          "--------------------------\n" +
                          "📌 *Título:* " + titulo + "\n" +
                          "📝 *Conteúdo:* " + conteudo;
        
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
     * Busca e-mails por assunto informado manualmente com formatação otimizada.
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
                // Montagem da mensagem formatada para o Telegram
                StringBuilder sb = new StringBuilder();
                sb.append("📩 *RELATÓRIO DE E-MAILS*\n");
                sb.append("------------------------------------------\n");
                sb.append("🔍 _Busca por:_ `").append(assuntoNormalizado).append("`\n\n");

                for (int i = 0; i < emails.size(); i++) {
                    Map<String, String> email = emails.get(i);
                    sb.append(i + 1).append("️⃣ *De:* ").append(email.get("remetente")).append("\n");
                    sb.append("📌 *Assunto:* ").append(email.get("assunto")).append("\n");
                    sb.append("------------------------------------------\n");
                }

                telegramService.sendMessage(sb.toString(), destinatario);

                model.addAttribute("mensagens", emails);
                model.addAttribute("status", "Sucesso! " + emails.size() + " e-mail(s) encontrado(s).");
            } else {
                model.addAttribute("status", "ℹ️ Nenhum e-mail encontrado para: " + assunto);
            }
        } catch (Exception e) {
            model.addAttribute("status", "❌ Erro: " + e.getMessage());
        }

        return "index";
    }
}