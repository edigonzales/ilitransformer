package guru.interlis.transformer.expr.builtins;

import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.expr.BooleanValue;
import guru.interlis.transformer.expr.EvalContext;
import guru.interlis.transformer.expr.FunctionDef;
import guru.interlis.transformer.expr.FunctionRegistry;
import guru.interlis.transformer.expr.NullValue;
import guru.interlis.transformer.expr.ReferenceValue;
import guru.interlis.transformer.expr.TextValue;
import guru.interlis.transformer.expr.Value;
import guru.interlis.transformer.mapping.plan.TypeInfo;
import guru.interlis.transformer.state.CanonicalValue;
import guru.interlis.transformer.state.LookupKey;
import guru.interlis.transformer.state.SourceLookupIndex;
import guru.interlis.transformer.state.SourceRecord;

import ch.interlis.iom.IomObject;

import java.util.List;
import java.util.Objects;

public final class LookupFunctions {

    private LookupFunctions() {}

    public static void registerAll(FunctionRegistry registry) {
        registry.register(
                "oid",
                TypeInfo.TEXT,
                List.of(new FunctionDef.FunctionParam("alias", TypeInfo.TEXT)),
                LookupFunctions::oid);

        registry.register(
                "bagFirst",
                TypeInfo.TEXT,
                List.of(
                        new FunctionDef.FunctionParam("alias", TypeInfo.TEXT),
                        new FunctionDef.FunctionParam("bagAttr", TypeInfo.TEXT),
                        new FunctionDef.FunctionParam("valueAttr", TypeInfo.TEXT)),
                LookupFunctions::bagFirst);

        registry.register(
                "lookup",
                TypeInfo.UNKNOWN,
                List.of(
                        new FunctionDef.FunctionParam("classPath", TypeInfo.TEXT),
                        new FunctionDef.FunctionParam("keyAttr", TypeInfo.TEXT),
                        new FunctionDef.FunctionParam("keyValue", TypeInfo.TEXT),
                        new FunctionDef.FunctionParam("returnAttr", TypeInfo.TEXT)),
                LookupFunctions::lookup);

        registry.register(
                "lookupIn",
                TypeInfo.UNKNOWN,
                List.of(
                        new FunctionDef.FunctionParam("inputId", TypeInfo.TEXT),
                        new FunctionDef.FunctionParam("classPath", TypeInfo.TEXT),
                        new FunctionDef.FunctionParam("keyAttr", TypeInfo.TEXT),
                        new FunctionDef.FunctionParam("keyValue", TypeInfo.TEXT),
                        new FunctionDef.FunctionParam("returnAttr", TypeInfo.TEXT)),
                LookupFunctions::lookupIn);

        registry.register(FunctionDef.eagerVariadic(
                "existsIn",
                TypeInfo.BOOLEAN,
                List.of(
                        new FunctionDef.FunctionParam("inputId", TypeInfo.TEXT),
                        new FunctionDef.FunctionParam("classPath", TypeInfo.TEXT),
                        new FunctionDef.FunctionParam("keyAttr", TypeInfo.TEXT),
                        new FunctionDef.FunctionParam("keyValue", TypeInfo.TEXT)),
                LookupFunctions::existsIn));
    }

    static Value bagFirst(List<Value> args, EvalContext ctx) {
        if (args.size() < 3) {
            return NullValue.INSTANCE;
        }
        String alias = args.get(0).asText();
        String bagAttr = args.get(1).asText();
        String valueAttr = args.get(2).asText();
        IomObject source = ctx.sources().get(alias);
        if (source == null) {
            return NullValue.INSTANCE;
        }
        if (source.getattrvaluecount(bagAttr) <= 0) {
            return NullValue.INSTANCE;
        }
        IomObject bagItem = source.getattrobj(bagAttr, 0);
        if (bagItem == null) {
            return NullValue.INSTANCE;
        }
        String val = bagItem.getattrvalue(valueAttr);
        if (val == null) {
            return NullValue.INSTANCE;
        }
        return new TextValue(val);
    }

