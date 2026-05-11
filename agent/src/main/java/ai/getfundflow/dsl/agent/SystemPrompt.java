package ai.getfundflow.dsl.agent;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Builds the agent's system prompt: the stable, cacheable portion of the request.
 *
 * <p>Includes:
 * <ul>
 *   <li>DSL surface-syntax reference</li>
 *   <li>The current {@link Catalog} (type system + extensions + in-scope decls + functions)</li>
 *   <li>The 6 canonical examples (read from {@code examples/} on disk if available)</li>
 *   <li>Hard rule: don't invent identifiers (§14.2)</li>
 * </ul>
 *
 * <p>The output is intentionally large (>4K tokens) so prompt caching on Opus 4.7
 * has a meaningful prefix to cache.
 */
public final class SystemPrompt {

    private static final String DSL_REFERENCE = """
            # FundFlow DSL — Reference

            ## File shape

                module fundflow.examples.alpha          // optional
                import fundflow.stdlib.fees             // optional, repeatable
                rule "..." { ... }                      // one or more
                schedule "..." { ... }                  // optional
                waterfall "..." { ... }                 // optional
                policy "..." { ... }                    // optional
                type extension X extends Fund { ... }   // optional

            ## Rule body (clauses, any order)

                description: "..."                      // optional
                applies to: <selector>                  // optional
                effective:  <period>                    // optional
                let <name> = <expression>               // any number
                <statement>                             // any number

            ## Statements

                accrue <rate> on <basis> using <day_count>
                allocate <amount> across <set> by <weight>       // pro-rata
                allocate <amount> across <set> equally
                distribute <amount> through waterfall "<name>"
                post <amount> to ledger account "<name>" [with narrative "<text>"]
                post to ledger account "<name>" with narrative "<text>"   // consumes prior accrue
                post each allocation to ledger account "<name>"
                publish <expression> [as of <date>]
                when <cond> then <stmt> [else <stmt>]

            ## Expressions

                <expr> as of <date>
                <expr> at start of <period>
                <expr> at end of <period>
                <expr> over <period> using <day_count>
                <expr> per annum
                <expr> + <expr> | - | * | /                       // arithmetic
                <expr> < <expr>  | <= | > | >= | == | !=          // comparison
                <expr> and <expr> | or | not                      // boolean
                func(arg1, arg2, ...)                             // function call
                sum of <expr> [by <expr>]
                weighted average <expr> weighted by <expr>
                <noun phrase>                                     // resolved against catalog
                <field> of <base_type> "<entity>"                 // extension field

            ## Type rules

                Money + Money (same currency)        → Money
                Money + Money (different currency)   → ERROR
                Money * Percentage                   → Money
                Money * Money                        → ERROR (use Money * Percentage)
                Money * Number                       → Money
                Percentage + Percentage              → Percentage
                Number * Number                      → Number
                Money / Money                        → Number (ratio)
                Money / Number                       → Money
                <Money> as of <Date>                 → Money

            ## Literals

                USD 1,250,000.00       // Money — 3-letter ISO code + amount
                EUR 50_000             // underscores OK
                JPY 1_000_000          // no decimals enforced for JPY
                1.5%                   // Percentage
                25 bps                 // basis points
                2026-03-31             // ISO date
                actual/365             // day-count convention
                "string literal"

            ## Determinism rules

                - today() and now() are forbidden — the asOf date is in EvaluationContext
                - Wall-clock time is never read inside a rule
                - All rounding is HALF_EVEN at currency fraction digits

            """;

