package guru.interlis.transformer.expr.builtins;

import guru.interlis.transformer.expr.BooleanValue;
import guru.interlis.transformer.expr.EvalContext;
import guru.interlis.transformer.expr.FunctionDef;
import guru.interlis.transformer.expr.FunctionRegistry;
import guru.interlis.transformer.expr.NullValue;
import guru.interlis.transformer.expr.ReferenceValue;
import guru.interlis.transformer.expr.TextValue;
import guru.interlis.transformer.expr.Value;
import guru.interlis.transformer.mapping.plan.TypeInfo;
import guru.interlis.transformer.model.TypeSystemFacade;
import guru.interlis.transformer.state.SourceRecord;

import java.util.List;

public final class RefFunctions {

    private RefFunctions() {}

    public static void registerAll(FunctionRegistry registry) {
        registry.register(
                "refOid",
                TypeInfo.TEXT,
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
        return args.size() == 1 && args.getFirst() instanceof ReferenceValue ref && ref.oid() != null
                ? new TextValue(ref.oid())
                : NullValue.INSTANCE;
    }

    static Value refEquals(List<Value> args, EvalContext ctx) {
        if (args.size() != 2
                || !(args.get(0) instanceof ReferenceValue a)
                || !(args.get(1) instanceof ReferenceValue b)
                || a.oid() == null
                || !a.oid().equals(b.oid())
                || a.sourceAlias() == null
                || b.sourceAlias() == null) {
            return BooleanValue.FALSE;
        }
        SourceRecord left = ctx.sourceRecords().get(a.sourceAlias());
        SourceRecord right = ctx.sourceRecords().get(b.sourceAlias());
        if (left == null
                || right == null
                || left.sourceFileId() == null
                || !left.sourceFileId().equals(right.sourceFileId())) {
            return BooleanValue.FALSE;
        }

        var input = ctx.inputs().get(left.sourceFileId());
        var types = input == null ? null : input.typeSystem();
        var leftRole = referenceRole(types, left, a);
        var rightRole = referenceRole(types, right, b);
        String leftClass = leftRole == null ? a.targetClass() : leftRole.targetClass();
        String rightClass = rightRole == null ? b.targetClass() : rightRole.targetClass();
        if (leftClass != null
                && rightClass != null
                && !leftClass.equals(rightClass)
                && !compatibleClasses(types, a, leftClass, b, rightClass)) {
            return BooleanValue.FALSE;
        }

        // Two explicit BIDs are both part of the comparison. An XTF 2.4
        // EXTERNAL role without a BID identifies its target within the transfer.
        if (a.isTransferScoped(input, leftRole) || b.isTransferScoped(input, rightRole)) {
            return BooleanValue.TRUE;
        }
        String leftBid = a.basketId() == null ? left.sourceBasketId() : a.basketId();
        String rightBid = b.basketId() == null ? right.sourceBasketId() : b.basketId();
        return BooleanValue.of(leftBid != null && leftBid.equals(rightBid));
    }

    private static TypeSystemFacade.ReferenceInfo referenceRole(
            TypeSystemFacade types, SourceRecord source, ReferenceValue ref) {
        return types == null || ref.sourceAttribute() == null
                ? null
                : types.resolveReference(source.sourceClass(), ref.sourceAttribute());
    }

    private static boolean compatibleClasses(
            TypeSystemFacade types, ReferenceValue a, String leftClass, ReferenceValue b, String rightClass) {
        return types != null
                && (a.sourceAttribute() != null && types.isTypeCompatible(leftClass, rightClass)
                        || b.sourceAttribute() != null && types.isTypeCompatible(rightClass, leftClass));
    }
}
