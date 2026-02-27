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

    @PostMapping("/enviar")
    public String enviarMensagem(@RequestParam String titulo, @RequestParam String conteudo,
                                 @RequestParam(required = false) String destinatario, Model model) {
        String msg = "📢 *NOVA MENSAGEM*\n" + "--------------------------\n" +
                     "📌 *Título:* " + titulo + "\n" + "📝 *Conteúdo:* " + conteudo;
        telegramService.sendMessage(msg, destinatario);
        model.addAttribute("mensagem", "Mensagem enviada com sucesso!");
        return "index";
    }

    @PostMapping("/enviar-foto-texto")
    public String enviarFotoTexto(@RequestParam("foto") MultipartFile foto, @RequestParam String titulo,
                                  @RequestParam String conteudo, @RequestParam(required = false) String destinatario,
                                  Model model) {
        try {
            String legenda = "🖼️ *Foto enviada*\n" + "--------------------------\n" +
                             "📌 *Título:* " + titulo + "\n" + "📝 *Conteúdo:* " + conteudo;
            telegramService.sendPhotoFile(foto, legenda, destinatario);
            model.addAttribute("mensagem", "Foto enviada com sucesso!");
        } catch (Exception e) {
            model.addAttribute("mensagem", "Erro: " + e.getMessage());
        }
        return "index";
    }

    @PostMapping("/enviar-noticia-cnn")
        public String enviarNoticiaCNN(@RequestParam String palavra, 
                               @RequestParam(required = false) String destinatario, 
                               Model model) {
            try {
                String urlFoto = telegramService.buscarImagemCNN(palavra);
                
                if (urlFoto != null) {
                    // CHAMADA DO NOVO MÉTODO QUE ENVIA COMO ARQUIVO
                    telegramService.sendPhotoFromUrlAsFile(urlFoto, "📰 Notícia sobre: " + palavra, destinatario);
                    model.addAttribute("mensagem", "Notícia enviada como arquivo!");
                } else {
                    model.addAttribute("mensagem", "Nenhuma notícia encontrada para: " + palavra);
                }
            } catch (Exception e) {
                model.addAttribute("mensagem", "Erro: " + e.getMessage());
            }
            return "index";
}
   
}