package guru.interlis.transformer.expr;

public sealed interface Expression permits LiteralExpr, PathExpr, FunctionCallExpr, ConditionalExpr {}
