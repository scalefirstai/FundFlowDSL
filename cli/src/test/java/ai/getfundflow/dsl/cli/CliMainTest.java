package ai.getfundflow.dsl.cli;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CliMainTest {

    private static final String GOOD_RULE = """
            rule "Good" {
              description: "minimal rule"
              let x = USD 100
            }
            """;

    private static final String BAD_RULE = """
            rule "Bad" {
              let y = unknown_binding
            }
            """;

    private static final String MGMT_FEE = """
            rule "Daily Mgmt Fee" {
              effective: from 2026-03-01
              let rate      = 1.5% per annum
              let basis     = opening nav
              let day_count = actual/365
              accrue rate on basis using day_count
              post to ledger account "Management Fee Payable"
                with narrative "Daily mgmt fee accrual"
            }
            """;

    private Result run(String... args) throws Exception {
        ByteArrayOutputStream outBuf = new ByteArrayOutputStream();
        ByteArrayOutputStream errBuf = new ByteArrayOutputStream();
        int code;
        try (PrintStream out = new PrintStream(outBuf, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(errBuf, true, StandardCharsets.UTF_8)) {
            code = CliMain.run(args, out, err);
        }
        return new Result(code,
                outBuf.toString(StandardCharsets.UTF_8),
                errBuf.toString(StandardCharsets.UTF_8));
    }

    private Path write(Path dir, String name, String content) throws Exception {
        Path p = dir.resolve(name);
        Files.writeString(p, content);
        return p;
    }

    // ---- parse -----------------------------------------------------------

    @Test
    void parseEmitsAstSummaryExitZero(@TempDir Path dir) throws Exception {
        Path file = write(dir, "good.ff", GOOD_RULE);
        Result r = run("parse", file.toString());
        assertThat(r.code).isZero();
        assertThat(r.out).contains("Good");
    }

    @Test
    void parseJsonProducesParseableJson(@TempDir Path dir) throws Exception {
        Path file = write(dir, "good.ff", GOOD_RULE);
        Result r = run("parse", file.toString(), "--json");
        assertThat(r.code).isZero();
        assertThat(r.out).contains("\"topLevelDecls\"");
        assertThat(r.out).contains("\"file\"");
    }

    @Test
    void parseRejectsBadSyntax(@TempDir Path dir) throws Exception {
        Path file = write(dir, "broken.ff", "rule {}\n");
        Result r = run("parse", file.toString());
        assertThat(r.code).isNotZero();
        assertThat(r.err).contains("parse error");
    }

    // ---- check -----------------------------------------------------------

    @Test
    void checkExitsZeroOnCleanProgram(@TempDir Path dir) throws Exception {
        Path file = write(dir, "good.ff", GOOD_RULE);
        Result r = run("check", file.toString());
        assertThat(r.code).isZero();
        assertThat(r.out).contains("0 error(s)");
    }

    @Test
    void checkExitsNonZeroOnUnresolvedBinding(@TempDir Path dir) throws Exception {
        Path file = write(dir, "bad.ff", BAD_RULE);
        Result r = run("check", file.toString());
        assertThat(r.code).isOne();
        assertThat(r.out).contains("FF2002");
        assertThat(r.out).contains("unknown_binding");
    }

    @Test
    void checkJsonProducesStructuredDiagnostics(@TempDir Path dir) throws Exception {
        Path file = write(dir, "bad.ff", BAD_RULE);
        Result r = run("check", file.toString(), "--json");
        assertThat(r.code).isOne();
        assertThat(r.out).contains("\"FF2002\"");
        assertThat(r.out).contains("\"errorCount\"");
    }

    // ---- run -------------------------------------------------------------

    @Test
    void runRequiresAsOf(@TempDir Path dir) throws Exception {
        Path file = write(dir, "good.ff", GOOD_RULE);
        Result r = run("run", file.toString());
        assertThat(r.code).isNotZero();
        assertThat(r.err).contains("--as-of");
    }

    @Test
    void runEvaluatesWithFixture(@TempDir Path dir) throws Exception {
        Path file = write(dir, "mgmt.ff", MGMT_FEE);
        Path fixture = write(dir, "data.fixture",
                "opening nav = USD 10000000\n");
        Result r = run("run", file.toString(),
                "--as-of", "2026-03-15",
                "--fixture", fixture.toString());
        assertThat(r.code).isZero();
        assertThat(r.out).contains("Management Fee Payable");
        assertThat(r.out).contains("410.96");
    }

    @Test
    void runJsonEmitsPostings(@TempDir Path dir) throws Exception {
        Path file = write(dir, "mgmt.ff", MGMT_FEE);
        Path fixture = write(dir, "data.fixture",
                "opening nav = USD 10000000\n");
        Result r = run("run", file.toString(),
                "--as-of", "2026-03-15",
                "--fixture", fixture.toString(),
                "--json");
        assertThat(r.code).isZero();
        assertThat(r.out).contains("\"postings\"");
        assertThat(r.out).contains("\"USD 410.96\"");
    }

    @Test
    void runAbortsOnSemanticErrors(@TempDir Path dir) throws Exception {
        Path file = write(dir, "bad.ff", BAD_RULE);
        Result r = run("run", file.toString(), "--as-of", "2026-01-01");
        assertThat(r.code).isOne();
        assertThat(r.err).contains("semantic error");
    }

    // ---- format ----------------------------------------------------------

    @Test
    void formatRewritesFileInPlace(@TempDir Path dir) throws Exception {
        Path file = write(dir, "sloppy.ff",
                "rule    \"Sloppy\"   {  description: \"x\"   }");
        Result r = run("format", file.toString());
        assertThat(r.code).isZero();
        String after = Files.readString(file);
        assertThat(after).startsWith("rule \"Sloppy\" {");
        assertThat(after).endsWith("\n");
    }

    @Test
    void formatCheckOnlyDoesNotMutate(@TempDir Path dir) throws Exception {
        String sloppy = "rule    \"Sloppy\"   {  description: \"x\"   }";
        Path file = write(dir, "sloppy.ff", sloppy);
        Result r = run("format", file.toString(), "--check");
        assertThat(r.code).isOne();
        assertThat(r.err).contains("is not formatted");
        assertThat(Files.readString(file)).isEqualTo(sloppy);
    }

    @Test
    void formatCheckPassesOnAlreadyFormatted(@TempDir Path dir) throws Exception {
        Path file = write(dir, "good.ff", GOOD_RULE);
        // First normalize it
        run("format", file.toString());
        Result r = run("format", file.toString(), "--check");
        assertThat(r.code).isZero();
    }

    // ---- usage / help ----------------------------------------------------

    @Test
    void noArgsPrintsUsage() throws Exception {
        Result r = run();
        assertThat(r.code).isNotZero();
        assertThat(r.err).contains("Usage: fundflow");
    }

    @Test
    void unknownSubcommandPrintsUsage() throws Exception {
        Result r = run("nope");
        assertThat(r.code).isNotZero();
        assertThat(r.err).contains("unknown subcommand: nope");
    }

    @Test
    void helpExitsZero() throws Exception {
        Result r = run("--help");
        assertThat(r.code).isZero();
        assertThat(r.out).contains("Usage: fundflow");
    }

    record Result(int code, String out, String err) {}
}
