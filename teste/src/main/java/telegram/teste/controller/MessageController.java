package telegram.teste.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import telegram.teste.service.TelegramService;
import telegram.teste.service.SettingsService;
import telegram.teste.service.DefesaCivilMonitor;

@Controller
public class MessageController {

    @Autowired
    private TelegramService telegramService;

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private DefesaCivilMonitor defesaCivilMonitor;

    @org.springframework.web.bind.annotation.GetMapping("/")
    public String index(Model model) {
        var cfg = settingsService.readConfig();
        model.addAttribute("configToken", cfg.getOrDefault("telegram.bot.token", ""));
        model.addAttribute("configChatId", cfg.getOrDefault("telegram.chat.id", ""));
        String ultima = telegramService.lerUltimaPalavraSalva();
        model.addAttribute("ultimaPalavra", ultima);
        // Mascara parcial do token para exibição
        String t = cfg.getOrDefault("telegram.bot.token", "");
        String masked = "";
        if (t != null && !t.isBlank()) {
            if (t.length() > 8) masked = t.substring(0,4) + "..." + t.substring(t.length()-4);
            else masked = t.substring(0, Math.min(4, t.length())) + "...";
        }
        model.addAttribute("maskedToken", masked);
        return "index";
    }

    @PostMapping("/salvar-config")
    public String salvarConfig(@RequestParam(required = false) String token,
                               @RequestParam(required = false) String chatId,
                               Model model) {
        settingsService.saveConfig(token, chatId);
        model.addAttribute("mensagem", "Configurações salvas com sucesso!");
        return index(model);
    }

    @PostMapping("/salvar-ultima")
    public String salvarUltima(@RequestParam String ultima, Model model) {
        telegramService.salvarPalavraNoArquivo(ultima);
        model.addAttribute("mensagem", "Última palavra salva.");
        return index(model);
    }

    @PostMapping("/enviar-teste")
    public String enviarTeste(Model model) {
        var cfg = settingsService.readConfig();
        String token = cfg.get("telegram.bot.token");
        String chatId = cfg.get("telegram.chat.id");
        if (token == null || token.isBlank() || chatId == null || chatId.isBlank()) {
            model.addAttribute("mensagem", "Erro: token ou chat_id não configurados. Salve as configurações primeiro.");
            return index(model);
        }

        try {
            String texto = "🧪 Mensagem de teste enviada pelo usuário via UI.";
            telegramService.sendMessage(texto, null);
            model.addAttribute("mensagem", "Mensagem de teste enviada com sucesso!");
        } catch (Exception e) {
            model.addAttribute("mensagem", "Erro ao enviar mensagem de teste: " + e.getMessage());
        }
        return index(model);
    }

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

    @org.springframework.web.bind.annotation.GetMapping("/defesacivil")
    public String verAlertas(Model model) {
        // lê último alerta gravado pelo monitor
        String alerta = "";
        try {
            java.nio.file.Path p = java.nio.file.Paths.get("teste/defesacivil_last.txt");
            if (java.nio.file.Files.exists(p)) alerta = java.nio.file.Files.readString(p);
        } catch (Exception e) {
            // ignora
        }
        model.addAttribute("alerta", alerta == null ? "" : alerta.trim());
        return "defesacivil";
    }

    @PostMapping("/defesacivil/verificar-agora")
    public String verificarDefesaCivilAgora(Model model) {
        try {
            defesaCivilMonitor.verificarAgora();
            model.addAttribute("mensagem", "Verificação da Defesa Civil executada. Consulte a página de alertas.");
        } catch (Exception e) {
            model.addAttribute("mensagem", "Erro ao executar verificação: " + e.getMessage());
        }
        return verAlertas(model);
    }
}