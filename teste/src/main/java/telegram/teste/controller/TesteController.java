package telegram.teste.controller;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import telegram.teste.service.TelegramService;


@RestController
public class TesteController {

    @Autowired
    private  TelegramService telegramService;

   

    @GetMapping("/teste")
    public String enviarMensagem() {
        String mensagem = "Olá! Esta é uma mensagem de teste enviada pelo Telegram Bot.";
        telegramService.sendMessage(mensagem, null);
        return "Mensagem de teste enviada para o Telegram!";
    }
}