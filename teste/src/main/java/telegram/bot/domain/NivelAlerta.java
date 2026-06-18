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
}
