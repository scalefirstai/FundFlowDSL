package ai.getfundflow.dsl.cli;

import ai.getfundflow.dsl.ast.Program;
import ai.getfundflow.dsl.diagnostics.DiagnosticRenderer;
import ai.getfundflow.dsl.diagnostics.Formatter;
import ai.getfundflow.dsl.lsp.LspMain;
import ai.getfundflow.dsl.parser.AstBuilder;
import ai.getfundflow.dsl.parser.ParseException;
import ai.getfundflow.dsl.parser.ParseHarness;
import ai.getfundflow.dsl.parser.gen.FundFlowParser;
import ai.getfundflow.dsl.runtime.AuditSink;
import ai.getfundflow.dsl.runtime.DataSource;
import ai.getfundflow.dsl.runtime.EvaluationContext;
import ai.getfundflow.dsl.runtime.EvaluationResult;
import ai.getfundflow.dsl.runtime.Evaluator;
import ai.getfundflow.dsl.runtime.LedgerEntry;
import ai.getfundflow.dsl.runtime.MapDataSource;
import ai.getfundflow.dsl.runtime.RuntimeValue;
import ai.getfundflow.dsl.semantic.Diagnostic;
import ai.getfundflow.dsl.semantic.SemanticAnalyzer;
import ai.getfundflow.dsl.semantic.Severity;
import ai.getfundflow.dsl.semantic.SourceMap;
import ai.getfundflow.dsl.core.calendar.WeekendOnlyCalendar;
import ai.getfundflow.dsl.core.types.BusinessDate;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Entry point for the {@code fundflow} CLI (spec §12).
 *
 * <pre>
 * fundflow parse  &lt;file.ff&gt;                                     # Parse and print AST
 * fundflow check  &lt;file.ff&gt;                                     # Run full semantic analysis
 * fundflow run    &lt;file.ff&gt; --as-of D [--fixture F]             # Execute against fixture
 * fundflow format &lt;file.ff&gt; [--check]                           # Format in place (or check)
 * fundflow lsp                                                  # Start the LSP server on stdio
 *
 * Any subcommand accepts --json for machine-readable output.
 * All commands exit with non-zero status on any error.
 * </pre>
 */
public final class CliMain {

    private static final String USAGE = """
            Usage: fundflow <subcommand> [args] [--json]

              parse   <file.ff>
              check   <file.ff>
              run     <file.ff> --as-of YYYY-MM-DD [--fixture path]
              format  <file.ff> [--check]
              catalog [<file.ff>]
              agent   --prompt "..." [--context <file.ff>]
              lsp
            """;

    public static void main(String[] args) {
        int code;
        try {
            code = run(args, System.out, System.err);
        } catch (Exception e) {
            System.err.println("internal error: " + e.getMessage());
            code = 70;
        }
        System.exit(code);
    }

    public static int run(String[] args, PrintStream out, PrintStream err) throws Exception {
        if (args.length == 0) {
            err.println(USAGE);
            return 64;
        }
        String subcommand = args[0];
        String[] rest = new String[args.length - 1];
        System.arraycopy(args, 1, rest, 0, rest.length);

        return switch (subcommand) {
            case "parse" -> parseCommand(rest, out, err);
            case "check" -> checkCommand(rest, out, err);
            case "run" -> runCommand(rest, out, err);
            case "format" -> formatCommand(rest, out, err);
            case "catalog" -> catalogCommand(rest, out, err);
            case "agent" -> agentCommand(rest, out, err);
            case "lsp" -> lspCommand(rest, out, err);
            case "--help", "-h", "help" -> {
                out.println(USAGE);
                yield 0;
            }
            default -> {
                err.println("unknown subcommand: " + subcommand);
                err.println(USAGE);
                yield 64;
            }
        };
    }

    // ========================================================================
    // catalog
    // ========================================================================

    private static int catalogCommand(String[] args, PrintStream out, PrintStream err) throws IOException {
        Options opts = parseOptions(args);
        ai.getfundflow.dsl.semantic.SymbolTable symbols;
        ai.getfundflow.dsl.semantic.TypeRegistry types;
        if (opts.positional.isEmpty()) {
            symbols = new ai.getfundflow.dsl.semantic.SymbolTable();
            types = new ai.getfundflow.dsl.semantic.TypeRegistry();
        } else {
            Path file = Paths.get(opts.positional.get(0));
            String source = Files.readString(file);
            ai.getfundflow.dsl.parser.AstBuilder builder = new ai.getfundflow.dsl.parser.AstBuilder();
            ai.getfundflow.dsl.ast.Program program;
            try {
                program = builder.build(
                        (ai.getfundflow.dsl.parser.gen.FundFlowParser.ProgramContext)
                                ai.getfundflow.dsl.parser.ParseHarness.parse(source),
                        file.toString());
            } catch (RuntimeException e) {
                err.println("parse error: " + e.getMessage());
                return 1;
            }
            ai.getfundflow.dsl.semantic.SemanticAnalyzer.SemanticResult result =
                    new ai.getfundflow.dsl.semantic.SemanticAnalyzer().analyze(program, builder.sourceMap());
            symbols = result.symbols();
            types = result.types();
        }
        ai.getfundflow.dsl.agent.Catalog catalog = ai.getfundflow.dsl.agent.Catalog.build(symbols, types);
        out.println(JsonWriter.write(catalog.toJsonShape()));
        return 0;
    }

