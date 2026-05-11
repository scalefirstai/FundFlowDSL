package ai.getfundflow.dsl.ast;

import java.util.List;

public record WaterfallDecl(String name, List<WaterfallBody> body) implements TopLevelDecl {

    public WaterfallDecl {
        body = List.copyOf(body);
    }

    public sealed interface WaterfallBody permits LetBinding, Statement {}
}
