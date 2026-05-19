package telegram.teste.runner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

public class DefesaCivilTestRunner {
    public static void main(String[] args) throws Exception {
        Path cfg = Paths.get(".env");
        String url = "https://www.defesacivil.rs.gov.br/";
        if (Files.exists(cfg)) {
            try {
                for (String l : Files.readAllLines(cfg, StandardCharsets.UTF_8)) {
                    String line = l.trim();
                    if (line.startsWith("defesacivil.url=")) {
                        url = line.split("=",2)[1].trim();
                        break;
                    }
                }
            } catch (IOException e) {
                // ignore
            }
        }

        System.out.println("Usando URL: " + url);
        try {
            Document doc = Jsoup.connect(url).userAgent("Mozilla/5.0").timeout(15000).get();

            // try find rss
            var links = doc.select("link[type=application/rss+xml], link[type=application/atom+xml]");
            if (!links.isEmpty()) {
                System.out.println("Feed encontrado: " + links.first().attr("abs:href"));
            }

            // check headlines
            for (int i=1;i<=3;i++){
                var els = doc.select("h"+i);
                if (!els.isEmpty()){
                    System.out.println("Encontrado headlines h"+i+":");
                    els.stream().limit(5).forEach(e -> System.out.println(" - " + e.text()));
                }
            }

            // procurar palavras-chave
            String[] keywords = new String[] {"alerta","aviso","atenção","emergência","bol"};
            for (String kw : keywords) {
                var matches = doc.getElementsContainingOwnText(kw);
                if (!matches.isEmpty()) {
                    System.out.println("Matches para '"+kw+"':");
                    matches.stream().limit(5).forEach(m -> {
                        var a = m.selectFirst("a[href]");
                        String link = a != null ? a.attr("abs:href") : "";
                        System.out.println(" - " + m.text() + (link.isBlank() ? "" : " -> " + link));
                    });
                }
            }

            System.out.println("--- resumo do corpo (primeiros 400 chars) ---");
            String texto = doc.body().text();
            System.out.println(texto.substring(0, Math.min(400, texto.length())));

        } catch (Exception e) {
            System.err.println("Erro ao coletar: " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }
}