    // ========================================================================
    // agent
    // ========================================================================

    private static int agentCommand(String[] args, PrintStream out, PrintStream err) throws IOException {
        Options opts = parseOptions(args);
        if (opts.prompt == null) {
            err.println("agent: --prompt \"...\" is required");
            return 64;
        }
        ai.getfundflow.dsl.agent.EnvLoader envLoader = ai.getfundflow.dsl.agent.EnvLoader.load();
        String apiKey = envLoader.anthropicApiKey().orElse(null);
        if (apiKey == null) {
            err.println("agent: ANTHROPIC_API_KEY (or ANTROPIC_KEY) not set in env or .env");
            return 78;
        }

        // Build catalog from optional --context file
        ai.getfundflow.dsl.semantic.SymbolTable symbols;
        ai.getfundflow.dsl.semantic.TypeRegistry types;
        if (opts.contextPath == null) {
            symbols = new ai.getfundflow.dsl.semantic.SymbolTable();
            types = new ai.getfundflow.dsl.semantic.TypeRegistry();
        } else {
            String source = Files.readString(opts.contextPath);
            ai.getfundflow.dsl.parser.AstBuilder builder = new ai.getfundflow.dsl.parser.AstBuilder();
            ai.getfundflow.dsl.ast.Program program = builder.build(
                    (ai.getfundflow.dsl.parser.gen.FundFlowParser.ProgramContext)
                            ai.getfundflow.dsl.parser.ParseHarness.parse(source),
                    opts.contextPath.toString());
            ai.getfundflow.dsl.semantic.SemanticAnalyzer.SemanticResult ctxResult =
                    new ai.getfundflow.dsl.semantic.SemanticAnalyzer().analyze(program, builder.sourceMap());
            symbols = ctxResult.symbols();
            types = ctxResult.types();
        }

        ai.getfundflow.dsl.agent.Catalog catalog = ai.getfundflow.dsl.agent.Catalog.build(symbols, types);
        String systemPrompt = new ai.getfundflow.dsl.agent.SystemPrompt(catalog).render();
        ai.getfundflow.dsl.agent.Agent agent =
                new ai.getfundflow.dsl.agent.AnthropicAgent(apiKey, systemPrompt);
        ai.getfundflow.dsl.agent.AgentLoop loop = new ai.getfundflow.dsl.agent.AgentLoop(
                agent, new ai.getfundflow.dsl.agent.Validator());
        ai.getfundflow.dsl.agent.AgentLoop.AgentResult result = loop.run(opts.prompt);

        if (opts.json) {
            Map<String, Object> shape = new LinkedHashMap<>();
            shape.put("accepted", result.accepted());
            shape.put("attempts", result.attemptsUsed());
            shape.put("program", result.program());
            shape.put("finalErrors", result.finalReport().errors().stream()
                    .map(CliMain::diagnosticToMap).toList());
            out.println(JsonWriter.write(shape));
        } else {
            out.println("# attempts: " + result.attemptsUsed()
                    + " (accepted=" + result.accepted() + ")");
            out.println();
            out.println(result.program());
            if (!result.accepted()) {
                err.println("agent did not converge within " + result.attemptsUsed()
                        + " attempts; final errors:");
                result.finalReport().errors()
                        .forEach(d -> err.println("  " + d.code().code() + " " + d.message()));
            }
        }
        return result.accepted() ? 0 : 1;
    }

    // ========================================================================
    // parse
    // ========================================================================

    private static int parseCommand(String[] args, PrintStream out, PrintStream err) throws IOException {
        Options opts = parseOptions(args);
        if (opts.positional.size() != 1) {
            err.println("parse: expected exactly one file argument");
            return 64;
        }
        Path file = Paths.get(opts.positional.get(0));
        String source = Files.readString(file);
        Program program;
        try {
            FundFlowParser.ProgramContext ctx =
                    (FundFlowParser.ProgramContext) ParseHarness.parse(source);
            program = new AstBuilder().build(ctx, file.toString());
        } catch (ParseException e) {
            err.println("parse error at " + file + ":" + e.line() + ":" + (e.charPositionInLine() + 1)
                    + " " + e.getMessage());
            return 1;
        } catch (RuntimeException e) {
            err.println("parse error at " + file + ": " + e.getMessage());
            return 1;
        }
        if (opts.json) {
            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("file", file.toString());
            summary.put("module", program.module().map(m -> String.join(".", m.path())).orElse(null));
            summary.put("imports", program.imports().stream()
                    .map(i -> String.join(".", i.path())).toList());
            summary.put("topLevelDecls", program.declarations().size());
            out.println(JsonWriter.write(summary));
        } else {
            out.println(program);
        }
        return 0;
    }

