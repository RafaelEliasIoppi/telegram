package telegram.bot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import telegram.bot.domain.GmailConfig;
import telegram.bot.repository.GmailConfigRepository;

@Service
@Transactional
public class GmailConfigService {

    private static final Logger log = LoggerFactory.getLogger(GmailConfigService.class);

    @Autowired
    private GmailConfigRepository repo;

    @Value("${gmail.enabled:true}")
    private boolean envEnabled;

    @Value("${gmail.user:}")
    private String envUser;

    @Value("${gmail.app.password:}")
    private String envAppPassword;

    @Value("${gmail.imap.host:imap.gmail.com}")
    private String envImapHost;

    @Value("${gmail.imap.port:993}")
    private int envImapPort;

    @Value("${gmail.fixedRate:300000}")
    private long envFixedRate;

    @PostConstruct
    public void seedFromEnv() {
        if (repo.count() > 0) return;
        GmailConfig config = GmailConfig.builder()
                .id(1L)
                .enabled(envEnabled)
                .user(envUser)
                .appPassword(envAppPassword)
                .imapHost(envImapHost)
                .imapPort(envImapPort)
                .fixedRate(envFixedRate)
                .build();
        repo.save(config);
        log.info("GmailConfig seeded from env vars");
    }

    public GmailConfig getConfig() {
        return repo.findById(1L).orElse(null);
    }

    /**
     * Intervalo (ms) efetivo entre ciclos de verificação de TODAS as fontes,
     * configurável pela UI (campo "Intervalo de verificação" em /gmail) e
     * aplicado dinamicamente pelo agendador. Possui piso de segurança de
     * 10s para evitar martelar as fontes; cai no valor de env/default quando
     * não configurado.
     */
    public long getIntervaloVerificacaoMs() {
        GmailConfig cfg = getConfig();
        long ms = cfg != null ? cfg.getFixedRate() : 0L;
        if (ms <= 0L) {
            ms = envFixedRate > 0 ? envFixedRate : 300000L;
        }
        return Math.max(ms, 10000L);
    }

    public void saveConfig(GmailConfig config) {
        config.setId(1L);
        repo.save(config);
    }
}
