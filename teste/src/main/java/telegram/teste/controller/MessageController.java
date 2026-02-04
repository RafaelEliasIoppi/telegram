package telegram.teste.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import telegram.teste.service.TelegramService;

@Controller
public class MessageController {

    @Autowired
    private TelegramService telegramService;

    @PostMapping("/enviar")
    public String enviarMensagem(@RequestParam String titulo,
                                 @RequestParam String conteudo,
                                 @RequestParam String destinatario) {
        String mensagem = "📢 " + titulo + "\n\n" + conteudo;
        telegramService.sendMessage(mensagem);
        return "redirect:/sucesso.html";
    }

    @GetMapping("/")
    public String index() {
        return "index.html";
    }
}
