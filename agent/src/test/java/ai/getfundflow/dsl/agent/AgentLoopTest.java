package ai.getfundflow.dsl.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.junit.jupiter.api.Test;

class AgentLoopTest {

    /** Deterministic stand-in for {@link AnthropicAgent} — yields a queued sequence of responses. */
    private static final class ScriptedAgent implements Agent {
        private final Deque<String> responses;
        int callCount = 0;

        ScriptedAgent(String... responses) {
            this.responses = new ArrayDeque<>(List.of(responses));
        }

        @Override
        public String generate(List<Turn> history) {
            callCount++;
            String next = responses.poll();
            if (next == null) throw new IllegalStateException("no more scripted responses");
            return next;
        }
    }

    @Test
    void cleanProgramAcceptedOnFirstAttempt() {
        ScriptedAgent agent = new ScriptedAgent("""
                ```fundflow
                rule "Clean" {
                  let x = USD 100
                }
                ```
                """);
        AgentLoop loop = new AgentLoop(agent, new Validator());
        AgentLoop.AgentResult r = loop.run("Generate a clean rule");

        assertThat(r.accepted()).isTrue();
        assertThat(r.attemptsUsed()).isEqualTo(1);
        assertThat(r.program()).contains("rule \"Clean\"");
        assertThat(agent.callCount).isEqualTo(1);
    }

    @Test
    void retriesOnValidationErrorAndAcceptsCorrectedProgram() {
        ScriptedAgent agent = new ScriptedAgent(
                // First attempt: references an unknown binding
                """
                ```fundflow
                rule "R" {
                  let x = unknown_thing
                }
                ```
                """,
                // Second attempt: corrected
                """
                ```fundflow
                rule "R" {
                  let x = USD 100
                }
                ```
                """);
        AgentLoop loop = new AgentLoop(agent, new Validator());
        AgentLoop.AgentResult r = loop.run("Generate a rule");

        assertThat(r.accepted()).isTrue();
        assertThat(r.attemptsUsed()).isEqualTo(2);
        assertThat(agent.callCount).isEqualTo(2);
        // First attempt is recorded in the attempts list
        assertThat(r.attempts()).hasSize(2);
        assertThat(r.attempts().get(0).report().ok()).isFalse();
        assertThat(r.attempts().get(1).report().ok()).isTrue();
    }

    @Test
    void givesUpAfterMaxAttempts() {
        ScriptedAgent agent = new ScriptedAgent(
                "```fundflow\nrule \"A\" { let x = unknown1 }\n```",
                "```fundflow\nrule \"A\" { let x = unknown2 }\n```",
                "```fundflow\nrule \"A\" { let x = unknown3 }\n```");
        AgentLoop loop = new AgentLoop(agent, new Validator(), 3);
        AgentLoop.AgentResult r = loop.run("...");
        assertThat(r.accepted()).isFalse();
        assertThat(r.attemptsUsed()).isEqualTo(3);
        assertThat(agent.callCount).isEqualTo(3);
    }

    @Test
    void retryConversationCarriesDiagnostics() {
        // Capture the conversation history given to each generate() call.
        Agent capturingAgent = new Agent() {
            int call = 0;
            @Override
            public String generate(List<Turn> history) {
                call++;
                if (call == 1) {
                    return "```fundflow\nrule \"R\" { let x = bogus_ref }\n```";
                }
                // On the retry, the history should include the diagnostic feedback
                String lastUserTurn = history.get(history.size() - 1).content();
                assertThat(lastUserTurn).contains("FF2002");
                assertThat(lastUserTurn).contains("bogus_ref");
                return "```fundflow\nrule \"R\" { let x = USD 100 }\n```";
            }
        };
        AgentLoop loop = new AgentLoop(capturingAgent, new Validator());
        AgentLoop.AgentResult r = loop.run("...");
        assertThat(r.accepted()).isTrue();
    }

    @Test
    void extractProgramHandlesFundflowFence() {
        String response = "Sure! Here you go:\n\n```fundflow\nrule \"X\" {}\n```\n\nLet me know.";
        String program = AnthropicAgent.extractProgram(response);
        assertThat(program).isEqualTo("rule \"X\" {}\n");
    }

    @Test
    void extractProgramHandlesBareFence() {
        String response = "```\nrule \"X\" {}\n```";
        String program = AnthropicAgent.extractProgram(response);
        assertThat(program).isEqualTo("rule \"X\" {}\n");
    }

    @Test
    void extractProgramFallsBackToRawText() {
        String response = "rule \"X\" {}";
        String program = AnthropicAgent.extractProgram(response);
        assertThat(program).isEqualTo("rule \"X\" {}");
    }

    @Test
    void diagnosticsFeedbackMessageListsEveryError() {
        Validator.ValidationReport report = new Validator().validate("""
                rule "R" {
                  let x = unknown_a
                  let y = unknown_b
                }
                """);
        String feedback = AnthropicAgent.renderDiagnosticsForFeedback(report);
        assertThat(feedback).contains("FF2002");
        assertThat(feedback).contains("unknown_a");
        assertThat(feedback).contains("unknown_b");
        assertThat(feedback).contains("Output ONLY the .ff source");
    }
}
