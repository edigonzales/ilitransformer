package guru.interlis.transformer.expr;

public record PathExpr(String alias, String attributeName) implements Expression {
    public PathExpr {
        if (alias == null || alias.isBlank()) throw new IllegalArgumentException("alias must not be blank");
    }
}
