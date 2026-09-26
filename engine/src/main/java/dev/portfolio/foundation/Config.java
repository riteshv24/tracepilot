package dev.portfolio.foundation;

import java.nio.file.Path;
import java.util.*;

/** No secrets are persisted in the workspace or exposed in /api/meta. */
public record Config(int port, String bind, String token, String allowedHost, Path data, Path web, String jdbc, String dbUser, String dbPassword, String ollama, String model, String embeddingModel) {
    public static Config fromEnvironment(int defaultPort) {
        Map<String, String> env=System.getenv();
        int port=Integer.parseInt(env.getOrDefault("PORT", Integer.toString(defaultPort)));
        Checks.range(port, "PORT", 1, 65535);
        String bind=env.getOrDefault("APP_BIND", "127.0.0.1");
        String token=env.getOrDefault("APP_TOKEN", "");
        if(!Set.of("127.0.0.1", "localhost", "::1").contains(bind)&&token.length()<24) throw new IllegalStateException("A non-loopback bind requires APP_TOKEN with at least 24 characters");
        return new Config(port, bind, token, env.getOrDefault("APP_PUBLIC_HOST", ""), Path.of(env.getOrDefault("APP_DATA", ".local")), Path.of(env.getOrDefault("APP_WEB", "web")), env.getOrDefault("JDBC_URL", ""), env.getOrDefault("DB_USER", "portfolio"), env.getOrDefault("DB_PASSWORD", ""), env.getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434"), env.getOrDefault("OLLAMA_MODEL", ""), env.getOrDefault("OLLAMA_EMBED_MODEL", ""));
    }
    public Store createStore() {
        return jdbc.isBlank()?new FileStore(data):new JdbcStore(jdbc, dbUser, dbPassword);
    }
    public ModelClient createModel() {
        return new ModelClient(ollama, model, embeddingModel);
    }
}
