package telegram.teste.service;

import java.io.IOException;
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

    public void sendMessage(String text, String destinatario) {
        String url = "https://api.telegram.org/bot" + token + "/sendMessage";
        Map<String, String> body = Map.of("chat_id", resolveChatId(destinatario), "text", text, "parse_mode", "Markdown");
        restTemplate.postForEntity(url, body, String.class);
    }

    /**
     * Versão Final: Baixa a imagem, limpa a URL e envia como arquivo físico.
     * Resolve: "failed to get HTTP URL content" e "IMAGE_PROCESS_FAILED"
     */
    public void sendPhotoFromUrlAsFile(String urlFoto, String caption, String destinatario) {
        try {
            // 1. Limpeza de URL (Remove parâmetros de redimensionamento que corrompem o arquivo)
            String urlLimpa = urlFoto.split("\\?")[0];
            logger.info("Tentando baixar imagem limpa de: {}", urlLimpa);

            // 2. Headers de navegador real para não ser bloqueado pelo servidor da imagem
            HttpHeaders headersDownload = new HttpHeaders();
            headersDownload.set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/122.0.0.0 Safari/537.36");
            headersDownload.set("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8");
            
            HttpEntity<String> entityDownload = new HttpEntity<>(headersDownload);
            ResponseEntity<byte[]> response = restTemplate.exchange(urlLimpa, HttpMethod.GET, entityDownload, byte[].class);
            byte[] imageBytes = response.getBody();

            if (imageBytes != null && imageBytes.length > 0) {
                // 3. Envio para o Telegram como Multipart
                String urlTelegram = "https://api.telegram.org/bot" + token + "/sendPhoto";
                HttpHeaders headersTelegram = new HttpHeaders();
                headersTelegram.setContentType(MediaType.MULTIPART_FORM_DATA);

                MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
                body.add("chat_id", resolveChatId(destinatario));
                
                // Forçar o nome "image.jpg" ajuda o Telegram a processar o binário corretamente
                body.add("photo", new ByteArrayResource(imageBytes) {
                    @Override public String getFilename() { return "image.jpg"; }
                });
                
                body.add("caption", caption);
                body.add("parse_mode", "Markdown");

                restTemplate.postForEntity(urlTelegram, new HttpEntity<>(body, headersTelegram), String.class);
                logger.info("Sucesso! Imagem enviada via buffer binário.");
            }
        } catch (Exception e) {
            logger.error("Falha ao processar imagem: {}", e.getMessage());
            sendMessage("⚠️ Encontrei a notícia, mas a imagem não pôde ser processada.\n\n" + caption, destinatario);
        }
    }

    public void sendPhotoFile(MultipartFile foto, String caption, String destinatario) throws IOException {
        String url = "https://api.telegram.org/bot" + token + "/sendPhoto";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("chat_id", resolveChatId(destinatario));
        body.add("photo", new ByteArrayResource(foto.getBytes()) {
            @Override public String getFilename() { return foto.getOriginalFilename(); }
        });
        body.add("caption", caption);
        body.add("parse_mode", "Markdown");
        restTemplate.postForEntity(url, new HttpEntity<>(body, headers), String.class);
    }

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
        // Se subiu demais e chegou no menu ou topo, para a busca para não pegar o logo
        if (el == null || profundidade > 6 || el.tagName().equals("header") || el.tagName().equals("nav")) {
            return null;
        }

        // Tenta achar QUALQUER imagem dentro deste bloco
        Elements imgs = el.select("img");
        for (Element img : imgs) {
            String url = extrairUrlReal(img);
            
            // FILTRO: Ignora o que sabidamente NÃO é notícia
            if (url != null && 
                !url.toLowerCase().endsWith(".svg") && 
                !url.contains("logo") && 
                !url.contains("avatar") &&
                !url.contains("icon") &&
                !url.contains("transparent")) {
                
                logger.info("Imagem válida encontrada no nível {}: {}", profundidade, url);
                return url;
            }
        }

        // Se não achou imagem neste nível, sobe para o pai (recursão)
        return explorarVizinhanca(el.parent(), profundidade + 1);
    }

    private String extrairUrlReal(Element img) {
        // Adicionamos todos os possíveis atributos de "Lazy Load" que grandes portais usam
        String[] atributos = {
            "abs:data-src", 
            "abs:data-lazy-src", 
            "abs:data-original", 
            "abs:src", 
            "abs:srcset"
        };
        
        for (String attr : atributos) {
            String url = img.attr(attr);
            if (!url.isEmpty() && !url.contains("data:image")) {
                // Se for srcset, pegamos a primeira ou a última URL válida
                if (attr.contains("srcset")) {
                    return url.split(",")[0].trim().split(" ")[0]; 
                }
                return url;
            }
        }
        return null;
    }

  
    private String resolveChatId(String dest) {
        return (dest == null || dest.isBlank()) ? defaultChatId : dest;
    }
}