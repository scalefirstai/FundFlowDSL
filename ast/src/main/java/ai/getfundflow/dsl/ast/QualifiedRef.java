package ai.getfundflow.dsl.ast;

import java.util.List;

public record QualifiedRef(List<NounPhrase> phrases) {
    public QualifiedRef {
        if (phrases.isEmpty()) {
            throw new IllegalArgumentException("qualifiedRef needs at least one noun phrase");
        }
        phrases = List.copyOf(phrases);
    }
}
