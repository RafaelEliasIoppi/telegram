package telegram.bot.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import telegram.bot.domain.Alerta;
import telegram.bot.domain.ChatConfig;
import telegram.bot.repository.AlertaRepository;
import telegram.bot.repository.ChatConfigRepository;

@Service
@Transactional
public class AlertaService {

    @Autowired
    private AlertaRepository alertaRepo;

    @Autowired
    private ChatConfigRepository chatConfigRepo;

    /**
     * Verifica se o alerta é duplicado (por hash do conteudo).
     * Se não for duplicado, salva e retorna Optional.of(alerta).
     * Se for duplicado, retorna Optional.empty().
     */
    public Optional<Alerta> registrarSeNovo(Alerta alerta) {
        String hash = calcularHash(alerta.getConteudo());
        alerta.setHashConteudo(hash);
        if (alertaRepo.findByHashConteudo(hash).isPresent()) return Optional.empty();
        return Optional.of(alertaRepo.save(alerta));
    }

    /**
     * Retorna lista de chats que devem receber este alerta.
     */
    public List<Long> chatIdsParaAlerta(Alerta alerta) {
        return chatConfigRepo.findByAtivoTrue().stream()
                .filter(c -> c.getChatId() != null)
                .filter(c -> c.aceitaFonte(alerta.getFonte()))
                .filter(c -> c.aceitaNivel(alerta.getNivel()))
                .map(ChatConfig::getChatId)
                .toList();
    }

    /**
     * Marca alerta como enviado.
     */
    public void marcarEnviado(Long alertaId) {
        alertaRepo.findById(alertaId).ifPresent(a -> {
            a.setEnviado(true);
            alertaRepo.save(a);
        });
    }

    /**
     * Últimos N alertas (atualmente fixo em 20 pelo repository).
     */
    public List<Alerta> ultimosAlertas(int n) {
        return alertaRepo.findTop20ByOrderByDataHoraDesc();
    }

    public List<ChatConfig> listarChats() {
        return chatConfigRepo.findAll();
    }

    public ChatConfig salvarChat(ChatConfig config) {
        return chatConfigRepo.save(config);
    }

    public void removerChat(Long chatId) {
        chatConfigRepo.deleteById(chatId);
    }

    /**
     * Hash SHA-256 do conteúdo para deduplicação.
     */
    private String calcularHash(String conteudo) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(conteudo.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return conteudo.substring(0, Math.min(255, conteudo.length()));
        }
    }
}
