package ai.getfundflow.dsl.agent;

import java.util.ArrayList;
import java.util.List;

/**
 * Generate → validate → retry loop (spec §14.1, §14.3).
 *
 * <p>Caps attempts at a configurable maximum (default 3, matching the spec's
 * "approves on attempt ≤ 3" acceptance criterion). On each iteration:
 *
 * <ol>
 *   <li>Ask the agent to generate a candidate program given the conversation so far</li>
 *   <li>Extract the .ff program from the response (fenced code block)</li>
 *   <li>Validate (parse + semantic)</li>
 *   <li>If clean → return the program and the validation report</li>
 *   <li>Else → append the diagnostics as a user turn and loop</li>
 * </ol>
 */
public final class AgentLoop {

    public static final int DEFAULT_MAX_ATTEMPTS = 3;

    private final Agent agent;
    private final Validator validator;
    private final int maxAttempts;

    public AgentLoop(Agent agent, Validator validator) {
        this(agent, validator, DEFAULT_MAX_ATTEMPTS);
    }

    public AgentLoop(Agent agent, Validator validator, int maxAttempts) {
        if (maxAttempts < 1) throw new IllegalArgumentException("maxAttempts must be >= 1");
        this.agent = agent;
        this.validator = validator;
        this.maxAttempts = maxAttempts;
    }

    public AgentResult run(String userPrompt) {
        List<Agent.Turn> history = new ArrayList<>();
        history.add(new Agent.Turn(Agent.Role.USER, userPrompt));
        List<Attempt> attempts = new ArrayList<>();

        for (int i = 1; i <= maxAttempts; i++) {
            String rawResponse = agent.generate(history);
            String program = AnthropicAgent.extractProgram(rawResponse);
            Validator.ValidationReport report = validator.validate(program);
            attempts.add(new Attempt(i, rawResponse, program, report));

            if (report.ok()) {
                return new AgentResult(true, i, program, report, attempts);
            }
            // Feed the failed attempt + diagnostics back into the conversation.
            history.add(new Agent.Turn(Agent.Role.ASSISTANT, rawResponse));
            history.add(new Agent.Turn(Agent.Role.USER,
                    AnthropicAgent.renderDiagnosticsForFeedback(report)));
        }

        Attempt last = attempts.get(attempts.size() - 1);
        return new AgentResult(false, maxAttempts, last.program(), last.report(), attempts);
    }

    public record AgentResult(
            boolean accepted,
            int attemptsUsed,
            String program,
            Validator.ValidationReport finalReport,
            List<Attempt> attempts) {
        public AgentResult {
            attempts = List.copyOf(attempts);
        }
    }

    public record Attempt(int number, String rawResponse, String program, Validator.ValidationReport report) {}
}
