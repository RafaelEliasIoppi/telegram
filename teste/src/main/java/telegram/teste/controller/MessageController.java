package telegram.teste.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
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
                                 @RequestParam(required = false) String destinatario) {
        // 1. Monta a mensagem
        String mensagem = "📢 " + titulo + "\n\n" + conteudo;

        // 2. Envia para o Telegram (se destinatário não informado, usa default)
        telegramService.sendMessage(mensagem, destinatario);

        return "sucesso"; // se estiver em templates/sucesso.html
        // ou "redirect:/sucesso.html" se estiver em static/sucesso.html
    }

    /**
     * Página inicial.
     */
    @GetMapping("/")
    public String index() {
        return "index"; // se estiver em templates/index.html
        // ou "redirect:/index.html" se estiver em static/index.html
    }

    /**
     * Dispara a checagem de e-mails do Gmail (SNT).
     */
    @PostMapping("/buscar-gmail")
    public String buscarGmail(@RequestParam(required = false) String destinatario) {
        // 🔹 chama o monitor para verificar e-mails do SNT
        gmailMonitor.verificarEmailsSNT();

        return "sucesso"; // ou "redirect:/sucesso.html" se estiver em static
    }
}
