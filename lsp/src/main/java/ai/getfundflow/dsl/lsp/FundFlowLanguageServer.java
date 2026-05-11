package ai.getfundflow.dsl.lsp;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;

public final class FundFlowLanguageServer implements LanguageServer, LanguageClientAware {

    private final DocumentStore store = new DocumentStore();
    private final FundFlowTextDocumentService textService = new FundFlowTextDocumentService(store);
    private final FundFlowWorkspaceService workspaceService = new FundFlowWorkspaceService();
    private LanguageClient client;
    private volatile int exitCode = 1;

    @Override
    public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
        ServerCapabilities capabilities = new ServerCapabilities();
        capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);
        capabilities.setDocumentFormattingProvider(true);
        capabilities.setHoverProvider(true);
        capabilities.setDefinitionProvider(true);
        CompletionOptions completion = new CompletionOptions();
        completion.setResolveProvider(false);
        completion.setTriggerCharacters(List.of(" ", "(", "."));
        capabilities.setCompletionProvider(completion);
        return CompletableFuture.completedFuture(new InitializeResult(capabilities));
    }

    @Override
    public CompletableFuture<Object> shutdown() {
        exitCode = 0;
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void exit() {
        // Caller (LspMain) is responsible for terminating the JVM on stdio launch.
    }

    public int exitCode() {
        return exitCode;
    }

    @Override
    public TextDocumentService getTextDocumentService() {
        return textService;
    }

    @Override
    public WorkspaceService getWorkspaceService() {
        return workspaceService;
    }

    @Override
    public void connect(LanguageClient client) {
        this.client = client;
        textService.connect(client);
    }

    public LanguageClient client() {
        return client;
    }

    public DocumentStore documentStore() {
        return store;
    }
}
