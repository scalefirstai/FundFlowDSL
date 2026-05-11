package ai.getfundflow.dsl.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EnvLoaderTest {

    @Test
    void readsKeyValuePairsFromDotEnvFile(@TempDir Path dir) throws Exception {
        Path env = dir.resolve(".env");
        Files.writeString(env, """
                # comment line
                ANTHROPIC_API_KEY=sk-ant-test-123
                OTHER_KEY = "quoted value"
                """);
        EnvLoader loader = EnvLoader.load(env);
        assertThat(loader.get("ANTHROPIC_API_KEY")).contains("sk-ant-test-123");
        assertThat(loader.get("OTHER_KEY")).contains("quoted value");
    }

    @Test
    void anthropicApiKeyAcceptsTypoVariant(@TempDir Path dir) throws Exception {
        Path env = dir.resolve(".env");
        Files.writeString(env, "ANTROPIC_KEY=sk-ant-typo-456\n");
        EnvLoader loader = EnvLoader.load(env);
        assertThat(loader.anthropicApiKey()).contains("sk-ant-typo-456");
    }

    @Test
    void canonicalKeyWinsOverTypo(@TempDir Path dir) throws Exception {
        Path env = dir.resolve(".env");
        Files.writeString(env, """
                ANTHROPIC_API_KEY=canonical
                ANTROPIC_KEY=typo
                """);
        EnvLoader loader = EnvLoader.load(env);
        assertThat(loader.anthropicApiKey()).contains("canonical");
    }

    @Test
    void missingFileIsHandledGracefully(@TempDir Path dir) {
        Path env = dir.resolve("does_not_exist.env");
        EnvLoader loader = EnvLoader.load(env);
        // Should not throw; falls back to system env only
        assertThat(loader.get("DOES_NOT_EXIST")).isEmpty();
    }
}
