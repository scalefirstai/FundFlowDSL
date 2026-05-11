package ai.getfundflow.dsl.parser;

import static org.assertj.core.api.Assertions.assertThat;

import ai.getfundflow.dsl.ast.Program;
import ai.getfundflow.dsl.diagnostics.Formatter;
import ai.getfundflow.dsl.parser.gen.FundFlowParser;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Spec §10.3: {@code format(format(x)) == format(x)} for all valid programs.
 * We run every program in {@code corpus/valid/} through one formatter pass,
 * re-parse the result, format it again, and assert byte-equality.
 */
class FormatterIdempotenceTest {

    @ParameterizedTest(name = "idempotent: {0}")
    @MethodSource("validCorpus")
    void formatTwiceMatchesFormatOnce(Path path) throws IOException {
        String source = Files.readString(path);

        FundFlowParser.ProgramContext parse1 = (FundFlowParser.ProgramContext) ParseHarness.parse(source);
        Program ast1 = new AstBuilder().build(parse1);
        String formattedOnce = Formatter.format(ast1);

        FundFlowParser.ProgramContext parse2 = (FundFlowParser.ProgramContext) ParseHarness.parse(formattedOnce);
        Program ast2 = new AstBuilder().build(parse2);
        String formattedTwice = Formatter.format(ast2);

        assertThat(formattedTwice).isEqualTo(formattedOnce);
    }

    @ParameterizedTest(name = "ends with newline: {0}")
    @MethodSource("validCorpus")
    void formatterAlwaysEndsWithNewline(Path path) throws IOException {
        String source = Files.readString(path);
        Program ast = new AstBuilder().build(
                (FundFlowParser.ProgramContext) ParseHarness.parse(source));
        String formatted = Formatter.format(ast);
        assertThat(formatted).endsWith("\n");
    }

    @ParameterizedTest(name = "no trailing whitespace: {0}")
    @MethodSource("validCorpus")
    void formatterStripsTrailingWhitespace(Path path) throws IOException {
        String source = Files.readString(path);
        Program ast = new AstBuilder().build(
                (FundFlowParser.ProgramContext) ParseHarness.parse(source));
        String formatted = Formatter.format(ast);
        for (String line : formatted.split("\n", -1)) {
            assertThat(line).doesNotMatch(".*[ \\t]$");
        }
    }

    static Stream<Path> validCorpus() {
        try {
            Path root = Paths.get(
                    FormatterIdempotenceTest.class.getClassLoader().getResource("corpus/valid").toURI());
            try (Stream<Path> stream = Files.walk(root)) {
                List<Path> files = stream
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".ff"))
                        .sorted()
                        .collect(Collectors.toList());
                return files.stream();
            }
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }
}
