package telegram.bot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "gmail_config")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GmailConfig {

    @Id
    private Long id;

    private boolean enabled;

    // "user" é palavra reservada em SQL/H2; mapeia para coluna com nome seguro.
    @Column(name = "gmail_user")
    private String user;

    // Evita vazar a senha de app no toString() gerado pelo @Data.
    @ToString.Exclude
    private String appPassword;

    private String imapHost;

    private int imapPort;

    @org.hibernate.annotations.ColumnDefault("300000")
    private long fixedRate;
}
