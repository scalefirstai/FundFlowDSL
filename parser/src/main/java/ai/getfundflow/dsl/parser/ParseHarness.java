package ai.getfundflow.dsl.parser;

import ai.getfundflow.dsl.parser.gen.FundFlowLexer;
import ai.getfundflow.dsl.parser.gen.FundFlowParser;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.antlr.v4.runtime.tree.ParseTree;

public final class ParseHarness {

    private ParseHarness() {}

    public static ParseTree parse(String source) {
        ThrowingErrorListener listener = new ThrowingErrorListener();

        FundFlowLexer lexer = new FundFlowLexer(CharStreams.fromString(source));
        lexer.removeErrorListeners();
        lexer.addErrorListener(listener);

        CommonTokenStream tokens = new CommonTokenStream(lexer);

        FundFlowParser parser = new FundFlowParser(tokens);
        parser.removeErrorListeners();
        parser.addErrorListener(listener);
        parser.setErrorHandler(new BailErrorStrategy());

        return parser.program();
    }

    private static final class ThrowingErrorListener extends BaseErrorListener {
        @Override
        public void syntaxError(
                Recognizer<?, ?> recognizer,
                Object offendingSymbol,
                int line,
                int charPositionInLine,
                String msg,
                RecognitionException e) {
            throw new ParseException(line, charPositionInLine, msg);
        }
    }
}
