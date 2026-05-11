package ai.getfundflow.dsl.ast;

import java.util.List;

public record NounPhrase(List<NounAtom> atoms) {

    public NounPhrase {
        if (atoms.isEmpty()) {
            throw new IllegalArgumentException("nounPhrase needs at least one atom");
        }
        atoms = List.copyOf(atoms);
    }

    public sealed interface NounAtom permits NounAtom.Ident, NounAtom.Quoted, NounAtom.Number {

        record Ident(String text) implements NounAtom {}

        record Quoted(String value) implements NounAtom {}

        record Number(String text) implements NounAtom {}
    }
}
