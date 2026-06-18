package telegram.bot.service.monitor;

import java.util.List;

import telegram.bot.domain.Alerta;

/**
 * Contrato base para todas as fontes monitoradas pelo bot.
 *
 * <p>Cada implementação representa uma origem (Defesa Civil, INMET, etc.)
 * que pode ser ativada ou desativada via propriedades e que sabe consultar
 * sua fonte para retornar uma lista de {@link Alerta} candidatos. A
 * implementação NÃO deve persistir os alertas — apenas devolvê-los para
 * que o orquestrador decida o que fazer.</p>
 */
public interface FonteMonitor {

    /**
     * Identificador curto e estável da fonte (ex.: {@code "DEFESA_CIVIL_RS"},
     * {@code "INMET"}). Usado como valor do campo {@link Alerta#getFonte()}.
     *
     * @return nome canônico da fonte
     */
    String getNome();

    /**
     * Descrição legível da fonte, útil para logs e exibição em UI.
     *
     * @return descrição amigável
     */
    String getDescricao();

    /**
     * Indica se o monitor está habilitado (normalmente controlado por
     * propriedade {@code <fonte>.enabled}).
     *
     * @return {@code true} se o monitor deve ser executado pelo scheduler
     */
    boolean isAtivo();

    /**
     * Realiza a consulta/scraping da fonte e retorna os alertas encontrados.
     * Implementações devem tratar falhas de rede graciosamente, devolvendo
     * lista vazia em caso de erro (e registrando o problema em log).
     *
     * @return alertas candidatos (nunca {@code null})
     */
    List<Alerta> verificar();
}
