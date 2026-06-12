package guru.interlis.transformer.engine;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCode;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.expr.BooleanValue;
import guru.interlis.transformer.expr.EvalContext;
import guru.interlis.transformer.expr.ExpressionEngine;
import guru.interlis.transformer.expr.Value;
import guru.interlis.transformer.loss.LossEvent;
import guru.interlis.transformer.loss.LossinessCollector;
import guru.interlis.transformer.mapping.plan.AssignmentPlan;
import guru.interlis.transformer.mapping.plan.BagPlan;
import guru.interlis.transformer.mapping.plan.OutputBinding;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.mapping.plan.TypeInfo;
import guru.interlis.transformer.model.TypeSystemFacade;
import guru.interlis.transformer.state.CanonicalValue;
import guru.interlis.transformer.state.DuplicateTargetOidException;
import guru.interlis.transformer.state.OidGenerationRequest;
import guru.interlis.transformer.state.OidGenerationService;
import guru.interlis.transformer.state.OidStrategy;
import guru.interlis.transformer.state.SourceRecord;
import guru.interlis.transformer.state.TargetObjectKey;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class BagTransformationService {

    private final ExpressionEngine expressionEngine;
    private final OidGenerationService oidGenerationService;
    private final LossinessCollector lossinessCollector;

    public BagTransformationService(ExpressionEngine expressionEngine, OidGenerationService oidGenerationService) {
        this(expressionEngine, oidGenerationService, null);
    }

    public BagTransformationService(ExpressionEngine expressionEngine, OidGenerationService oidGenerationService,
                                    LossinessCollector lossinessCollector) {
        this.expressionEngine = expressionEngine;
        this.oidGenerationService = oidGenerationService;
        this.lossinessCollector = lossinessCollector;
    }

    public void embed(BagExecutionContext ctx) {
        BagPlan bag = ctx.plan();
        Iom_jObject target = (Iom_jObject) ctx.target();
        TransformPlan plan = ctx.transformPlan();

        List<SourceRecord> bagSourceRecords;
        String bagSourceClass = null;
        if (bag.fromSource().sourceClass() != null) {
            bagSourceClass = TypeSystemFacade.getScopedName(bag.fromSource().sourceClass());
        }

        if (bag.hasParentRef()) {
            String parentOid = ctx.parent().sourceRecord().sourceObject().getobjectoid();
            if (bag.fromSource().sourceClass() != null) {
                String resolvedSourceClass = TypeSystemFacade.getScopedName(bag.fromSource().sourceClass());
                bagSourceRecords = ctx.parentChildIndex().children(
                        resolvedSourceClass, bag.parentRefAttribute(), parentOid);
                if (bag.fromSource().inputIds() != null && !bag.fromSource().inputIds().isEmpty()) {
                    String inputId = bag.fromSource().inputIds().iterator().next();
                    bagSourceRecords = bagSourceRecords.stream()
                            .filter(r -> inputId.equals(r.sourceFileId()))
                            .toList();
                }
            } else {
                // Fallback when sourceClass unknown: scan stateStore and filter by parent ref attribute
                bagSourceRecords = new ArrayList<>();
                for (SourceRecord sr : ctx.stateStore().sourceRecords()) {
                    IomObject obj = sr.sourceObject();
                    if (obj.getattrvaluecount(bag.parentRefAttribute()) <= 0) continue;
                    IomObject ref = obj.getattrobj(bag.parentRefAttribute(), 0);
                    if (ref != null && parentOid.equals(ref.getobjectrefoid())) {
                        boolean inputMatch = bag.fromSource().inputIds().isEmpty()
                                || bag.fromSource().inputIds().contains(sr.sourceFileId());
                        if (inputMatch) {
                            bagSourceRecords.add(sr);
                        }
                    }
                }
            }
        } else {
            // Fallback: scan stateStore, optionally filtered by sourceClass
            bagSourceRecords = new ArrayList<>();
            for (SourceRecord sr : ctx.stateStore().sourceRecords()) {
                boolean classMatch = bagSourceClass == null || bagSourceClass.equals(sr.sourceClass());
                boolean inputMatch = bag.fromSource().inputIds().isEmpty()
                        || bag.fromSource().inputIds().contains(sr.sourceFileId());
                if (classMatch && inputMatch) {
                    bagSourceRecords.add(sr);
                }
            }
        }

        if (bagSourceRecords.isEmpty()) {
            return;
        }

        List<SourceRecord> sorted = new ArrayList<>(bagSourceRecords);
        sorted.sort(Comparator.comparing(sr -> sr.sourceObject().getobjectoid(),
                Comparator.nullsLast(Comparator.naturalOrder())));

        List<IomObject> structures = new ArrayList<>();
        Map<String, IomObject> parentSources = Map.of(
                bag.parentAlias() != null ? bag.parentAlias() : ctx.parent().sourcePlan().alias(),
                ctx.parent().sourceRecord().sourceObject());

        for (SourceRecord sr : sorted) {
            IomObject bagSource = sr.sourceObject();
            Map<String, IomObject> allSources = new LinkedHashMap<>();
            allSources.put(bag.fromSource().alias(), bagSource);
            allSources.putAll(parentSources);

            EvalContext bagCtx = new EvalContext(allSources, ctx.diagnostics(),
                    ctx.rule().ruleId(), plan.enumMaps(),
                    ctx.geometryAdapter(), ctx.sourceAttributeTypes());

            if (bag.whereExpression() != null && bag.whereExpression().sourceText() != null
                    && !bag.whereExpression().sourceText().isBlank()) {
                Value whereResult = expressionEngine.evaluate(bag.whereExpression(), bagCtx);
                if (!isFilterTruthy(whereResult)) {
                    continue;
                }
            }

            Iom_jObject struct = new Iom_jObject(bag.structureName(), null);

            Map<String, IomObject> assignSources = Map.of(bag.fromSource().alias(), bagSource);
            EvalContext assignCtx = new EvalContext(assignSources, ctx.diagnostics(),
                    ctx.rule().ruleId(), plan.enumMaps(),
                    ctx.geometryAdapter(), ctx.sourceAttributeTypes());

            for (AssignmentPlan ap : bag.assignments()) {
                Value value = expressionEngine.evaluate(ap.expression(), assignCtx);
                if (value.isDefined()) {
                    setBagAttribute(struct, ap, value, ctx);
                }
            }

            checkMandatoryAttributes(struct, bag, ctx.diagnostics());

            // Embed nested bags recursively
            for (BagPlan nestedBag : bag.nestedBags()) {
                if (nestedBag.isEmbed()) {
                    BagExecutionContext nestedCtx = new BagExecutionContext(
                            nestedBag,
                            new BoundSourceObject(bag.fromSource(), sr),
                            struct,
                            ctx.transformPlan(),
                            ctx.stateStore(),
                            ctx.parentChildIndex(),
                            ctx.diagnostics(),
                            ctx.rule(),
                            ctx.expandedTargets(),
                            ctx.geometryAdapter(),
                            ctx.sourceAttributeTypes(),
                            ctx.referenceIndex()
                    );
                    embed(nestedCtx);
                }
            }

            structures.add(struct);
        }

        for (IomObject struct : structures) {
            target.addattrobj(bag.bagAttrName(), struct);
        }
    }

    public void expand(BagExecutionContext ctx) {
        expandBag(ctx.plan(), ctx.parent().sourceRecord(),
                ctx.parent().sourceRecord().sourceObject(), (Iom_jObject) ctx.target(), ctx);
    }

    private void expandBag(BagPlan bag, SourceRecord parentRecord, IomObject parentObj,
                           Iom_jObject parentTarget, BagExecutionContext ctx) {
        TransformPlan plan = ctx.transformPlan();

        String bagAttrName = bag.bagAttrName();
        int count = parentObj.getattrvaluecount(bagAttrName);
        if (count <= 0) return;

        int limit = count;
        if (bag.maxItems() != null && bag.maxItems() >= 0 && count > bag.maxItems()) {
            limit = bag.maxItems();
            if (lossinessCollector != null) {
                lossinessCollector.record(new LossEvent(
                        ctx.rule().ruleId(),
                        parentRecord.sourceClass(),
                        parentObj.getobjectoid(),
                        bagAttrName,
                        "BAG_MAX_ITEMS_EXCEEDED",
                        "BAG '" + bagAttrName + "' contains " + count
                                + " items; only " + limit + " can be materialized"));
            }
        }

        String outputId = ctx.rule().outputId();

        for (int i = 0; i < limit; i++) {
            IomObject structure = parentObj.getattrobj(bagAttrName, i);
            if (structure == null) continue;

            String targetOid;
            if (bag.hasIdentityPlan()) {
                try {
                    targetOid = oidGenerationService.generate(new OidGenerationRequest(
                            bag.identityPlan().oidStrategy(),
                            bag.identityPlan().namespace(),
                            ctx.rule().ruleId(),
                            parentRecord.sourceFileId(),
                            parentRecord.sourceBasketId(),
                            bag.structureName(),
                            parentObj.getobjectoid() + "-" + i,
                            new LinkedHashMap<>(),
                            null));
                } catch (UnsupportedOperationException e) {
                    targetOid = oidGenerationService.generate(new OidGenerationRequest(
                            OidStrategy.INTEGER,
                            bag.identityPlan().namespace(),
                            ctx.rule().ruleId(),
                            parentRecord.sourceFileId(),
                            parentRecord.sourceBasketId(),
                            bag.structureName(),
                            parentObj.getobjectoid() + "-" + i,
                            new LinkedHashMap<>(),
                            null));
                }
            } else {
                try {
                    targetOid = oidGenerationService.generate(new OidGenerationRequest(
                            plan.oidPlan().defaultStrategy(),
                            plan.oidPlan().namespace(),
                            ctx.rule().ruleId(),
                            parentRecord.sourceFileId(),
                            parentRecord.sourceBasketId(),
                            bag.structureName(),
                            parentObj.getobjectoid() + "-" + i,
                            new LinkedHashMap<>(),
                            null));
                } catch (UnsupportedOperationException e) {
                    targetOid = oidGenerationService.generate(new OidGenerationRequest(
                            OidStrategy.INTEGER,
                            plan.oidPlan().namespace(),
                            ctx.rule().ruleId(),
                            parentRecord.sourceFileId(),
                            parentRecord.sourceBasketId(),
                            bag.structureName(),
                            parentObj.getobjectoid() + "-" + i,
                            new LinkedHashMap<>(),
                            null));
                }
            }

            Iom_jObject bagTarget = new Iom_jObject(bag.structureName(), targetOid);

            Map<String, IomObject> assignSources = new LinkedHashMap<>();
            assignSources.put(bag.fromSource().alias(), structure);
            String parentAlias = bag.parentAlias();
            if (parentAlias != null && !parentAlias.isBlank() && !parentAlias.equals(bag.fromSource().alias())) {
                assignSources.put(parentAlias, parentObj);
            }
            EvalContext assignCtx = new EvalContext(assignSources, ctx.diagnostics(),
                    ctx.rule().ruleId(), plan.enumMaps(),
                    ctx.geometryAdapter(), ctx.sourceAttributeTypes());

            for (AssignmentPlan ap : bag.assignments()) {
                Value value = expressionEngine.evaluate(ap.expression(), assignCtx);
                if (value.isDefined()) {
                    setBagAttribute(bagTarget, ap, value, ctx);
                }
            }

            checkMandatoryAttributes(bagTarget, bag, ctx.diagnostics());

            try {
                ctx.stateStore().registerTarget(
                        new TargetObjectKey(outputId, bag.structureName(), bagTarget.getobjectoid()),
                        bagTarget);
            } catch (DuplicateTargetOidException e) {
                ctx.diagnostics().add(new Diagnostic(DiagnosticCode.RUN_DUPLICATE_TARGET_OID, Severity.ERROR,
                        e.getMessage(), bag.structureName(), "This indicates a bug in OID generation"));
            }

            bindParentReference(bag, bagTarget, parentTarget, ctx);

            // Nested bags (EXPAND only)
            for (BagPlan nestedBag : bag.nestedBags()) {
                if (nestedBag.isExpand()) {
                    expandBag(nestedBag, parentRecord, structure, bagTarget, ctx);
                }
            }

            // Add to expanded targets for output merging
            String targetTopic = extractTopic(bag.structureName());
            String targetBasketId = BasketRouter.determineTargetBasket(
                    plan.basketPlan().defaultStrategy(), parentRecord.sourceBasketId(), targetTopic,
                    bag.structureName());
            String basketKey = basketKey(targetTopic, targetBasketId);
            ctx.expandedTargets()
                    .computeIfAbsent(outputId, ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(basketKey, ignored -> new ArrayList<>())
                    .add(bagTarget);
        }
    }

    private void checkMandatoryAttributes(Iom_jObject struct, BagPlan bag, DiagnosticCollector diag) {
        for (AssignmentPlan ap : bag.assignments()) {
            String attrName = ap.targetAttrName();
            if (struct.getattrvalue(attrName) == null && struct.getattrvaluecount(attrName) == 0) {
                diag.add(new Diagnostic(DiagnosticCode.RUN_BAG_MANDATORY_MISSING, Severity.WARNING,
                        "Mandatory structure attribute not set: " + attrName
                                + " in structure " + bag.structureName(),
                        bag.bagAttrName(), "Check bag assignment coverage"));
            }
        }
    }

    private void bindParentReference(BagPlan bag, Iom_jObject bagTarget,
                                     Iom_jObject parentTarget, BagExecutionContext ctx) {
        if (bag.parentRefPlan() == null) {
            return;
        }
        String parentTargetOid = parentTarget.getobjectoid();
        if (parentTargetOid == null || parentTargetOid.isBlank()) {
            ctx.diagnostics().add(new Diagnostic(DiagnosticCode.RUN_REF_MISSING_MANDATORY, Severity.ERROR,
                    "Expanded BAG parent target has no OID for role '" + bag.parentRefPlan().targetRoleName() + "'",
                    bag.structureName() + "/" + bagTarget.getobjectoid(),
                    "Check BAG OID generation and parent target materialization"));
            return;
        }
        IomObject ref = bagTarget.addattrobj(bag.parentRefPlan().targetRoleName(), Iom_jObject.REF);
        ref.setobjectrefoid(parentTargetOid);
    }

    static String extractTopic(String qualifiedClassName) {
        String[] parts = qualifiedClassName.split("\\.");
        if (parts.length >= 2) {
            return parts[0] + "." + parts[1];
        }
        return qualifiedClassName;
    }

    static String basketKey(String topic, String basketId) {
        return topic + "::" + (basketId == null ? "" : basketId);
    }

    private static boolean isFilterTruthy(Value value) {
        if (value == null || value.isNull()) return false;
        if (value instanceof BooleanValue bv) return bv.value();
        if (value instanceof guru.interlis.transformer.expr.TextValue tv) return !tv.value().isEmpty();
        if (value instanceof guru.interlis.transformer.expr.NumberValue nv)
            return nv.value().compareTo(java.math.BigDecimal.ZERO) != 0;
        return true;
    }

    private void setBagAttribute(Iom_jObject target, AssignmentPlan ap, Value value,
                                 BagExecutionContext ctx) {
        TypeInfo targetType = ap.expression().resultType();
        if (isGeometryType(targetType) && ctx.geometryAdapter() != null) {
            IomObject geomObj = ctx.geometryAdapter().denormalize(value, targetType);
            if (geomObj != null) {
                target.addattrobj(ap.targetAttrName(), geomObj);
            }
            return;
        }
        Object nativeValue = value.toNative();
        if (nativeValue != null) {
            target.setattrvalue(ap.targetAttrName(), nativeValue.toString());
        }
    }

    private static boolean isGeometryType(TypeInfo type) {
        return type == TypeInfo.COORD || type == TypeInfo.POLYLINE
                || type == TypeInfo.SURFACE || type == TypeInfo.AREA;
    }
}
