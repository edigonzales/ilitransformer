package guru.interlis.transformer.expr;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.expr.builtins.BasicFunctions;
import guru.interlis.transformer.expr.builtins.DateFunctions;
import guru.interlis.transformer.expr.builtins.EnumFunctions;
import guru.interlis.transformer.expr.builtins.MathFunctions;
import guru.interlis.transformer.expr.builtins.RefFunctions;
import guru.interlis.transformer.expr.builtins.StringFunctions;
import guru.interlis.transformer.mapping.plan.TypeInfo;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ExpressionEngine {

    private final FunctionRegistry functionRegistry;

    public ExpressionEngine() {
        this.functionRegistry = new FunctionRegistry();
        registerBuiltins();
    }

    public ExpressionEngine(FunctionRegistry functionRegistry) {
        this.functionRegistry = functionRegistry;
    }

    private void registerBuiltins() {
        BasicFunctions.registerAll(functionRegistry);
        StringFunctions.registerAll(functionRegistry);
        DateFunctions.registerAll(functionRegistry);
        EnumFunctions.registerAll(functionRegistry);
        RefFunctions.registerAll(functionRegistry);
        MathFunctions.registerAll(functionRegistry);
    }

    public FunctionRegistry functionRegistry() {
        return functionRegistry;
    }

    public Value evaluate(String expression, Map<String, IomObject> sources) {
        EvalContext ctx = new EvalContext(sources, null, null);
        return evaluate(expression, ctx);
    }

    public Value evaluate(String expression, EvalContext ctx) {
        if (expression == null || expression.isBlank()) {
            return NullValue.INSTANCE;
        }
        String trimmed = expression.trim();

        // Legacy quick path: simple string literal
        if ((trimmed.startsWith("\"") && trimmed.endsWith("\""))
                || (trimmed.startsWith("'") && trimmed.endsWith("'"))) {
            return new TextValue(trimmed.substring(1, trimmed.length() - 1));
        }

        // Legacy quick path: simple path reference ${alias.attr}
        if (trimmed.startsWith("${") && trimmed.endsWith("}") && !trimmed.contains("(")) {
            String path = trimmed.substring(2, trimmed.length() - 1);
            return resolveSourcePath(path, ctx);
        }

        // Full parsing
        Expression ast;
        try {
            ast = ExpressionParser.parse(trimmed);
        } catch (ExpressionParseException e) {
            if (ctx.diagnostics() != null) {
                ctx.diagnostics().add(new Diagnostic(
                        DiagnosticCode.EXPR_SYNTAX, Severity.ERROR,
                        "Expression parse error: " + e.getMessage(),
                        ctx.ruleId(), expression));
            }
            return NullValue.INSTANCE;
        }

        try {
            return evaluateAst(ast, ctx);
        } catch (Exception e) {
            if (ctx.diagnostics() != null) {
                ctx.diagnostics().add(new Diagnostic(
                        DiagnosticCode.EXPR_TYPE, Severity.ERROR,
                        "Expression evaluation error: " + e.getMessage(),
                        ctx.ruleId(), expression));
            }
            return NullValue.INSTANCE;
        }
    }

    // -- AST evaluation -----------------------------------------------

    private Value evaluateAst(Expression ast, EvalContext ctx) {
        return switch (ast) {
            case LiteralExpr l -> l.value();
            case PathExpr p -> {
                if (p.attributeName() == null || p.attributeName().isBlank()) {
                    // Bare alias: return the source object's OID as a reference
                    IomObject source = ctx.sources().get(p.alias());
                    if (source != null) {
                        String oid = source.getobjectoid();
                        if (oid != null) {
                            yield new ReferenceValue(source.getobjecttag(), oid);
                        }
                    }
                    yield NullValue.INSTANCE;
                }
                yield resolveSourcePath(p.alias() + "." + p.attributeName(), ctx);
            }
            case FunctionCallExpr f -> evaluateFunctionCall(f, ctx);
            case ConditionalExpr c -> evaluateConditional(c, ctx);
        };
    }

    private Value evaluateFunctionCall(FunctionCallExpr call, EvalContext ctx) {
        Optional<FunctionDef> defOpt = functionRegistry.resolve(call.functionName());
        if (defOpt.isEmpty()) {
            if (ctx.diagnostics() != null) {
                ctx.diagnostics().add(new Diagnostic(
                        DiagnosticCode.EXPR_UNKNOWN_FUNC, Severity.ERROR,
                        "Unknown function: " + call.functionName(),
                        ctx.ruleId(), "Check function name or ensure it is registered"));
            }
            return NullValue.INSTANCE;
        }
        FunctionDef def = defOpt.get();

        if (def.nonDeterministic() && ctx.diagnostics() != null) {
            ctx.diagnostics().add(new Diagnostic(
                    DiagnosticCode.EXPR_NON_DETERMINISTIC, Severity.WARNING,
                    "Non-deterministic function used: " + def.name(),
                    ctx.ruleId(), "Results may vary between runs"));
        }

        List<Value> evalArgs = call.arguments().stream()
                .map(arg -> evaluateAst(arg, ctx))
                .toList();
        return def.impl().apply(evalArgs, ctx);
    }

    private Value evaluateConditional(ConditionalExpr cond, EvalContext ctx) {
        Value conditionVal = evaluateAst(cond.condition(), ctx);
        boolean truthy = isTruthy(conditionVal);
        return evaluateAst(truthy ? cond.thenExpr() : cond.elseExpr(), ctx);
    }

    private boolean isTruthy(Value value) {
        if (!value.isDefined()) return false;
        if (value instanceof BooleanValue bv) return bv.value();
        if (value instanceof TextValue tv) return !tv.value().isEmpty();
        if (value instanceof NumberValue nv) return nv.value() != 0;
        return true;
    }

    // -- Source path resolution ---------------------------------------

    private Value resolveSourcePath(String path, EvalContext ctx) {
        String[] parts = path.split("\\.", 2);
        if (parts.length < 2) {
            // Bare alias: treat as reference to the source object's OID
            IomObject source = ctx.sources().get(parts[0]);
            if (source != null) {
                String oid = source.getobjectoid();
                if (oid != null) {
                    return new ReferenceValue(source.getobjecttag(), oid);
                }
                return NullValue.INSTANCE;
            }
            return NullValue.INSTANCE;
        }
        IomObject source = ctx.sources().get(parts[0]);
        if (source == null) {
            return NullValue.INSTANCE;
        }
        String attrName = parts[1];

        // Check if this is a geometry attribute
        TypeInfo attrType = resolveSourceAttributeType(ctx, parts[0], attrName);
        if (attrType != null && (attrType == guru.interlis.transformer.mapping.plan.TypeInfo.COORD
                || attrType == guru.interlis.transformer.mapping.plan.TypeInfo.POLYLINE
                || attrType == guru.interlis.transformer.mapping.plan.TypeInfo.SURFACE
                || attrType == guru.interlis.transformer.mapping.plan.TypeInfo.AREA)) {
            return resolveGeometryValue(source, attrName, attrType, ctx);
        }

        String attrValue = source.getattrvalue(parts[1]);
        if (attrValue == null) {
            return NullValue.INSTANCE;
        }
        return new TextValue(attrValue);
    }

    private TypeInfo resolveSourceAttributeType(EvalContext ctx, String alias, String attrName) {
        if (ctx.sourceAttributeTypes() == null) return null;
        Map<String, TypeInfo> aliasTypes = ctx.sourceAttributeTypes().get(alias);
        if (aliasTypes == null) return null;
        return aliasTypes.get(attrName);
    }

    private Value resolveGeometryValue(IomObject source, String attrName, TypeInfo attrType, EvalContext ctx) {
        if (ctx.geometryAdapter() == null) {
            String attrValue = source.getattrvalue(attrName);
            if (attrValue != null) return new TextValue(attrValue);
            return NullValue.INSTANCE;
        }
        IomObject geomObj = firstObjectAttribute(source, attrName);
        if (geomObj != null) {
            Value normalized = ctx.geometryAdapter().normalize(geomObj, attrType);
            if (attrType == TypeInfo.AREA && normalized instanceof GeometryObjectValue gov) {
                CoordValue pointOnSurface = resolvePointOnSurface(source, attrName, ctx);
                if (pointOnSurface != null) {
                    return gov.withPointOnSurface(pointOnSurface);
                }
            }
            return normalized;
        }
        String attrValue = source.getattrvalue(attrName);
        if (attrType == TypeInfo.COORD && attrValue != null) {
            Iom_jObject temp = new Iom_jObject(attrType.name(), null);
            temp.setattrvalue("value", attrValue);
            return ctx.geometryAdapter().normalize(temp, attrType);
        }
        if (attrValue != null || firstObjectAttribute(source, "_itf_" + attrName) != null) {
            reportMissingMergedGeometry(source, attrName, attrType, ctx);
        }
        return NullValue.INSTANCE;
    }

    private CoordValue resolvePointOnSurface(IomObject source, String attrName, EvalContext ctx) {
        IomObject helperPoint = firstObjectAttribute(source, "_itf_" + attrName);
        if (helperPoint != null) {
            Value value = ctx.geometryAdapter().normalize(helperPoint, TypeInfo.COORD);
            return value instanceof CoordValue cv ? cv : null;
        }
        return parsePointOnSurface(source.getattrvalue(attrName), ctx);
    }

    private CoordValue parsePointOnSurface(String attrValue, EvalContext ctx) {
        if (attrValue == null || attrValue.isBlank()) return null;
        Iom_jObject temp = new Iom_jObject("COORD", null);
        temp.setattrvalue("value", attrValue);
        Value value = ctx.geometryAdapter().normalize(temp, TypeInfo.COORD);
        return value instanceof CoordValue cv ? cv : null;
    }

    private IomObject firstObjectAttribute(IomObject source, String attrName) {
        int count = source.getattrvaluecount(attrName);
        if (count <= 0) return null;
        return source.getattrobj(attrName, 0);
    }

    private void reportMissingMergedGeometry(IomObject source, String attrName, TypeInfo attrType, EvalContext ctx) {
        if (ctx.diagnostics() == null) return;
        String oid = source.getobjectoid() != null ? source.getobjectoid() : "<no-oid>";
        ctx.diagnostics().add(new Diagnostic(
                DiagnosticCode.GEOM_INVALID,
                Severity.ERROR,
                "Geometry attribute " + source.getobjecttag() + "." + attrName
                        + " of type " + attrType
                        + " has no merged geometry object; only scalar data is available",
                source.getobjecttag() + "/" + oid,
                "Ensure the model-aware ITF reader merges geometry helper tables and the input contains them"));
    }

    // -- Legacy compatibility -----------------------------------------

    @Deprecated
    public Object evaluateLegacy(String expression, Map<String, IomObject> sources) {
        Value result = evaluate(expression, sources);
        return result.toNative();
    }
}
