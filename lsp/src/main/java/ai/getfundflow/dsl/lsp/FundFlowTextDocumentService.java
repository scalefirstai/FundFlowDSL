package ai.getfundflow.dsl.lsp;

import ai.getfundflow.dsl.ast.AppliesToClause;
import ai.getfundflow.dsl.ast.DescriptionClause;
import ai.getfundflow.dsl.ast.EffectiveClause;
import ai.getfundflow.dsl.ast.LetBinding;
import ai.getfundflow.dsl.ast.NameRef;
import ai.getfundflow.dsl.ast.NounPhrase.NounAtom;
import ai.getfundflow.dsl.ast.PolicyDecl;
import ai.getfundflow.dsl.ast.Program;
import ai.getfundflow.dsl.ast.RuleClause;
import ai.getfundflow.dsl.ast.RuleDecl;
import ai.getfundflow.dsl.ast.ScheduleDecl;
import ai.getfundflow.dsl.ast.TopLevelDecl;
import ai.getfundflow.dsl.ast.WaterfallDecl;
import ai.getfundflow.dsl.diagnostics.Formatter;
import ai.getfundflow.dsl.semantic.SourceLocation;
import ai.getfundflow.dsl.semantic.SourceMap;
import ai.getfundflow.dsl.semantic.Symbol;
import ai.getfundflow.dsl.stdlib.FunctionRegistry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DidSaveTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.MarkupContent;
import org.eclipse.lsp4j.MarkupKind;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.Range;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.TextDocumentService;

public final class FundFlowTextDocumentService implements TextDocumentService {

    private static final List<String> KEYWORDS = List.of(
            "rule", "schedule", "waterfall", "policy", "type", "extension", "extends", "field",
            "module", "import",
            "description", "applies", "to", "effective", "from", "inception",
            "let", "when", "then", "else", "if",
            "accrue", "allocate", "distribute", "post", "publish",
            "on", "using", "over", "by", "across", "at", "of", "as",
            "start", "end", "equally", "with", "narrative", "through",
            "per", "annum", "and", "or", "not",
            "sum", "weighted", "average");

    private final DocumentStore store;
    private LanguageClient client;

    public FundFlowTextDocumentService(DocumentStore store) {
        this.store = store;
    }

    public void connect(LanguageClient client) {
        this.client = client;
    }

    // -------------------------------------------------------------------------
    // Document lifecycle
    // -------------------------------------------------------------------------

    @Override
    public void didOpen(DidOpenTextDocumentParams params) {
        analyzeAndPublish(params.getTextDocument().getUri(), params.getTextDocument().getText());
    }

    @Override
    public void didChange(DidChangeTextDocumentParams params) {
        if (params.getContentChanges().isEmpty()) return;
        // Server is configured for full-document sync, so the first change carries
        // the entire new text.
        String full = params.getContentChanges().get(0).getText();
        analyzeAndPublish(params.getTextDocument().getUri(), full);
    }

    @Override
    public void didSave(DidSaveTextDocumentParams params) {
        // No special handling — analysis already runs on every change.
    }

    @Override
    public void didClose(DidCloseTextDocumentParams params) {
        String uri = params.getTextDocument().getUri();
        store.remove(uri);
        if (client != null) {
            client.publishDiagnostics(new PublishDiagnosticsParams(uri, List.of()));
        }
    }

    private void analyzeAndPublish(String uri, String source) {
        DocumentState state = store.analyze(uri, source);
        if (client != null) {
            client.publishDiagnostics(new PublishDiagnosticsParams(
                    uri, LspDiagnostics.toLsp(state.semanticResult().diagnostics().all())));
        }
    }

    // -------------------------------------------------------------------------
    // Formatting
    // -------------------------------------------------------------------------