    private static final String INSTRUCTIONS = """
            # Your task

            Generate a FundFlow DSL program (a `.ff` source file) that satisfies the user's
            request. **You must follow these rules:**

            1. **Output only the .ff program**, wrapped in a single fenced code block:

                   ```fundflow
                   rule "..." {
                     ...
                   }
                   ```

               No prose before or after. No explanations. The validator parses the contents
               between the fences directly.

            2. **Never invent identifiers.** Every name you reference — fund names, share
               class names, ledger accounts, waterfalls, rule names, let-bindings — must
               either:
               - be declared inline (with `let`, with `rule "..."`, with `waterfall "..."`,
                 or as a field on a `type extension`), or
               - appear in the catalog you were given (in `declarations`, `extensions`,
                 or `functions`).

               If you cannot find a needed name in the catalog, declare it with a `let`
               binding using a literal placeholder value. Do NOT make up plausible-looking
               names — the validator rejects unresolved references.

            3. **Use only the stdlib functions in the catalog.** No `today`, no `now`, no
               other names. The 32 functions in the catalog (`abs`, `round`, `max`, `min`,
               `npv`, `irr`, `year`, `month`, `datediff`, etc.) are exhaustive.

            4. **Use only the type-extension fields in the catalog.** A reference like
               `commitment_period of fund "Acme"` requires `commitment_period` to be a
               declared field on some `type extension … extends Fund`.

            5. **Follow the type rules.** `Money + Money` requires same currency.
               `Money * Money` is forbidden — use `Money * Percentage` instead.

            6. **If the validator rejected a previous attempt**, the diagnostics will be
               appended to your conversation. Read every diagnostic, fix every issue, and
               try again. Each diagnostic includes a code (`FFxxxx`), a message, and a
               source location.
            """;

    private final Catalog catalog;
    private final String exampleCorpus;

    public SystemPrompt(Catalog catalog) {
        this(catalog, defaultExampleCorpus());
    }

    public SystemPrompt(Catalog catalog, String exampleCorpus) {
        this.catalog = catalog;
        this.exampleCorpus = exampleCorpus;
    }

    public String render() {
        StringBuilder sb = new StringBuilder();
        sb.append(DSL_REFERENCE).append('\n');
        sb.append("# Available identifiers (the catalog)\n\n");
        sb.append("This is the authoritative list of names you may reference. ");
        sb.append("Anything else MUST be declared inline in the .ff program.\n\n");
        sb.append("```json\n");
        sb.append(renderCatalogJson()).append('\n');
        sb.append("```\n\n");
        if (!exampleCorpus.isEmpty()) {
            sb.append("# Canonical examples\n\n");
            sb.append("These are working programs from the spec. Match their shape and style.\n\n");
            sb.append(exampleCorpus).append('\n');
        }
        sb.append(INSTRUCTIONS);
        return sb.toString();
    }

    private String renderCatalogJson() {
        return prettyJson(catalog.toJsonShape(), 0);
    }

    /** Tiny indent-aware JSON renderer to avoid a hard dependency on the CLI's writer. */
    private static String prettyJson(Object value, int depth) {
        StringBuilder sb = new StringBuilder();
        appendJson(sb, value, depth);
        return sb.toString();
    }

    private static void appendJson(StringBuilder sb, Object value, int depth) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            sb.append('"').append(s.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value);
        } else if (value instanceof Map<?, ?> m) {
            if (m.isEmpty()) { sb.append("{}"); return; }
            sb.append('{');
            int i = 0;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (i++ > 0) sb.append(',');
                sb.append('\n').append("  ".repeat(depth + 1));
                appendJson(sb, String.valueOf(e.getKey()), depth + 1);
                sb.append(": ");
                appendJson(sb, e.getValue(), depth + 1);
            }
            sb.append('\n').append("  ".repeat(depth)).append('}');
        } else if (value instanceof Iterable<?> iter) {
            sb.append('[');
            int i = 0;
            for (Object o : iter) {
                if (i++ > 0) sb.append(", ");
                appendJson(sb, o, depth + 1);
            }
            sb.append(']');
        } else {
            sb.append('"').append(value).append('"');
        }
    }

    private static String defaultExampleCorpus() {
        StringBuilder sb = new StringBuilder();
        String[] files = {
                "01_management_fee.ff", "02_performance_fee.ff", "03_capital_call.ff",
                "04_nav_calculation.ff", "05_equalization.ff", "06_european_waterfall.ff"
        };
        for (String name : files) {
            Path p = Path.of("examples", name);
            if (!Files.exists(p)) p = Path.of("..", "examples", name);
            if (!Files.exists(p)) continue;
            try {
                sb.append("## ").append(name).append("\n\n");
                sb.append("```fundflow\n").append(Files.readString(p)).append("```\n\n");
            } catch (IOException ignore) {
                /* skip missing files */
            }
        }
        return sb.toString();
    }
}
