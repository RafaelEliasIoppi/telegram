package telegram.bot.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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

    private String user;

    private String appPassword;

    private String imapHost;

    private int imapPort;

    @org.hibernate.annotations.ColumnDefault("300000")
    private long fixedRate;
}
