package guru.interlis.transformer.expr.builtins;

import guru.interlis.transformer.expr.EvalContext;
import guru.interlis.transformer.expr.FunctionDef;
import guru.interlis.transformer.expr.FunctionRegistry;
import guru.interlis.transformer.expr.NullValue;
import guru.interlis.transformer.expr.ReferenceValue;
import guru.interlis.transformer.expr.Value;
import guru.interlis.transformer.mapping.plan.TypeInfo;

import java.util.List;

public final class RefFunctions {

    private RefFunctions() {}

    public static void registerAll(FunctionRegistry registry) {
        registry.register(
                "refOid",
                TypeInfo.REFERENCE,
                List.of(new FunctionDef.FunctionParam("ref", TypeInfo.REFERENCE)),
                RefFunctions::refOid);

        registry.register(
                "refEquals",
                TypeInfo.BOOLEAN,
                List.of(
                        new FunctionDef.FunctionParam("a", TypeInfo.REFERENCE),
                        new FunctionDef.FunctionParam("b", TypeInfo.REFERENCE)),
                RefFunctions::refEquals);
    }

    static Value refOid(List<Value> args, EvalContext ctx) {
        if (args.isEmpty() || !args.get(0).isDefined()) return NullValue.INSTANCE;
        Value val = args.get(0);
        if (val instanceof ReferenceValue rv) {
            return new guru.interlis.transformer.expr.TextValue(rv.oid());
        }
        return val;
    }

    static Value refEquals(List<Value> args, EvalContext ctx) {
        if (args.size() < 2) return guru.interlis.transformer.expr.BooleanValue.FALSE;
        Value a = args.get(0);
        Value b = args.get(1);
        if (!a.isDefined() || !b.isDefined()) return guru.interlis.transformer.expr.BooleanValue.FALSE;
        if (a instanceof ReferenceValue ra && b instanceof ReferenceValue rb) {
            return guru.interlis.transformer.expr.BooleanValue.of(ra.oid().equals(rb.oid()));
        }
        return guru.interlis.transformer.expr.BooleanValue.of(a.asText().equals(b.asText()));
    }
}
