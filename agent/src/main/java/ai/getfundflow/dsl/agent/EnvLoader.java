package ai.getfundflow.dsl.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Tiny {@code .env} loader.
 *
 * <p>Reads {@code KEY=VALUE} lines from a file (default: {@code .env} in the current
 * working directory) and merges them with the JVM's existing environment, with the
 * JVM environment taking precedence. Values may be quoted; lines starting with {@code #}
 * are treated as comments.
 *
 * <p>For the Anthropic API key specifically, {@link #anthropicApiKey()} accepts either
 * the canonical {@code ANTHROPIC_API_KEY} or the variant {@code ANTROPIC_KEY} (typo
 * tolerance for the {@code .env} on this project).
 */
public final class EnvLoader {

    private final Map<String, String> values;

    private EnvLoader(Map<String, String> values) {
        this.values = Map.copyOf(values);
    }

    public static EnvLoader load() {
        return load(Paths.get(".env"));
    }

    public static EnvLoader load(Path envFile) {
        Map<String, String> merged = new LinkedHashMap<>();
        if (Files.exists(envFile)) {
            try {
                for (String line : Files.readAllLines(envFile)) {
                    parseLine(line, merged);
                }
            } catch (IOException e) {
                throw new RuntimeException("failed to read " + envFile, e);
            }
        }
        // System env takes precedence over .env
        for (Map.Entry<String, String> e : System.getenv().entrySet()) {
            merged.put(e.getKey(), e.getValue());
        }
        return new EnvLoader(merged);
    }

    private static void parseLine(String line, Map<String, String> into) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) return;
        int eq = trimmed.indexOf('=');
        if (eq < 0) return;
        String key = trimmed.substring(0, eq).trim();
        String raw = trimmed.substring(eq + 1).trim();
        if (raw.length() >= 2
                && ((raw.startsWith("\"") && raw.endsWith("\""))
                        || (raw.startsWith("'") && raw.endsWith("'")))) {
            raw = raw.substring(1, raw.length() - 1);
        }
        into.put(key, raw);
    }

    public Optional<String> get(String key) {
        return Optional.ofNullable(values.get(key));
    }

    public String require(String key) {
        return get(key).orElseThrow(() -> new IllegalStateException(
                "missing required environment variable: " + key));
    }

    /** Resolves the Anthropic API key from either {@code ANTHROPIC_API_KEY} or {@code ANTROPIC_KEY}. */
    public Optional<String> anthropicApiKey() {
        return get("ANTHROPIC_API_KEY").or(() -> get("ANTROPIC_KEY"));
    }
}