    // ========================================================================
    // check
    // ========================================================================

    private static int checkCommand(String[] args, PrintStream out, PrintStream err) throws IOException {
        Options opts = parseOptions(args);
        if (opts.positional.size() != 1) {
            err.println("check: expected exactly one file argument");
            return 64;
        }
        Path file = Paths.get(opts.positional.get(0));
        String source = Files.readString(file);
        AstBuilder builder = new AstBuilder();
        Program program;
        try {
            FundFlowParser.ProgramContext ctx =
                    (FundFlowParser.ProgramContext) ParseHarness.parse(source);
            program = builder.build(ctx, file.toString());
        } catch (ParseException e) {
            err.println("parse error: " + e.getMessage());
            return 1;
        } catch (RuntimeException e) {
            err.println("parse error: " + e.getMessage());
            return 1;
        }
        SemanticAnalyzer.SemanticResult result =
                new SemanticAnalyzer().analyze(program, builder.sourceMap());

        if (opts.json) {
            out.println(JsonWriter.write(Map.of(
                    "file", file.toString(),
                    "diagnostics", result.diagnostics().all().stream()
                            .map(CliMain::diagnosticToMap).toList(),
                    "errorCount", result.diagnostics().errors().size(),
                    "warningCount", result.diagnostics().warnings().size())));
        } else {
            for (Diagnostic d : result.diagnostics().all()) {
                out.println(DiagnosticRenderer.render(d, source));
            }
            int errors = result.diagnostics().errors().size();
            int warnings = result.diagnostics().warnings().size();
            out.println(errors + " error(s), " + warnings + " warning(s)");
        }
        return result.diagnostics().hasErrors() ? 1 : 0;
    }

    private static Map<String, Object> diagnosticToMap(Diagnostic d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("code", d.code().code());
        m.put("severity", d.severity().name().toLowerCase());
        m.put("message", d.message());
        m.put("file", d.location().file());
        m.put("line", d.location().line());
        m.put("column", d.location().column());
        m.put("length", d.location().length());
        d.hint().ifPresent(h -> m.put("hint", h));
        return m;
    }

    // ========================================================================
    // run
    // ========================================================================

    private static int runCommand(String[] args, PrintStream out, PrintStream err) throws IOException {
        Options opts = parseOptions(args);
        if (opts.positional.size() != 1) {
            err.println("run: expected exactly one file argument");
            return 64;
        }
        LocalDate asOf = opts.asOf;
        if (asOf == null) {
            err.println("run: --as-of YYYY-MM-DD is required");
            return 64;
        }
        Path file = Paths.get(opts.positional.get(0));
        String source = Files.readString(file);
        AstBuilder builder = new AstBuilder();
        Program program;
        try {
            FundFlowParser.ProgramContext ctx =
                    (FundFlowParser.ProgramContext) ParseHarness.parse(source);
            program = builder.build(ctx, file.toString());
        } catch (ParseException e) {
            err.println("parse error: " + e.getMessage());
            return 1;
        } catch (RuntimeException e) {
            err.println("parse error: " + e.getMessage());
            return 1;
        }
        SemanticAnalyzer.SemanticResult sem = new SemanticAnalyzer()
                .analyze(program, builder.sourceMap());
        if (sem.diagnostics().hasErrors()) {
            err.println(sem.diagnostics().errors().size() + " semantic error(s); aborting.");
            for (Diagnostic d : sem.diagnostics().errors()) {
                err.println(DiagnosticRenderer.render(d, source));
            }
            return 1;
        }

        DataSource ds = opts.fixturePath == null
                ? DataSource.empty()
                : MapDataSource.of(FixtureLoader.load(opts.fixturePath));
        EvaluationContext ctx = new EvaluationContext(
                new BusinessDate(asOf, WeekendOnlyCalendar.DEFAULT),
                ds,
                WeekendOnlyCalendar.DEFAULT,
                AuditSink.discarding());
        EvaluationResult result = new Evaluator().evaluate(program, ctx);

        if (opts.json) {
            out.println(JsonWriter.write(Map.of(
                    "file", file.toString(),
                    "asOf", asOf.toString(),
                    "outputs", outputsToMap(result.outputs()),
                    "postings", result.postings().stream()
                            .map(CliMain::postingToMap).toList(),
                    "auditEntries", result.trail().size())));
        } else {
            out.println("# outputs");
            result.outputs().forEach((k, v) -> out.println("  " + k + " = " + renderValue(v)));
            out.println();
            out.println("# postings (" + result.postings().size() + ")");
            for (LedgerEntry e : result.postings()) {
                out.println("  " + e.date() + "  " + e.account() + "  "
                        + e.amount().currency().getCurrencyCode() + " " + e.amount().amount().toPlainString()
                        + e.narrative().map(n -> "  // " + n).orElse(""));
            }
            out.println();
            out.println("# audit trail: " + result.trail().size() + " entries; hash="
                    + result.trail().contentHash().substring(0, 16));
        }
        return 0;
    }

