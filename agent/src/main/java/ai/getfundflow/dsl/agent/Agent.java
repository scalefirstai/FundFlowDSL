package ai.getfundflow.dsl.agent;

import java.util.List;

/**
 * Single-method contract the agent loop drives. Implementations include
 * {@link AnthropicAgent} (live API call) and test mocks.
 */
public interface Agent {

    /**
     * Generate a candidate {@code .ff} program given the conversation so far.
     *
     * @param history accumulated turns; the loop appends a user turn carrying the
     *                request (plus, on retries, the prior diagnostics)
     */
    String generate(List<Turn> history);

    enum Role { USER, ASSISTANT }

    record Turn(Role role, String content) {}
}
