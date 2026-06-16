package guru.interlis.transformer.expr;

import java.util.List;
import java.util.function.Function;

@FunctionalInterface
public interface LazyFunctionImplementation {
    Value apply(List<Expression> unevaluatedArgs, Function<Expression, Value> evaluator, EvalContext ctx);
}
