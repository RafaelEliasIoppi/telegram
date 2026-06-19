package telegram.bot.service.monitor;

import java.net.URI;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Utilitário sem estado para extrair uma URL de imagem representativa
 * a partir de um documento HTML ou de um item de feed RSS.
 *
 * Ordem de preferência:
 *  1) {@code <meta property="og:image">}
 *  2) {@code <meta name="twitter:image">}
 *  3) Primeiro {@code <img src="...">} cujo URL pareça válido
 *  4) Para RSS: {@code <enclosure url="...">} / {@code <media:content url="...">}
 */
public final class ImagemExtractor {

    private ImagemExtractor() {}

    public static String extrairDeHtml(Document doc, String baseUrl) {
        if (doc == null) return null;

        // META TAGS confiáveis (og:image, twitter:image): aceitam qualquer URL
        // http(s) — muitos sites servem a imagem por endpoint dinâmico, sem
        // extensão no caminho. Exigir extensão aqui descartaria imagens válidas.
        Element og = doc.selectFirst("meta[property=og:image]");
        if (og != null) {
            String url = og.attr("content");
            if (isMetaImagemConfiavel(url)) return absoluta(url, baseUrl);
        }

        Element tw = doc.selectFirst("meta[name=twitter:image]");
        if (tw != null) {
            String url = tw.attr("content");
            if (isMetaImagemConfiavel(url)) return absoluta(url, baseUrl);
        }

        // <img src> genérico: mantém a exigência de extensão de imagem para
        // evitar logos, sprites e pixels de rastreamento.
        for (Element img : doc.select("img[src]")) {
            String src = img.attr("src");
            if (isImagemValida(src)) {
                return absoluta(src, baseUrl);
            }
        }
        return null;
    }

    public static String extrairDeItemRss(Element item) {
        if (item == null) return null;

        Elements enclosure = item.select("enclosure[url]");
        for (Element e : enclosure) {
            String tipo = e.attr("type");
            String url = e.attr("url");
            if (isImagemValida(url) && (tipo == null || tipo.startsWith("image"))) {
                return url;
            }
        }

        Elements media = item.select("media|content, media\\:content, content[url]");
        for (Element m : media) {
            String url = m.attr("url");
            if (isImagemValida(url)) {
                return url;
            }
        }

        Element image = item.selectFirst("image, image > url");
        if (image != null) {
            String url = image.text().trim();
            if (isImagemValida(url)) return url;
        }

        // imagem embutida na descrição HTML do RSS
        String description = item.select("description, summary").text();
        if (description != null && description.contains("<img")) {
            int i = description.indexOf("<img");
            int s = description.indexOf("src=\"", i);
            if (s > 0) {
                int e = description.indexOf("\"", s + 5);
                if (e > 0) {
                    String url = description.substring(s + 5, e);
                    if (isImagemValida(url)) return url;
                }
            }
        }
        return null;
    }

    /**
     * Validação para META TAGS confiáveis (og:image / twitter:image): basta
     * ser uma URL http(s) que não seja data: nem SVG. Não exige extensão,
     * pois é comum servir a imagem por endpoint dinâmico.
     */
    private static boolean isMetaImagemConfiavel(String url) {
        if (url == null || url.isBlank()) return false;
        String lower = url.toLowerCase();
        if (lower.contains("data:image")) return false;
        if (!lower.startsWith("http")) return false;
        // SVG não é bem suportado como foto no Telegram; descarta também aqui.
        String semQuery = lower.split("[?#]", 2)[0];
        return !semQuery.endsWith(".svg");
    }

    private static boolean isImagemValida(String url) {
        if (url == null || url.isBlank()) return false;
        String lower = url.toLowerCase();
        if (lower.contains("data:image")) return false;
        if (lower.endsWith(".svg")) return false;
        if (!lower.startsWith("http")) return false;
        // Exige extensão de imagem real. Tolerante a query string (?...) e a
        // fragmentos (#...): a extensão é checada apenas no caminho, ignorando
        // o que vem depois. As tags confiáveis og:image / twitter:image são
        // tratadas por isMetaImagemConfiavel; aqui evitamos capturar logos,
        // sprites e pixels de rastreamento que só contêm "photo"/"/image" no path.
        String caminho = lower.split("[?#]", 2)[0];
        return caminho.matches(".*\\.(jpe?g|png|webp)$");
    }

    private static String absoluta(String url, String base) {
        if (url.startsWith("http")) return url;
        try {
            return URI.create(base).resolve(url).toString();
        } catch (Exception e) {
            return url;
        }
    }
}
