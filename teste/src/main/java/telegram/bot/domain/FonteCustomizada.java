package telegram.bot.domain;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Fonte de monitoramento adicionada pelo usuário via UI.
 * O FonteCustomizadaMonitor varre todas as linhas {@code ativo = true}
 * periodicamente, baixa o conteúdo da {@code url}, e gera alertas para
 * trechos que contenham alguma das palavras-chave.
 *
 * Tipos suportados:
 *  - URL: página HTML genérica (usa Jsoup; opcionalmente um seletor CSS)
 *  - RSS: feed RSS/Atom (varre &lt;item&gt;/&lt;entry&gt;)
 */
@Entity
@Table(name = "fontes_customizadas")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FonteCustomizada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String nome;

    /** Identificador interno usado no campo Alerta.fonte (UPPER_SNAKE). */
    @Column(nullable = false, length = 80)
    private String codigo;

    /** URL (HTML) ou RSS (XML). */
    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(nullable = false, length = 1000)
    private String url;

    /** Seletor CSS opcional para páginas HTML (ex.: ".alerta, h2"). */
    @Column(length = 500)
    private String seletor;

    /** Palavras-chave separadas por vírgula; pelo menos uma precisa casar. */
    @Column(length = 1000)
    private String palavrasChave;

    @Column(nullable = false, length = 20)
    private String nivel;

    @Column(nullable = false)
    private boolean ativo;

    private LocalDateTime dataCadastro;

    @PrePersist
    public void prePersist() {
        if (dataCadastro == null) {
            dataCadastro = LocalDateTime.now();
        }
        if (nivel == null || nivel.isBlank()) {
            nivel = "INFO";
        }
        if (tipo == null || tipo.isBlank()) {
            tipo = "URL";
        }
    }

    /** Lista normalizada (lowercase, sem espaços nas pontas) das palavras-chave. */
    public List<String> palavrasChaveList() {
        if (palavrasChave == null || palavrasChave.isBlank()) {
            return List.of();
        }
        return Arrays.stream(palavrasChave.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(String::toLowerCase)
                .collect(Collectors.toList());
    }
}