    @Override
    public CompletableFuture<List<? extends TextEdit>> formatting(DocumentFormattingParams params) {
        return CompletableFuture.supplyAsync(() -> {
            Optional<DocumentState> maybe = store.get(params.getTextDocument().getUri());
            if (maybe.isEmpty() || maybe.get().program() == null) return List.of();
            String formatted = Formatter.format(maybe.get().program());
            TextEdit edit = new TextEdit(fullDocumentRange(maybe.get().source()), formatted);
            return List.of(edit);
        });
    }

    private Range fullDocumentRange(String source) {
        int lastLine = 0;
        int lastChar = 0;
        for (int i = 0; i < source.length(); i++) {
            if (source.charAt(i) == '\n') {
                lastLine++;
                lastChar = 0;
            } else {
                lastChar++;
            }
        }
        return new Range(new Position(0, 0), new Position(lastLine, lastChar));
    }

    // -------------------------------------------------------------------------
    // Hover
    // -------------------------------------------------------------------------

    @Override
    public CompletableFuture<Hover> hover(HoverParams params) {
        return CompletableFuture.supplyAsync(() -> {
            DocumentState state = store.get(params.getTextDocument().getUri()).orElse(null);
            if (state == null || state.program() == null) return null;
            String word = wordAt(state.source(), params.getPosition());
            if (word == null) return null;

            // Function in the registry?
            Optional<FunctionRegistry.Signature> sig = FunctionRegistry.lookup(word);
            if (sig.isPresent()) {
                FunctionRegistry.Signature s = sig.get();
                String body = "**" + s.name() + "** — " + s.category().name().toLowerCase()
                        + "\n\n" + s.summary()
                        + "\n\narity: " + arityRange(s);
                return new Hover(new MarkupContent(MarkupKind.MARKDOWN, body));
            }

            // Let-binding in any rule of the current document?
            String body = findBindingType(state, word);
            if (body != null) {
                return new Hover(new MarkupContent(MarkupKind.MARKDOWN, body));
            }
            return null;
        });
    }

    private String findBindingType(DocumentState state, String name) {
        if (state.semanticResult() == null || state.semanticResult().symbols() == null) return null;
        for (TopLevelDecl d : state.program().declarations()) {
            String owner = ownerName(d);
            if (owner == null) continue;
            Symbol.BindingSymbol binding =
                    state.semanticResult().symbols().bindingsFor(owner).get(name);
            if (binding != null) {
                return "**" + name + "**: " + binding.type().describe()
                        + "\n\n_let-bound in_ `" + owner + "`";
            }
        }
        return null;
    }

    private String ownerName(TopLevelDecl d) {
        return switch (d) {
            case RuleDecl r -> r.name();
            case ScheduleDecl s -> s.name();
            case WaterfallDecl w -> w.name();
            case PolicyDecl p -> p.name();
            default -> null;
        };
    }

    private String arityRange(FunctionRegistry.Signature sig) {
        if (sig.minArity() == sig.maxArity()) return String.valueOf(sig.minArity());
        if (sig.maxArity() == Integer.MAX_VALUE) return sig.minArity() + "+";
        return sig.minArity() + ".." + sig.maxArity();
    }

    // -------------------------------------------------------------------------
    // Completion
    // -------------------------------------------------------------------------

    @Override
    public CompletableFuture<Either<List<CompletionItem>, CompletionList>> completion(CompletionParams params) {
        return CompletableFuture.supplyAsync(() -> {
            List<CompletionItem> items = new ArrayList<>();
            for (String kw : KEYWORDS) {
                CompletionItem item = new CompletionItem(kw);
                item.setKind(CompletionItemKind.Keyword);
                items.add(item);
            }
            for (String fn : FunctionRegistry.names()) {
                CompletionItem item = new CompletionItem(fn);
                item.setKind(CompletionItemKind.Function);
                FunctionRegistry.lookup(fn).ifPresent(sig -> item.setDetail(sig.summary()));
                items.add(item);
            }
            Optional<DocumentState> maybe = store.get(params.getTextDocument().getUri());
            if (maybe.isPresent() && maybe.get().program() != null
                    && maybe.get().semanticResult() != null
                    && maybe.get().semanticResult().symbols() != null) {
                Program program = maybe.get().program();
                Symbol.BindingSymbol[] allBindings = collectBindings(program, maybe.get());
                for (Symbol.BindingSymbol b : allBindings) {
                    CompletionItem item = new CompletionItem(b.name());
                    item.setKind(CompletionItemKind.Variable);
                    item.setDetail(b.type().describe());
                    items.add(item);
                }
            }
            return Either.forLeft(items);
        });
    }

