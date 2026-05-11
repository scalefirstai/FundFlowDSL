package ai.getfundflow.dsl.agent;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import java.util.ArrayList;
import java.util.List;

/**
 * Live {@link Agent} backed by Anthropic's Claude API (claude-opus-4-7).
 *
 * <p>Notable defaults, per the project's claude-api skill:
 * <ul>
 *   <li>Model: {@code claude-opus-4-7}</li>
 *   <li>Adaptive thinking on (the only on-mode for 4.7)</li>
 *   <li>Prompt caching on the system prompt (5-minute ephemeral TTL)</li>
 *   <li>Generous {@code max_tokens} (4096 — a single rule is well under 1k tokens of output)</li>
 * </ul>
 */
public final class AnthropicAgent implements Agent {

    private static final String MODEL = "claude-opus-4-7";
    private static final long MAX_TOKENS = 4096L;

    private final AnthropicClient client;
    private final String systemPrompt;

    public AnthropicAgent(String apiKey, String systemPrompt) {
        this.client = AnthropicOkHttpClient.builder().apiKey(apiKey).build();
        this.systemPrompt = systemPrompt;
    }

    /** Visible for tests: inject a pre-built client (e.g. with a mocked transport). */
    AnthropicAgent(AnthropicClient client, String systemPrompt) {
        this.client = client;
        this.systemPrompt = systemPrompt;
    }

    @Override
    public String generate(List<Turn> history) {
        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(MODEL)
                .maxTokens(MAX_TOKENS)
                .thinking(ThinkingConfigAdaptive.builder().build())
                .systemOfTextBlockParams(List.of(
                        TextBlockParam.builder()
                                .text(systemPrompt)
                                .cacheControl(CacheControlEphemeral.builder().build())
                                .build()));

        for (Turn turn : history) {
            switch (turn.role()) {
                case USER -> params.addUserMessage(turn.content());
                case ASSISTANT -> params.addMessage(MessageParam.builder()
                        .role(MessageParam.Role.ASSISTANT)
                        .content(turn.content())
                        .build());
            }
        }

        Message response = client.messages().create(params.build());
        StringBuilder out = new StringBuilder();
        for (var block : response.content()) {
            block.text().ifPresent(t -> out.append(t.text()));
        }
        return out.toString();
    }

    /** Extracts the first ```fundflow … ``` fenced block; falls back to raw text. */
    public static String extractProgram(String response) {
        if (response == null || response.isEmpty()) return "";
        // Look for ```fundflow first, then any ``` fence
        int fenceStart = response.indexOf("```fundflow");
        int fenceLength = "```fundflow".length();
        if (fenceStart < 0) {
            fenceStart = response.indexOf("```ff");
            fenceLength = "```ff".length();
        }
        if (fenceStart < 0) {
            fenceStart = response.indexOf("```");
            fenceLength = 3;
        }
        if (fenceStart < 0) return response.trim();

        int contentStart = fenceStart + fenceLength;
        // Skip optional language tag on first line
        int newline = response.indexOf('\n', contentStart);
        if (newline > 0) contentStart = newline + 1;

        int fenceEnd = response.indexOf("```", contentStart);
        if (fenceEnd < 0) return response.substring(contentStart).trim();
        return response.substring(contentStart, fenceEnd).trim() + "\n";
    }

    /**
     * Renders a validation report as a user message the loop can feed back.
     * Lists every error with code + message + location; truncates large reports
     * to keep the conversation tractable.
     */
    public static String renderDiagnosticsForFeedback(Validator.ValidationReport report) {
        List<String> lines = new ArrayList<>();
        lines.add("Your previous attempt failed validation. Fix every error below and try again.");
        lines.add("");
        if (!report.parse().isEmpty()) {
            lines.add("# Parse errors");
            report.parse().forEach(d -> lines.add(formatDiagnostic(d)));
            lines.add("");
        }
        if (report.types().stream().anyMatch(d ->
                d.severity() == ai.getfundflow.dsl.semantic.Severity.ERROR)) {
            lines.add("# Type / symbol errors");
            report.types().stream()
                    .filter(d -> d.severity() == ai.getfundflow.dsl.semantic.Severity.ERROR)
                    .forEach(d -> lines.add(formatDiagnostic(d)));
            lines.add("");
        }
        if (report.eval().stream().anyMatch(d ->
                d.severity() == ai.getfundflow.dsl.semantic.Severity.ERROR)) {
            lines.add("# Effectivity / runtime errors");
            report.eval().stream()
                    .filter(d -> d.severity() == ai.getfundflow.dsl.semantic.Severity.ERROR)
                    .forEach(d -> lines.add(formatDiagnostic(d)));
            lines.add("");
        }
        lines.add("Generate a corrected program. Output ONLY the .ff source in a fenced "
                + "```fundflow block.");
        return String.join("\n", lines);
    }

    private static String formatDiagnostic(ai.getfundflow.dsl.semantic.Diagnostic d) {
        StringBuilder sb = new StringBuilder();
        sb.append("- ").append(d.code().code()).append(" at line ")
                .append(d.location().line()).append(": ").append(d.message());
        d.hint().ifPresent(h -> sb.append("  (hint: ").append(h).append(")"));
        return sb.toString();
    }
}
