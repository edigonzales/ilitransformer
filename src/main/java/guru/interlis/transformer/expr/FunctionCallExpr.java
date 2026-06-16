package guru.interlis.transformer.expr;

import java.util.List;

public record FunctionCallExpr(String functionName, List<Expression> arguments) implements Expression {
    public FunctionCallExpr {
        if (functionName == null || functionName.isBlank())
            throw new IllegalArgumentException("functionName must not be blank");
        if (arguments == null) arguments = List.of();
    }
}
