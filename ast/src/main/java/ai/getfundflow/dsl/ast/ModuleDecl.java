package ai.getfundflow.dsl.ast;

import java.util.List;

public record ModuleDecl(List<String> path) {
    public ModuleDecl {
        path = List.copyOf(path);
    }
}
