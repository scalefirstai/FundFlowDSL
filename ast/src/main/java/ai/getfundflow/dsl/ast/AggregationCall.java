package ai.getfundflow.dsl.ast;

import java.util.Optional;

public sealed interface AggregationCall extends Expression
        permits AggregationCall.SumOf, AggregationCall.WeightedAverage {

    record SumOf(Expression source, Optional<Expression> by) implements AggregationCall {}

    record WeightedAverage(Expression source, Expression weight) implements AggregationCall {}
}
