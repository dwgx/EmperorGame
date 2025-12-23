package work.emperor.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Objects;
import java.util.Properties;

/**
 * Simple MySQL connection helper.
 * Uses env or system properties: DB_URL, DB_USER, DB_PASS.
 */
public final class Database {
    private static final ServerLogger LOG = ServerLogger.get(Database.class);

    private Database() {}

    public static Connection open() throws SQLException {
        String url = envOrProp("DB_URL", "jdbc:mysql://localhost:3306/emperorgame?useSSL=false&serverTimezone=UTC");
        String user = envOrProp("DB_USER", "root");
        String pass = envOrProp("DB_PASS", "");
        if (url.isBlank()) throw new SQLException("DB_URL is not configured");
        tryLoadDriver();
        LOG.debug("创建数据库连接 " + url);
        Properties props = new Properties();
        if (!user.isBlank()) props.setProperty("user", user);
        if (!pass.isBlank()) props.setProperty("password", pass);
        return DriverManager.getConnection(url, props);
    }

    private static void tryLoadDriver() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ignored) {
            // driver not on classpath; ignore
        }
    }

    private static String envOrProp(String key, String fallback) {
        String env = System.getenv(key);
        if (env != null && !env.isBlank()) return env.trim();
        String prop = System.getProperty(key);
        if (prop != null && !prop.isBlank()) return prop.trim();
        return Objects.requireNonNullElse(fallback, "");
    }
}
