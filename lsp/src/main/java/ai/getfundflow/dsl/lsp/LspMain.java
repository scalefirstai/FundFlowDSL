package ai.getfundflow.dsl.lsp;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Future;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageClient;

/**
 * Stdio entry point. Invoked by the CLI (WP-10) via {@code fundflow lsp}.
 *
 * <p>Reads JSON-RPC messages on stdin, writes on stdout. Any other logging goes
 * to stderr to avoid corrupting the protocol stream.
 */
public final class LspMain {

    private LspMain() {}

    public static void main(String[] args) throws Exception {
        run(System.in, System.out);
    }

    /** Run the server until the client disconnects or {@code shutdown} is called. */
    public static int run(InputStream in, OutputStream out) throws Exception {
        FundFlowLanguageServer server = new FundFlowLanguageServer();
        Launcher<LanguageClient> launcher = Launcher.createLauncher(
                server, LanguageClient.class, in, out);
        server.connect(launcher.getRemoteProxy());
        Future<?> listening = launcher.startListening();
        listening.get();
        return server.exitCode();
    }
}
