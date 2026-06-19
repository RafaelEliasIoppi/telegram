package telegram.bot.domain;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "chat_configs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatConfig {

    @Id
    private Long chatId;

    private String nome;

    private boolean ativo;

    private String nivelMinimo;

    private String fontesAtivas;

    private LocalDateTime dataCadastro;

    @PrePersist
    public void prePersist() {
        if (dataCadastro == null) {
            dataCadastro = LocalDateTime.now();
        }
        if (nivelMinimo == null || nivelMinimo.isBlank()) {
            nivelMinimo = "INFO";
        }
    }

    public boolean aceitaFonte(String fonte) {
        if (fontesAtivas == null || fontesAtivas.isBlank()) return true;
        // Tolera espaços ao redor das vírgulas (ex.: "DEFESA_CIVIL_RS, INMET").
        return Arrays.stream(fontesAtivas.split(","))
                .map(String::trim)
                .anyMatch(f -> f.equals(fonte));
    }

    public boolean aceitaNivel(String nivel) {
        // INFO < AVISO < CRITICO
        // se nivelMinimo = "CRITICO", só aceita CRITICO
        // se nivelMinimo = "AVISO", aceita AVISO e CRITICO
        // se nivelMinimo = "INFO", aceita tudo
        List<String> niveis = List.of("INFO", "AVISO", "CRITICO");
        int minIdx = niveis.indexOf(nivelMinimo == null ? "INFO" : nivelMinimo);
        int alertaIdx = niveis.indexOf(nivel);
        if (minIdx < 0) minIdx = 0;
        if (alertaIdx < 0) return false;
        return alertaIdx >= minIdx;
    }
}
