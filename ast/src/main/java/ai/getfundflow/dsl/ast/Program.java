package ai.getfundflow.dsl.ast;

import java.util.List;
import java.util.Optional;

public record Program(
        Optional<ModuleDecl> module,
        List<ImportDecl> imports,
        List<TopLevelDecl> declarations) {

    public Program {
        imports = List.copyOf(imports);
        declarations = List.copyOf(declarations);
    }
}
