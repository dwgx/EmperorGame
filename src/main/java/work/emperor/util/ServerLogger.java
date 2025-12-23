package work.emperor.util;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Lightweight wrapper around java.util.logging to keep the server logs consistent.
 */
public final class ServerLogger {
    private static final Map<String, ServerLogger> CACHE = new ConcurrentHashMap<>();
    private final Logger delegate;

    private ServerLogger(Class<?> cls) {
        this.delegate = Logger.getLogger(cls.getName());
        this.delegate.setLevel(Level.INFO);
        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.INFO);
        this.delegate.setUseParentHandlers(false);
        this.delegate.addHandler(handler);
    }

    public static ServerLogger get(Class<?> cls) {
        return CACHE.computeIfAbsent(cls.getName(), ignored -> new ServerLogger(cls));
    }

    public void info(String message) {
        delegate.info(message);
    }

    public void warn(String message) {
        delegate.warning(message);
    }

    public void error(String message, Throwable throwable) {
        delegate.log(Level.SEVERE, message, throwable);
    }

    public void debug(String message) {
        delegate.fine(message);
    }
}
