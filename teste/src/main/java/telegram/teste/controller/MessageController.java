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
        // Monitor settings
        model.addAttribute("monitorEnabled", cfg.getOrDefault("defesacivil.enabled", "false"));
        model.addAttribute("monitorKeywords", cfg.getOrDefault("defesacivil.keywords", "alerta,aviso,emergência"));
        String rate = cfg.getOrDefault("defesacivil.fixedRate", "600000");
        try { model.addAttribute("monitorIntervalMinutes", String.valueOf(Integer.parseInt(rate)/60000)); } catch (Exception ex) { model.addAttribute("monitorIntervalMinutes", "10"); }
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

    // Removida a rota que permitia salvar token/chat via UI por motivos de segurança.

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

    @org.springframework.web.bind.annotation.GetMapping("/defesacivil/candidatos")
    public String verCandidatos(Model model) {
        java.util.List<String> candidatos = defesaCivilMonitor.listarCandidatos(12);
        model.addAttribute("candidatos", candidatos);
        return "defesacivil_candidates";
    }

    @PostMapping("/defesacivil/enviar-candidato")
    public String enviarCandidato(@RequestParam String candidato, Model model) {
        try {
            telegramService.sendMessage(candidato, null);
            model.addAttribute("mensagem", "Candidato enviado ao Telegram com sucesso.");
        } catch (Exception e) {
            model.addAttribute("mensagem", "Erro ao enviar candidato: " + e.getMessage());
        }
        return verCandidatos(model);
    }

    @PostMapping("/defesacivil/salvar-candidato")
    public String salvarCandidato(@RequestParam String candidato, Model model) {
        try {
            defesaCivilMonitor.salvarAlerta(candidato);
            model.addAttribute("mensagem", "Candidato salvo como último alerta.");
        } catch (Exception e) {
            model.addAttribute("mensagem", "Erro ao salvar candidato: " + e.getMessage());
        }
        return verCandidatos(model);
    }

    @PostMapping("/defesacivil/salvar-config")
    public String salvarDefesaCivilConfig(@RequestParam(required = false) String enabled,
                                          @RequestParam(required = false) String keywords,
                                          @RequestParam(required = false) Integer intervalMinutes,
                                          Model model) {
        try {
            java.util.Map<String,String> updates = new java.util.HashMap<>();
            updates.put("defesacivil.enabled", (enabled != null && enabled.equals("on")) ? "true" : "false");
            if (keywords != null) updates.put("defesacivil.keywords", keywords.trim());
            if (intervalMinutes != null) updates.put("defesacivil.fixedRate", String.valueOf(intervalMinutes * 60 * 1000));
            settingsService.saveSettings(updates);
            model.addAttribute("mensagem", "Configurações do monitor salvas.");
        } catch (Exception e) {
            model.addAttribute("mensagem", "Erro ao salvar configurações: " + e.getMessage());
        }
        return index(model);
    }

}