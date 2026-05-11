package ai.getfundflow.dsl.lsp;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.eclipse.lsp4j.MessageActionItem;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.ShowMessageRequestParams;
import org.eclipse.lsp4j.services.LanguageClient;

/** Test stand-in that captures publishDiagnostics invocations. */
final class MockLanguageClient implements LanguageClient {

    final List<PublishDiagnosticsParams> published = new ArrayList<>();
    final List<MessageParams> logged = new ArrayList<>();

    @Override
    public void telemetryEvent(Object o) {}

    @Override
    public void publishDiagnostics(PublishDiagnosticsParams diagnostics) {
        published.add(diagnostics);
    }

    @Override
    public void showMessage(MessageParams messageParams) {}

    @Override
    public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams requestParams) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public void logMessage(MessageParams message) {
        logged.add(message);
    }
}
