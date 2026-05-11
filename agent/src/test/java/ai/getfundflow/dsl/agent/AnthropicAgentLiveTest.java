package ai.getfundflow.dsl.agent;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Hits the real Anthropic API. Disabled by default — enable by exporting
 * {@code FUNDFLOW_LIVE_TESTS=1}. The API key is read from {@code .env} (or the
 * environment) via {@link EnvLoader}.
 *
 * <p>Spec §14.3 acceptance: "natural-language prompt → agent generates → validator
 * approves on attempt ≤ 3 for each canonical example." We do one of these here;
 * additional cases can be added once the live-test cost profile is acceptable.
 */
class AnthropicAgentLiveTest {

    @Test
    @EnabledIfEnvironmentVariable(named = "FUNDFLOW_LIVE_TESTS", matches = "1")
    void liveAgentGeneratesAValidProgramForADailyMgmtFeePrompt() {
        EnvLoader env = EnvLoader.load(Path.of(".env"));
        if (env.anthropicApiKey().isEmpty()) {
            env = EnvLoader.load(Path.of("..", ".env"));
        }
        String apiKey = env.anthropicApiKey()
                .orElseThrow(() -> new IllegalStateException(
                        "live test requires ANTHROPIC_API_KEY or ANTROPIC_KEY in env or .env"));

        Catalog catalog = Catalog.build(
                new ai.getfundflow.dsl.semantic.SymbolTable(),
                new ai.getfundflow.dsl.semantic.TypeRegistry());
        String systemPrompt = new SystemPrompt(catalog).render();
        AnthropicAgent agent = new AnthropicAgent(apiKey, systemPrompt);
        AgentLoop loop = new AgentLoop(agent, new Validator());

        AgentLoop.AgentResult result = loop.run(
                "Generate a rule named \"Daily Mgmt Fee Accrual\" that:\n"
                        + " - applies to fund \"Acme Master Fund\"\n"
                        + " - accrues 1.5% per annum on opening_nav using actual/365\n"
                        + " - posts to ledger account \"Management Fee Payable\" with narrative "
                        + "\"Daily mgmt fee accrual\"\n"
                        + " - effective from 2026-01-01");

        assertThat(result.accepted())
                .as("agent should converge within %d attempts. Final program:%n%s%nFinal errors:%n%s",
                        AgentLoop.DEFAULT_MAX_ATTEMPTS,
                        result.program(),
                        result.finalReport().errors())
                .isTrue();
        assertThat(result.attemptsUsed()).isLessThanOrEqualTo(AgentLoop.DEFAULT_MAX_ATTEMPTS);
        assertThat(result.program()).contains("Daily Mgmt Fee Accrual");
    }
}
