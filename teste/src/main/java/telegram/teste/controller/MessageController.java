package telegram.teste.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import telegram.teste.service.DefesaCivilMonitor;
import telegram.teste.service.TelegramService;

@Controller
public class MessageController {

    @Autowired
    private TelegramService telegramService;

    @Autowired
    private DefesaCivilMonitor defesaCivilMonitor;

    @PostMapping("/enviar")
    public String enviarMensagem(@RequestParam String titulo,
                                 @RequestParam String conteudo,
                                 @RequestParam String destinatario) {
        // 1. Monta a mensagem
        String mensagem = "📢 " + titulo + "\n\n" + conteudo;

        // 2. Envia para o Telegram (ajuste seu TelegramService para aceitar destinatário)
        telegramService.sendMessage(mensagem, destinatario);

        // 3. Opcional: roda a checagem da Defesa Civil
        defesaCivilMonitor.verificarAlertas();

        return "sucesso"; // se estiver em templates/sucesso.html
        // ou "redirect:/sucesso.html" se estiver em static/sucesso.html
    }

    @GetMapping("/")
    public String index() {
        return "index"; // se estiver em templates/index.html
        // ou "redirect:/index.html" se estiver em static/index.html
    }

    @PostMapping("/buscar-defesa-civil")
public String buscarDefesaCivil(@RequestParam(required = false) String destinatario) {
    // chama o monitor para pegar aviso atual
    String alerta = defesaCivilMonitor.verificarAgora(); // crie um método público que retorne aviso
    if (!alerta.isEmpty()) {
        telegramService.sendMessage("⚠️ Alerta Defesa Civil RS:\n" + alerta, destinatario);
    } else {
        telegramService.sendMessage("ℹ️ Nenhum alerta novo encontrado na Defesa Civil RS.", destinatario);
    }

    return "sucesso"; // ou "redirect:/sucesso.html" se estiver em static
}

}
