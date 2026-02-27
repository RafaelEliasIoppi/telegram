package telegram.teste.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import telegram.teste.service.TelegramService;

@Controller
public class MessageController {

    @Autowired
    private TelegramService telegramService;

    /**
     * Envia mensagem de texto simples.
     */
    @PostMapping("/enviar")
    public String enviarMensagem(@RequestParam String titulo,
                                 @RequestParam String conteudo,
                                 @RequestParam(required = false) String destinatario,
                                 Model model) {
        String mensagem = "📢 *NOVA MENSAGEM*\n" +
                          "--------------------------\n" +
                          "📌 *Título:* " + titulo + "\n" +
                          "📝 *Conteúdo:* " + conteudo;

        telegramService.sendMessage(mensagem, destinatario);
        model.addAttribute("mensagem", "Mensagem enviada com sucesso!");
        return "index";
    }

    /**
     * Envia foto + texto (upload local).
     */
    @PostMapping("/enviar-foto-texto")
    public String enviarFotoTexto(@RequestParam("foto") MultipartFile foto,
                                  @RequestParam String titulo,
                                  @RequestParam String conteudo,
                                  @RequestParam(required = false) String destinatario,
                                  Model model) {
        try {
            String legenda = "🖼️ *Foto enviada*\n" +
                             "--------------------------\n" +
                             "📌 *Título:* " + titulo + "\n" +
                             "📝 *Conteúdo:* " + conteudo;

            telegramService.sendPhotoFile(foto, legenda, destinatario);
            model.addAttribute("mensagem", "Foto + texto enviados com sucesso!");
        } catch (Exception e) {
            model.addAttribute("mensagem", "Erro ao enviar foto: " + e.getMessage());
        }
        return "index";
    }
}
