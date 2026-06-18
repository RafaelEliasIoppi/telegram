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

        Element og = doc.selectFirst("meta[property=og:image]");
        if (og != null) {
            String url = og.attr("content");
            if (isImagemValida(url)) return absoluta(url, baseUrl);
        }

        Element tw = doc.selectFirst("meta[name=twitter:image]");
        if (tw != null) {
            String url = tw.attr("content");
            if (isImagemValida(url)) return absoluta(url, baseUrl);
        }

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

    private static boolean isImagemValida(String url) {
        if (url == null || url.isBlank()) return false;
        String lower = url.toLowerCase();
        if (lower.contains("data:image")) return false;
        if (lower.endsWith(".svg")) return false;
        if (!lower.startsWith("http")) return false;
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png")
                || lower.endsWith(".webp") || lower.contains(".jpg?") || lower.contains(".jpeg?")
                || lower.contains(".png?") || lower.contains(".webp?")
                || lower.contains("/image") || lower.contains("photo");
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