    private Symbol.BindingSymbol[] collectBindings(Program program, DocumentState state) {
        List<Symbol.BindingSymbol> all = new ArrayList<>();
        for (TopLevelDecl d : program.declarations()) {
            String owner = ownerName(d);
            if (owner == null) continue;
            all.addAll(state.semanticResult().symbols().bindingsFor(owner).values());
        }
        return all.toArray(new Symbol.BindingSymbol[0]);
    }

    // -------------------------------------------------------------------------
    // Definition (go-to)
    // -------------------------------------------------------------------------

    @Override
    public CompletableFuture<Either<List<? extends Location>, List<? extends LocationLink>>>
            definition(DefinitionParams params) {
        return CompletableFuture.supplyAsync(() -> {
            DocumentState state = store.get(params.getTextDocument().getUri()).orElse(null);
            if (state == null || state.program() == null) return Either.forLeft(List.of());
            String word = wordAt(state.source(), params.getPosition());
            if (word == null) return Either.forLeft(List.of());

            for (TopLevelDecl d : state.program().declarations()) {
                String owner = ownerName(d);
                if (owner == null) continue;
                LetBinding match = findLet(d, word);
                if (match != null) {
                    SourceLocation loc = state.sourceMap().locationOf(match);
                    return Either.forLeft(Collections.singletonList(
                            new Location(state.uri(), Positions.toRange(loc))));
                }
            }
            // Top-level decl name? jump to that rule/schedule/etc.
            for (TopLevelDecl d : state.program().declarations()) {
                if (ownerName(d) != null && ownerName(d).equals(word)) {
                    SourceLocation loc = state.sourceMap().locationOf(d);
                    return Either.forLeft(Collections.singletonList(
                            new Location(state.uri(), Positions.toRange(loc))));
                }
            }
            return Either.forLeft(List.of());
        });
    }

    private LetBinding findLet(TopLevelDecl d, String name) {
        Iterable<RuleClause> clauses = switch (d) {
            case RuleDecl r -> r.clauses();
            case ScheduleDecl s -> s.clauses();
            case PolicyDecl p -> p.clauses();
            default -> null;
        };
        if (clauses == null) return null;
        for (RuleClause c : clauses) {
            if (c instanceof LetBinding lb && lb.name().equals(name)) return lb;
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Pulls the identifier at the cursor position out of the raw source. */
    static String wordAt(String source, Position position) {
        int offset = offsetOf(source, position);
        if (offset < 0) return null;
        int start = offset;
        int end = offset;
        while (start > 0 && isWordChar(source.charAt(start - 1))) start--;
        while (end < source.length() && isWordChar(source.charAt(end))) end++;
        if (start == end) return null;
        return source.substring(start, end);
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static int offsetOf(String source, Position position) {
        int line = 0;
        int col = 0;
        for (int i = 0; i < source.length(); i++) {
            if (line == position.getLine() && col == position.getCharacter()) return i;
            if (source.charAt(i) == '\n') {
                line++;
                col = 0;
            } else {
                col++;
            }
        }
        return line == position.getLine() && col == position.getCharacter() ? source.length() : -1;
    }

    @SuppressWarnings("unused")
    private static List<String> unusedAccessHack() {
        return Arrays.asList("keep", "imports", "alive");
    }
}
