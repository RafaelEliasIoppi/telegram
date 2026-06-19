package telegram.bot.domain;

public enum NivelAlerta {
    INFO, AVISO, CRITICO;

    public String emoji() {
        return switch (this) {
            case INFO -> "ℹ️";
            case AVISO -> "⚠️";
            case CRITICO -> "🚨";
        };
    }

    /** Rótulo amigável em caixa-alta para o cabeçalho do aviso. */
    public String rotulo() {
        return switch (this) {
            case INFO -> "INFORMATIVO";
            case AVISO -> "AVISO";
            case CRITICO -> "CRÍTICO";
        };
    }

    /** Bolinha colorida (semáforo de severidade) para a tag do rodapé. */
    public String bolinha() {
        return switch (this) {
            case INFO -> "🔵";
            case AVISO -> "🟠";
            case CRITICO -> "🔴";
        };
    }
}