    static Value oid(List<Value> args, EvalContext ctx) {
        if (args.isEmpty()) {
            return NullValue.INSTANCE;
        }
        Value arg = args.get(0);
        if (arg instanceof ReferenceValue rv) {
            return new TextValue(rv.oid());
        }
        String alias = arg.asText();
        IomObject source = ctx.sources().get(alias);
        if (source == null) {
            if (ctx.diagnostics() != null) {
                ctx.diagnostics()
                        .add(new Diagnostic(
                                DiagnosticCode.EXPR_UNKNOWN_PATH,
                                Severity.WARNING,
                                "oid(): source alias '" + alias + "' not found in context",
                                ctx.ruleId(),
                                "Check alias declaration in rule sources"));
            }
            return NullValue.INSTANCE;
        }
        String objectOid = source.getobjectoid();
        if (objectOid == null || objectOid.isBlank()) {
            return NullValue.INSTANCE;
        }
        return new TextValue(objectOid);
    }

    static Value lookup(List<Value> args, EvalContext ctx) {
        if (args.size() < 4) {
            if (ctx.diagnostics() != null) {
                ctx.diagnostics()
                        .add(new Diagnostic(
                                DiagnosticCode.EXPR_WRONG_ARG_COUNT,
                                Severity.ERROR,
                                "lookup() requires 4 arguments: classPath, keyAttr, keyValue, returnAttr",
                                ctx.ruleId(),
                                "Use lookup('Class.Path', 'KeyAttr', oid(alias), 'ReturnAttr')"));
            }
            return NullValue.INSTANCE;
        }

        String classPath = args.get(0).asText();
        String keyAttr = args.get(1).asText();
        String keyValue = args.get(2).asText();
        String returnAttr = args.get(3).asText();

        return lookupInternal(null, classPath, keyAttr, keyValue, returnAttr, ctx, "lookup");
    }

    static Value lookupIn(List<Value> args, EvalContext ctx) {
        if (args.size() < 5) {
            if (ctx.diagnostics() != null) {
                ctx.diagnostics()
                        .add(new Diagnostic(
                                DiagnosticCode.EXPR_WRONG_ARG_COUNT,
                                Severity.ERROR,
                                "lookupIn() requires 5 arguments: inputId, classPath, keyAttr, keyValue, returnAttr",
                                ctx.ruleId(),
                                "Use lookupIn('inputId', 'Class.Path', 'KeyAttr', oid(alias), 'ReturnAttr')"));
            }
            return NullValue.INSTANCE;
        }

        String inputId = args.get(0).asText();
        String classPath = args.get(1).asText();
        String keyAttr = args.get(2).asText();
        String keyValue = args.get(3).asText();
        String returnAttr = args.get(4).asText();

        return lookupInternal(inputId, classPath, keyAttr, keyValue, returnAttr, ctx, "lookupIn");
    }

    static Value existsIn(List<Value> args, EvalContext ctx) {
        if (args.size() < 4 || args.size() % 2 != 0) {
            if (ctx.diagnostics() != null) {
                ctx.diagnostics()
                        .add(new Diagnostic(
                                DiagnosticCode.EXPR_WRONG_ARG_COUNT,
                                Severity.ERROR,
                                "existsIn() requires inputId, classPath and one or more keyAttr/keyValue pairs",
                                ctx.ruleId(),
                                "Use existsIn('inputId', 'Class.Path', 'KeyAttr', keyValue)"));
            }
            return BooleanValue.FALSE;
        }

        SourceLookupIndex index = ctx.lookupIndex();
        if (index == null) {
            if (ctx.diagnostics() != null) {
                ctx.diagnostics()
                        .add(new Diagnostic(
                                DiagnosticCode.LOOKUP_INDEX_MISSING,
                                Severity.ERROR,
                                "existsIn() called but no SourceLookupIndex is available in context",
                                ctx.ruleId(),
                                "Ensure the engine initialized the source lookup index"));
            }
            return BooleanValue.FALSE;
        }

        String inputId = args.get(0).asText();
        String classPath = args.get(1).asText();
        String firstKeyAttr = args.get(2).asText();
        String firstKeyValue = args.get(3).asText();
        if (isBlank(classPath) || isBlank(firstKeyAttr) || firstKeyValue == null) {
            return BooleanValue.FALSE;
        }

        LookupKey key =
                new LookupKey(inputId, classPath, firstKeyAttr, new CanonicalValue("text", firstKeyValue, true));
        List<SourceRecord> hits = index.lookup(key);
        if (hits.isEmpty()) {
            return BooleanValue.FALSE;
        }

        for (SourceRecord hit : hits) {
            if (matchesAllAdditionalPairs(hit, args)) {
                return BooleanValue.TRUE;
            }
        }
        return BooleanValue.FALSE;
    }

