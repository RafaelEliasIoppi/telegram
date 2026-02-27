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
            // 1. Busca a URL da imagem no site da CNN
            String urlFoto = telegramService.buscarImagemCNN(palavra);
            
            if (urlFoto != null) {
                // 2. Envia para o Telegram (Baixando o binário para evitar erro 400)
                telegramService.sendPhotoFromUrlAsFile(urlFoto, "📰 Notícia sobre: " + palavra, destinatario);
                
                // 3. SALVA A PALAVRA NO ARQUIVO (Fundamental para o GitHub Actions não repetir)
                telegramService.salvarPalavraNoArquivo(palavra);
                
                model.addAttribute("mensagem", "Notícia enviada e palavra salva no histórico!");
            } else {
                model.addAttribute("mensagem", "Nenhuma notícia encontrada para: " + palavra);
            }
        } catch (Exception e) {
            model.addAttribute("mensagem", "Erro: " + e.getMessage());
        }
        return "index";
    }
    @PostMapping("/executar-busca-automatica")
public String executarBuscaAutomatica(Model model) {
    try {
        // 1. Lê o que está salvo no arquivo (ex: a última pesquisa feita ou vinda do Gmail)
        String palavraSalva = telegramService.lerUltimaPalavraSalva();

        if (palavraSalva != null && !palavraSalva.isBlank()) {
            // 2. Executa a busca na CNN usando essa palavra
            String urlFoto = telegramService.buscarImagemCNN(palavraSalva);
            
            if (urlFoto != null) {
                String legenda = "🤖 *Busca Automática Reexecutada*\n📌 Termo: " + palavraSalva;
                telegramService.sendPhotoFromUrlAsFile(urlFoto, legenda, null);
                model.addAttribute("mensagem", "Busca automática concluída para: " + palavraSalva);
            } else {
                model.addAttribute("mensagem", "Palavra salva encontrada, mas nada novo na CNN.");
            }
        } else {
            model.addAttribute("mensagem", "O arquivo de histórico está vazio.");
        }
    } catch (Exception e) {
        model.addAttribute("mensagem", "Erro na busca automática: " + e.getMessage());
    }
    return "index";
}
}