package ai.getfundflow.dsl.parser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class CorpusParseTest {

    @ParameterizedTest(name = "valid: {0}")
    @MethodSource("validCorpus")
    void validProgramsParseCleanly(Path path) throws IOException {
        String source = Files.readString(path);
        ParseHarness.parse(source);
    }

    @ParameterizedTest(name = "invalid: {0}")
    @MethodSource("invalidCorpus")
    void invalidProgramsFailWithLocatableError(Path path) throws IOException {
        String source = Files.readString(path);
        assertThatThrownBy(() -> ParseHarness.parse(source))
                .satisfiesAnyOf(
                        e -> assertThat(e).isInstanceOf(ParseException.class),
                        e -> assertThat(e).isInstanceOf(ParseCancellationException.class));
    }

    static Stream<Path> validCorpus() {
        return listCorpus("valid");
    }

    static Stream<Path> invalidCorpus() {
        return listCorpus("invalid");
    }

    private static Stream<Path> listCorpus(String subdir) {
        try {
            Path root = Paths.get(
                    CorpusParseTest.class.getClassLoader().getResource("corpus/" + subdir).toURI());
            try (Stream<Path> stream = Files.walk(root)) {
                List<Path> files = stream
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".ff"))
                        .sorted()
                        .collect(Collectors.toList());
                return files.stream();
            }
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException("Failed to list corpus: " + subdir, e);
        }
    }
}
