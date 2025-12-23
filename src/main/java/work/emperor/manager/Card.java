package work.emperor.manager;

import java.util.Locale;

public enum Card {
    EMPEROR,
    CITIZEN,
    SLAVE,
    TRAITOR,
    MADMAN;

    public static Card fromString(String raw) {
        if (raw == null) return null;
        try {
            return Card.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return null;
        }
    }
}
