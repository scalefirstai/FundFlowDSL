package ai.getfundflow.dsl.runtime;

import ai.getfundflow.dsl.core.types.Money;
import java.time.LocalDate;
import java.util.Optional;

public record LedgerEntry(
        LocalDate date,
        String account,
        Money amount,
        Optional<String> narrative,
        String sourceRule) {}