    private static Map<String, Object> outputsToMap(Map<String, RuntimeValue> outputs) {
        Map<String, Object> m = new LinkedHashMap<>();
        outputs.forEach((k, v) -> m.put(k, renderValue(v)));
        return m;
    }

    private static Map<String, Object> postingToMap(LedgerEntry e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("date", e.date().toString());
        m.put("account", e.account());
        m.put("amount", e.amount().currency().getCurrencyCode() + " " + e.amount().amount().toPlainString());
        e.narrative().ifPresent(n -> m.put("narrative", n));
        m.put("sourceRule", e.sourceRule());
        return m;
    }

    private static String renderValue(RuntimeValue v) {
        return switch (v) {
            case RuntimeValue.MoneyVal m -> m.value().currency().getCurrencyCode()
                    + " " + m.value().amount().toPlainString();
            case RuntimeValue.PercentVal p -> p.value().asPercent().toPlainString() + "%";
            case RuntimeValue.NumberVal n -> n.value().toPlainString();
            case RuntimeValue.BoolVal b -> Boolean.toString(b.value());
            case RuntimeValue.DateVal d -> d.value().toString();
            case RuntimeValue.PeriodVal p -> p.start() + ".." + p.end();
            case RuntimeValue.DayCountVal d -> d.value().code();
            case RuntimeValue.StringVal s -> s.value();
            case RuntimeValue.ListVal l -> "[" + l.values().size() + " items]";
            case RuntimeValue.NullVal n -> "null";
        };
    }

    // ========================================================================
    // format
    // ========================================================================

    private static int formatCommand(String[] args, PrintStream out, PrintStream err) throws IOException {
        Options opts = parseOptions(args);
        if (opts.positional.size() != 1) {
            err.println("format: expected exactly one file argument");
            return 64;
        }
        Path file = Paths.get(opts.positional.get(0));
        String source = Files.readString(file);
        Program program;
        try {
            FundFlowParser.ProgramContext ctx =
                    (FundFlowParser.ProgramContext) ParseHarness.parse(source);
            program = new AstBuilder().build(ctx, file.toString());
        } catch (ParseException e) {
            err.println("parse error: " + e.getMessage());
            return 1;
        } catch (RuntimeException e) {
            err.println("parse error: " + e.getMessage());
            return 1;
        }
        String formatted = Formatter.format(program);

        if (opts.checkOnly) {
            if (!formatted.equals(source)) {
                err.println(file + " is not formatted");
                return 1;
            }
            out.println(file + " is formatted");
            return 0;
        }
        Files.writeString(file, formatted);
        out.println("formatted " + file);
        return 0;
    }

    // ========================================================================
    // lsp
    // ========================================================================

    private static int lspCommand(String[] args, PrintStream out, PrintStream err) throws Exception {
        return LspMain.run(System.in, System.out);
    }

    // ========================================================================
    // arg parsing
    // ========================================================================

    private static Options parseOptions(String[] args) {
        Options opts = new Options();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--json" -> opts.json = true;
                case "--check" -> opts.checkOnly = true;
                case "--as-of" -> {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--as-of requires a date");
                    }
                    opts.asOf = LocalDate.parse(args[++i]);
                }
                case "--fixture" -> {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--fixture requires a path");
                    }
                    opts.fixturePath = Paths.get(args[++i]);
                }
                case "--prompt" -> {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--prompt requires a value");
                    }
                    opts.prompt = args[++i];
                }
                case "--context" -> {
                    if (i + 1 >= args.length) {
                        throw new IllegalArgumentException("--context requires a path");
                    }
                    opts.contextPath = Paths.get(args[++i]);
                }
                default -> opts.positional.add(a);
            }
        }
        return opts;
    }

    private static final class Options {
        boolean json = false;
        boolean checkOnly = false;
        LocalDate asOf = null;
        Path fixturePath = null;
        String prompt = null;
        Path contextPath = null;
        final List<String> positional = new ArrayList<>();
    }
}
