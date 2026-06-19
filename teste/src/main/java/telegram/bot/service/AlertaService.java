package telegram.bot.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.PageRequest;
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
        // Verificação prévia: descarta duplicados já conhecidos sem ir ao banco.
        if (alertaRepo.findByHashConteudo(hash).isPresent()) return Optional.empty();
        try {
            // saveAndFlush força a gravação imediata para que a violação do
            // índice único em hashConteudo (sob concorrência) seja detectada aqui.
            return Optional.of(alertaRepo.saveAndFlush(alerta));
        } catch (DataIntegrityViolationException e) {
            // Outro processo gravou o mesmo conteúdo entre o check e o save:
            // trata como duplicado.
            return Optional.empty();
        }
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
     * Últimos N alertas, ordenados do mais recente para o mais antigo.
     */
    public List<Alerta> ultimosAlertas(int n) {
        if (n < 1) n = 1;
        return alertaRepo.findByOrderByDataHoraDesc(PageRequest.of(0, n));
    }

    public List<ChatConfig> listarChats() {
        return chatConfigRepo.findAll();
    }

    public ChatConfig salvarChat(ChatConfig config) {
        return chatConfigRepo.save(config);
    }

    public void removerChat(Long chatId) {
        // Evita lançar exceção quando o chat não existe.
        try {
            chatConfigRepo.deleteById(chatId);
        } catch (EmptyResultDataAccessException ignored) {
            // chat já inexistente: nada a fazer
        }
    }

    /**
     * Hash SHA-256 do conteúdo para deduplicação.
     */
    private String calcularHash(String conteudo) {
        // Trata conteúdo nulo como string vazia para evitar NPE.
        String texto = conteudo != null ? conteudo : "";
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(texto.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            // Fallback: usa o próprio texto (já garantido não-nulo) limitado a 255 chars.
            return texto.substring(0, Math.min(255, texto.length()));
        }
    }
}
