package ai.getfundflow.dsl.lsp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.ExecutionException;
import org.eclipse.lsp4j.CompletionItem;
import org.eclipse.lsp4j.CompletionItemKind;
import org.eclipse.lsp4j.CompletionList;
import org.eclipse.lsp4j.CompletionParams;
import org.eclipse.lsp4j.DefinitionParams;
import org.eclipse.lsp4j.Diagnostic;
import org.eclipse.lsp4j.DiagnosticSeverity;
import org.eclipse.lsp4j.DidChangeTextDocumentParams;
import org.eclipse.lsp4j.DidCloseTextDocumentParams;
import org.eclipse.lsp4j.DidOpenTextDocumentParams;
import org.eclipse.lsp4j.DocumentFormattingParams;
import org.eclipse.lsp4j.FormattingOptions;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.HoverParams;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.Location;
import org.eclipse.lsp4j.LocationLink;
import org.eclipse.lsp4j.Position;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.TextDocumentContentChangeEvent;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentItem;
import org.eclipse.lsp4j.TextEdit;
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier;
import org.eclipse.lsp4j.jsonrpc.messages.Either;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LspServerTest {

    private FundFlowLanguageServer server;
    private MockLanguageClient client;

    @BeforeEach
    void setUp() {
        server = new FundFlowLanguageServer();
        client = new MockLanguageClient();
        server.connect(client);
    }

    private void open(String uri, String source) {
        TextDocumentItem item = new TextDocumentItem(uri, "fundflow", 1, source);
        server.getTextDocumentService().didOpen(new DidOpenTextDocumentParams(item));
    }

    // ---- initialize -------------------------------------------------------

    @Test
    void initializeAdvertisesExpectedCapabilities() throws Exception {
        InitializeResult result = server.initialize(new InitializeParams()).get();
        assertThat(result.getCapabilities().getTextDocumentSync().getLeft())
                .isEqualTo(org.eclipse.lsp4j.TextDocumentSyncKind.Full);
        assertThat(result.getCapabilities().getDocumentFormattingProvider().getLeft()).isTrue();
        assertThat(result.getCapabilities().getHoverProvider().getLeft()).isTrue();
        assertThat(result.getCapabilities().getDefinitionProvider().getLeft()).isTrue();
        assertThat(result.getCapabilities().getCompletionProvider()).isNotNull();
    }

    // ---- diagnostics on open/change ---------------------------------------

    @Test
    void didOpenPublishesDiagnosticsForBadProgram() {
        open("file:///bad.ff", """
                rule "Bad" {
                  let x = unknown_one
                }
                """);
        assertThat(client.published).hasSize(1);
        PublishDiagnosticsParams pub = client.published.get(0);
        assertThat(pub.getUri()).isEqualTo("file:///bad.ff");
        assertThat(pub.getDiagnostics()).isNotEmpty();
        Diagnostic d = pub.getDiagnostics().get(0);
        assertThat(d.getCode().getLeft()).isEqualTo("FF2002");
        assertThat(d.getSeverity()).isEqualTo(DiagnosticSeverity.Error);
        assertThat(d.getSource()).isEqualTo("fundflow");
    }

    @Test
    void didChangeRepublishesDiagnostics() {
        open("file:///x.ff", "rule \"X\" {\n  let a = unknown\n}\n");
        client.published.clear();
        server.getTextDocumentService().didChange(new DidChangeTextDocumentParams(
                new VersionedTextDocumentIdentifier("file:///x.ff", 2),
                List.of(new TextDocumentContentChangeEvent("rule \"X\" {\n  let a = USD 100\n}\n"))));
        assertThat(client.published).hasSize(1);
        assertThat(client.published.get(0).getDiagnostics()).isEmpty();
    }

    @Test
    void didCloseClearsDiagnostics() {
        open("file:///x.ff", "rule \"X\" {\n  let a = bad_ref\n}\n");
        client.published.clear();
        server.getTextDocumentService().didClose(
                new DidCloseTextDocumentParams(new TextDocumentIdentifier("file:///x.ff")));
        assertThat(client.published).hasSize(1);
        assertThat(client.published.get(0).getDiagnostics()).isEmpty();
    }

    // ---- formatting -------------------------------------------------------

    @Test
    void formattingReturnsCanonicalEdit() throws Exception {
        open("file:///fmt.ff", "rule    \"Sloppy\"   {  description: \"x\"   }");
        DocumentFormattingParams params = new DocumentFormattingParams();
        params.setTextDocument(new TextDocumentIdentifier("file:///fmt.ff"));
        params.setOptions(new FormattingOptions(2, true));
        List<? extends TextEdit> edits =
                server.getTextDocumentService().formatting(params).get();
        assertThat(edits).hasSize(1);
        String formatted = edits.get(0).getNewText();
        assertThat(formatted).contains("rule \"Sloppy\" {");
        assertThat(formatted).endsWith("\n");
    }

    // ---- hover ------------------------------------------------------------

    @Test
    void hoverOnLetBindingShowsType() throws Exception {
        open("file:///h.ff", """
                rule "Hover" {
                  let total_fee = USD 100
                  let other = total_fee
                }
                """);
        HoverParams params = new HoverParams(
                new TextDocumentIdentifier("file:///h.ff"),
                // cursor in the middle of `total_fee` on line 3 (0-based: 2)
                new Position(2, 16));
        Hover hover = server.getTextDocumentService().hover(params).get();
        assertThat(hover).isNotNull();
        assertThat(hover.getContents().getRight().getValue()).contains("Money(USD)");
    }

    @Test
    void hoverOnRegisteredFunctionShowsSignature() throws Exception {
        open("file:///fn.ff", "rule \"F\" {\n  let r = abs(1)\n}\n");
        HoverParams params = new HoverParams(
                new TextDocumentIdentifier("file:///fn.ff"),
                // cursor on `abs` (line 1, char 10)
                new Position(1, 10));
        Hover hover = server.getTextDocumentService().hover(params).get();
        assertThat(hover).isNotNull();
        String body = hover.getContents().getRight().getValue();
        assertThat(body).contains("abs");
        assertThat(body).contains("math");
    }

    // ---- completion -------------------------------------------------------

    @Test
    void completionIncludesKeywordsFunctionsAndBindings() throws Exception {
        open("file:///c.ff", "rule \"C\" {\n  let alpha = USD 100\n  let beta = alpha\n}\n");
        CompletionParams params = new CompletionParams(
                new TextDocumentIdentifier("file:///c.ff"), new Position(2, 13));
        Either<List<CompletionItem>, CompletionList> result =
                server.getTextDocumentService().completion(params).get();
        List<CompletionItem> items = result.getLeft();

        assertThat(items).extracting(CompletionItem::getLabel)
                .contains("rule", "let", "applies", "effective");
        assertThat(items).extracting(CompletionItem::getLabel)
                .contains("abs", "round", "max", "min", "npv");
        assertThat(items).extracting(CompletionItem::getLabel).contains("alpha", "beta");

        // verify kinds
        CompletionItem ruleItem = items.stream().filter(i -> "rule".equals(i.getLabel())).findFirst().orElseThrow();
        assertThat(ruleItem.getKind()).isEqualTo(CompletionItemKind.Keyword);
        CompletionItem absItem = items.stream().filter(i -> "abs".equals(i.getLabel())).findFirst().orElseThrow();
        assertThat(absItem.getKind()).isEqualTo(CompletionItemKind.Function);
        CompletionItem alphaItem = items.stream().filter(i -> "alpha".equals(i.getLabel())).findFirst().orElseThrow();
        assertThat(alphaItem.getKind()).isEqualTo(CompletionItemKind.Variable);
    }

    // ---- definition -------------------------------------------------------

    @Test
    void definitionJumpsToLetBinding() throws Exception {
        String src = """
                rule "Jump" {
                  let total_fee = USD 100
                  let other = total_fee
                }
                """;
        open("file:///def.ff", src);
        // cursor on `total_fee` reference (line 3, char 16)
        DefinitionParams params = new DefinitionParams(
                new TextDocumentIdentifier("file:///def.ff"), new Position(2, 16));
        Either<List<? extends Location>, List<? extends LocationLink>> result =
                server.getTextDocumentService().definition(params).get();
        List<? extends Location> locs = result.getLeft();
        assertThat(locs).hasSize(1);
        assertThat(locs.get(0).getUri()).isEqualTo("file:///def.ff");
        // The let binding is on the 2nd source line (0-based: 1)
        assertThat(locs.get(0).getRange().getStart().getLine()).isEqualTo(1);
    }

    @Test
    void definitionFindsRuleByName() throws Exception {
        String src = """
                rule "Anchor" {
                  description: "..."
                }
                """;
        open("file:///r.ff", src);
        // Cursor on `Anchor` inside the rule's quoted name — wordAt extracts "Anchor"
        // and definition resolves to this rule itself.
        Either<List<? extends Location>, List<? extends LocationLink>> result =
                server.getTextDocumentService().definition(new DefinitionParams(
                        new TextDocumentIdentifier("file:///r.ff"), new Position(0, 7))).get();
        assertThat(result.getLeft()).hasSize(1);
        assertThat(result.getLeft().get(0).getRange().getStart().getLine()).isEqualTo(0);
    }
}
