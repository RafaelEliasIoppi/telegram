package telegram.bot.domain;

import java.time.LocalDateTime;

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
 * Filtro de assunto de e-mail a ser monitorado pelo Gmail.
 * Cada linha ativa adiciona um padrão de subject que, se contido no
 * assunto de um e-mail novo (busca tolerante a acentos/caixa), gera
 * um alerta no Telegram.
 */
@Entity
@Table(name = "filtros_assunto")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FiltroAssunto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String nome;

    @Column(nullable = false, length = 500)
    private String padrao;

    @Column(nullable = false)
    private boolean ativo;

    private LocalDateTime dataCadastro;

    @PrePersist
    public void prePersist() {
        if (dataCadastro == null) {
            dataCadastro = LocalDateTime.now();
        }
    }
}