    private static Value lookupInternal(
            String inputId,
            String classPath,
            String keyAttr,
            String keyValue,
            String returnAttr,
            EvalContext ctx,
            String functionName) {
        SourceLookupIndex index = ctx.lookupIndex();
        if (index == null) {
            if (ctx.diagnostics() != null) {
                ctx.diagnostics()
                        .add(new Diagnostic(
                                DiagnosticCode.LOOKUP_INDEX_MISSING,
                                Severity.ERROR,
                                functionName + "() called but no SourceLookupIndex is available in context",
                                ctx.ruleId(),
                                "Ensure the engine initialized the source lookup index"));
            }
            return NullValue.INSTANCE;
        }

        LookupKey key = new LookupKey(inputId, classPath, keyAttr, new CanonicalValue("text", keyValue, true));

        List<SourceRecord> hits = index.lookup(key);
        if (hits.isEmpty()) {
            if (ctx.diagnostics() != null) {
                ctx.diagnostics()
                        .add(new Diagnostic(
                                DiagnosticCode.LOOKUP_NO_MATCH,
                                Severity.WARNING,
                                functionName + "() found no match for " + classPath + "." + keyAttr + "=" + keyValue,
                                ctx.ruleId(),
                                "Verify the referenced child object exists in the source data"));
            }
            return NullValue.INSTANCE;
        }
        String attrValue = hits.get(0).sourceObject().getattrvalue(returnAttr);
        if (hits.size() > 1 && !allReturnValuesEqual(hits, returnAttr)) {
            if (ctx.diagnostics() != null) {
                ctx.diagnostics()
                        .add(new Diagnostic(
                                DiagnosticCode.LOOKUP_AMBIGUOUS,
                                Severity.WARNING,
                                functionName + "() found " + hits.size() + " matches for " + classPath + "." + keyAttr
                                        + "=" + keyValue + ", using first — EGID may be overdetermined",
                                ctx.ruleId(),
                                "Ensure the lookup key uniquely identifies one record"));
            }
        }
        if (attrValue == null) {
            return NullValue.INSTANCE;
        }
        return new TextValue(attrValue);
    }

    private static boolean allReturnValuesEqual(List<SourceRecord> hits, String returnAttr) {
        String first = null;
        boolean initialized = false;
        for (SourceRecord hit : hits) {
            IomObject sourceObject = hit.sourceObject();
            String value = sourceObject != null ? sourceObject.getattrvalue(returnAttr) : null;
            if (!initialized) {
                first = value;
                initialized = true;
                continue;
            }
            if (!java.util.Objects.equals(first, value)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesAllAdditionalPairs(SourceRecord hit, List<Value> args) {
        IomObject sourceObject = hit.sourceObject();
        if (sourceObject == null) {
            return false;
        }
        for (int i = 4; i < args.size(); i += 2) {
            String attrName = args.get(i).asText();
            String expected = args.get(i + 1).asText();
            if (expected == null || !Objects.equals(expected, readAttributeOrRefOid(sourceObject, attrName))) {
                return false;
            }
        }
        return true;
    }

    private static String readAttributeOrRefOid(IomObject source, String attrName) {
        if (source == null || isBlank(attrName)) {
            return null;
        }
        String scalar = source.getattrvalue(attrName);
        if (scalar != null) {
            return scalar;
        }
        if (source.getattrvaluecount(attrName) > 0) {
            IomObject refObj = source.getattrobj(attrName, 0);
            if (refObj != null && refObj.getobjectrefoid() != null) {
                return refObj.getobjectrefoid();
            }
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
