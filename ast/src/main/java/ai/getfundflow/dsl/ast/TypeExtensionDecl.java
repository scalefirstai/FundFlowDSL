package ai.getfundflow.dsl.ast;

import java.util.List;

public record TypeExtensionDecl(String typeName, String baseType, List<FieldDecl> fields)
        implements TopLevelDecl {

    public TypeExtensionDecl {
        fields = List.copyOf(fields);
    }
}
