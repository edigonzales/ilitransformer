package guru.interlis.transformer.engine;

import ch.interlis.iom.IomObject;
import ch.interlis.iox.EndBasketEvent;
import ch.interlis.iox.EndTransferEvent;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox.ObjectEvent;
import ch.interlis.iox.StartBasketEvent;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.mapping.plan.BagPlan;
import guru.interlis.transformer.mapping.plan.RulePlan;
import guru.interlis.transformer.mapping.plan.SourcePlan;
import guru.interlis.transformer.mapping.plan.TransformPlan;
import guru.interlis.transformer.model.TypeSystemFacade;
import guru.interlis.transformer.state.ParentChildIndex;
import guru.interlis.transformer.state.SourceLookupIndex;
import guru.interlis.transformer.state.SourceRecord;
import guru.interlis.transformer.state.StateStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public final class SourceIndexingService {

    public IndexingResult indexSources(
            TransformPlan plan,
            Function<String, IoxReader> readerFactoryById,
            RuleDispatchIndex dispatchIndex,
            StateStore stateStore,
            SourceLookupIndex sourceLookupIndex,
            ParentChildIndex parentChildIndex,
            DiagnosticCollector diagnostics,
            ExecutionMetrics metrics) throws Exception {

        metrics.recordReadStart();
        Map<String, List<SourceRecord>> recordsByInput = new HashMap<>();
        long recordCount = 0;

        Set<String> inputIds = new HashSet<>();
        for (RulePlan rule : plan.rules()) {
            for (SourcePlan sp : rule.sources()) {
                inputIds.addAll(sp.inputIds());
            }
            for (BagPlan bag : rule.bags()) {
                inputIds.addAll(bag.fromSource().inputIds());
            }
        }

        for (String inputId : inputIds) {
            List<SourceRecord> inputRecords = new ArrayList<>();
            IoxReader reader = readerFactoryById.apply(inputId);
            try {
                String basketId = null;
                IoxEvent event;
                while ((event = reader.read()) != null) {
                    if (event instanceof StartBasketEvent basket) {
                        basketId = basket.getBid();
                        continue;
                    }
                    if (event instanceof EndBasketEvent) {
                        basketId = null;
                        continue;
                    }
                    if (event instanceof EndTransferEvent) {
                        break;
                    }
                    if (event instanceof ObjectEvent obj) {
                        IomObject source = obj.getIomObject();
                        stateStore.indexSourceObject(source.getobjecttag(), inputId, basketId, source);
                        SourceRecord sr = new SourceRecord(inputId, basketId, source.getobjecttag(), source);
                        stateStore.addSourceRecord(sr);
                        sourceLookupIndex.index(sr);
                        inputRecords.add(sr);
                        recordCount++;

                        indexBagChild(dispatchIndex, sr, parentChildIndex);

                        expandBagStructures(dispatchIndex, source, inputId, basketId, plan, stateStore);
                    }
                }
            } finally {
                reader.close();
            }
            recordsByInput.put(inputId, inputRecords);
        }

        metrics.recordReadEnd((int) recordCount);
        return new IndexingResult(recordCount, Collections.unmodifiableMap(recordsByInput));
    }

    private static void indexBagChild(RuleDispatchIndex dispatchIndex, SourceRecord sr,
                                       ParentChildIndex parentChildIndex) {
        String sourceClass = sr.sourceClass();
        String sourceOid = sr.sourceObject().getobjectoid();

        List<BagPlan> embedBags = dispatchIndex.embedBagsFor(sr.sourceFileId(), sourceClass);
        for (BagPlan bag : embedBags) {
            if (!bag.hasParentRef()) continue;
            String refAttr = bag.parentRefAttribute();
            String parentOid = readReferenceOid(sr.sourceObject(), refAttr);
            if (parentOid != null && !parentOid.isBlank()) {
                parentChildIndex.index(sourceClass, refAttr, parentOid, sr);
            }
        }
    }

    private static String readReferenceOid(IomObject source, String refAttr) {
        if (source.getattrvaluecount(refAttr) > 0) {
            IomObject refObj = source.getattrobj(refAttr, 0);
            if (refObj != null && refObj.getobjectrefoid() != null) {
                return refObj.getobjectrefoid();
            }
        }
        return source.getattrvalue(refAttr);
    }

    private static void expandBagStructures(RuleDispatchIndex dispatchIndex, IomObject source,
                                             String inputId, String basketId,
                                             TransformPlan plan, StateStore stateStore) {
        String sourceClass = source.getobjecttag();
        List<RuleDispatchIndex.BagExpansionEntry> entries = dispatchIndex.expandBagsFor(inputId, sourceClass);
        for (var entry : entries) {
            BagPlan bag = entry.bag();
            String parentClassName = TypeSystemFacade.getScopedName(entry.parentSource().sourceClass());
            if (!parentClassName.equals(sourceClass)) continue;

            String bagAttrName = bag.bagAttrName();
            int count = source.getattrvaluecount(bagAttrName);
            if (count <= 0) continue;

            for (int i = 0; i < count; i++) {
                IomObject structure = source.getattrobj(bagAttrName, i);
                if (structure == null) continue;
                structure.setattrvalue("_parentOid", source.getobjectoid());
                structure.setattrvalue("_parentClass", source.getobjecttag());
                stateStore.addSourceRecord(new SourceRecord(
                        inputId, basketId,
                        structure.getobjecttag(),
                        structure));
            }
        }
    }

    public record IndexingResult(long recordsRead, Map<String, List<SourceRecord>> recordsByInput) {}
}
