package ai.getfundflow.dsl.parser;

import static org.assertj.core.api.Assertions.assertThat;

import ai.getfundflow.dsl.ast.PrettyPrinter;
import ai.getfundflow.dsl.ast.Program;
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

class RoundTripTest {

    @ParameterizedTest(name = "round-trip: {0}")
    @MethodSource("validCorpus")
    void parseBuildPrintReparseYieldsEquivalentAst(Path path) throws IOException {
        String source = Files.readString(path);

        FundFlowParser.ProgramContext firstParse = (FundFlowParser.ProgramContext) ParseHarness.parse(source);
        AstBuilder builder = new AstBuilder();
        Program firstAst = builder.build(firstParse);

        String printed = PrettyPrinter.print(firstAst);

        FundFlowParser.ProgramContext secondParse;
        try {
            secondParse = (FundFlowParser.ProgramContext) ParseHarness.parse(printed);
        } catch (RuntimeException e) {
            throw new AssertionError(
                    "Pretty-printed output failed to re-parse for " + path.getFileName()
                            + "\n--- printed ---\n" + printed + "\n--- error ---\n" + e.getMessage(),
                    e);
        }
        Program secondAst = builder.build(secondParse);

        assertThat(secondAst)
                .as("AST equality after round-trip for " + path.getFileName()
                        + "\n--- printed ---\n" + printed)
                .isEqualTo(firstAst);
    }

    static Stream<Path> validCorpus() {
        try {
            Path root = Paths.get(
                    RoundTripTest.class.getClassLoader().getResource("corpus/valid").toURI());
            try (Stream<Path> stream = Files.walk(root)) {
                List<Path> files = stream
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".ff"))
                        .sorted()
                        .collect(Collectors.toList());
                return files.stream();
            }
        } catch (IOException | URISyntaxException e) {
            throw new RuntimeException("Failed to list corpus", e);
        }
    }
}
