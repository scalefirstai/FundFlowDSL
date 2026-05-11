package ai.getfundflow.dsl.ast;

import java.util.List;

public record ImportDecl(List<String> path) {
    public ImportDecl {
        path = List.copyOf(path);
    }
}
