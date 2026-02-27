package telegram.teste.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TelegramService {
    private static final Logger logger = LoggerFactory.getLogger(TelegramService.class);
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${telegram.bot.token}") private String token;
    @Value("${telegram.chat.id}") private String defaultChatId;

    private final String FILE_PATH = "/workspaces/telegram/teste/ultimo_assunto.txt";

    // --- PERSISTÊNCIA ---
    public String lerUltimaPalavraSalva() {
        try {
            Path path = Paths.get(FILE_PATH);
            return Files.exists(path) ? Files.readString(path, StandardCharsets.UTF_8).trim() : "";
        } catch (IOException e) { return ""; }
    }

    public void salvarPalavraNoArquivo(String novaPalavra) {
        try {
            Path path = Paths.get(FILE_PATH);
            Files.createDirectories(path.getParent());
            Files.writeString(path, novaPalavra, StandardCharsets.UTF_8);
        } catch (IOException e) { logger.error("Erro ao salvar arquivo"); }
    }

    // --- ENVIO TELEGRAM ---
    public void sendMessage(String text, String destinatario) {
        String url = "https://api.telegram.org/bot" + token + "/sendMessage";
        Map<String, String> body = Map.of("chat_id", resolveChatId(destinatario), "text", text, "parse_mode", "Markdown");
        restTemplate.postForEntity(url, body, String.class);
    }

    public void sendPhotoFile(MultipartFile foto, String caption, String destinatario) throws IOException {
        enviarParaTelegram(foto.getBytes(), foto.getOriginalFilename(), caption, destinatario);
    }

    public void sendPhotoFromUrlAsFile(String urlFoto, String caption, String destinatario) {
        try {
            logger.info("Baixando imagem para processamento: {}", urlFoto);
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            
            ResponseEntity<byte[]> response = restTemplate.exchange(urlFoto, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
            byte[] bytes = response.getBody();

            if (bytes != null && bytes.length > 1000) { // Ignora arquivos minúsculos (ícones)
                enviarParaTelegram(bytes, "news_image.jpg", caption, destinatario);
                logger.info("Sucesso no envio do binário.");
            } else {
                throw new RuntimeException("Imagem muito pequena ou nula.");
            }
        } catch (Exception e) {
            logger.error("Falha ao processar: {}", e.getMessage());
            sendMessage("🔔 *Notícia:* " + caption + "\n\n🔗 [Ver Link](" + urlFoto + ")", destinatario);
        }
    }

    private void enviarParaTelegram(byte[] bytes, String fileName, String caption, String dest) {
        String url = "https://api.telegram.org/bot" + token + "/sendPhoto";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("chat_id", resolveChatId(dest));
        body.add("photo", new ByteArrayResource(bytes) {
            @Override public String getFilename() { return fileName; }
        });
        body.add("caption", caption);
        body.add("parse_mode", "Markdown");

        restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
    }

    // --- SCRAPING COM FILTRO REFORÇADO ---
    public String buscarImagemCNN(String palavra) throws Exception {
        Document doc = Jsoup.connect("https://www.cnnbrasil.com.br/internacional/")
                .userAgent("Mozilla/5.0").timeout(15000).get();

        Elements elementosComTexto = doc.getElementsContainingOwnText(palavra);
        for (Element el : elementosComTexto) {
            String url = explorarVizinhanca(el, 0);
            if (url != null) return url;
        }
        return null;
    }

    private String explorarVizinhanca(Element el, int profundidade) {
        if (el == null || profundidade > 8 || el.tagName().equals("header") || el.tagName().equals("nav")) return null;
        
        Elements imgs = el.select("img");
        for (Element img : imgs) {
            String url = extrairUrlReal(img);
            if (url != null && isImagemValida(url)) return url;
        }
        return explorarVizinhanca(el.parent(), profundidade + 1);
    }

    private String extrairUrlReal(Element img) {
        String[] atributos = {"abs:data-src", "abs:data-lazy-src", "abs:data-original", "abs:src"};
        for (String attr : atributos) {
            String url = img.attr(attr);
            if (!url.isEmpty() && !url.contains("data:image")) return url;
        }
        return null;
    }

    private boolean isImagemValida(String url) {
        String u = url.toLowerCase();
        // BLOQUEIO CRÍTICO: Não aceita SVG, LOGOS ou ICONES
        if (u.contains(".svg") || u.contains("logo") || u.contains("icon") || u.contains("transparent")) {
            return false;
        }
        return u.contains(".jpg") || u.contains(".jpeg") || u.contains(".png") || u.contains(".webp");
    }

    private String resolveChatId(String dest) {
        return (dest == null || dest.isBlank()) ? defaultChatId : dest;
    }

    public void executarFluxoAutomatico() {
    try {
        // 1. Pega a palavra que está no arquivo (vinda de um commit anterior ou do Gmail)
        String palavraChave = lerUltimaPalavraSalva();

        if (palavraChave == null || palavraChave.isBlank()) {
            logger.warn("O arquivo de histórico está vazio. Nada para buscar.");
            return;
        }

        logger.info("Iniciando busca automática para: {}", palavraChave);

        // 2. Tenta buscar a imagem na CNN
        String urlFoto = buscarImagemCNN(palavraChave);

        if (urlFoto != null) {
            String legenda = "🤖 *Monitoramento Automático*\n\n📌 *Termo:* " + palavraChave + "\n📰 *Fonte:* CNN Internacional";
            sendPhotoFromUrlAsFile(urlFoto, legenda, null);
            logger.info("Busca concluída e enviada para o Telegram.");
        } else {
            logger.info("Nenhuma imagem nova encontrada na CNN para o termo: {}", palavraChave);
        }

    } catch (Exception e) {
        logger.error("Falha no fluxo automático do GitHub Actions: {}", e.getMessage());
    }
}
}