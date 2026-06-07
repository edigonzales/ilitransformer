package guru.interlis.transformer.engine;

import ch.interlis.iom.IomObject;
import ch.interlis.iom_j.Iom_jObject;
import ch.interlis.iox.EndBasketEvent;
import ch.interlis.iox.EndTransferEvent;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox.IoxWriter;
import ch.interlis.iox.ObjectEvent;
import ch.interlis.iox.StartBasketEvent;
import guru.interlis.transformer.diag.Diagnostic;
import guru.interlis.transformer.diag.DiagnosticCollector;
import guru.interlis.transformer.diag.Severity;
import guru.interlis.transformer.expr.ExpressionEngine;
import guru.interlis.transformer.mapping.model.JobConfig;
import guru.interlis.transformer.state.DeferredRef;
import guru.interlis.transformer.state.SourceRecord;
import guru.interlis.transformer.state.SourceRefKey;
import guru.interlis.transformer.state.StateStore;
import guru.interlis.transformer.state.TargetRefValue;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class TransformationEngine {
    private final ExpressionEngine expressionEngine;
    private final StateStore stateStore;
    private final DiagnosticCollector diagnostics;

    public TransformationEngine(ExpressionEngine expressionEngine, StateStore stateStore, DiagnosticCollector diagnostics) {
        this.expressionEngine = expressionEngine;
        this.stateStore = stateStore;
        this.diagnostics = diagnostics;
    }

    public void run(JobConfig config, Function<JobConfig.InputSpec, IoxReader> readerFactory, Map<String, IoxWriter> writersByOutputId) throws Exception {
        pass1Index(config, readerFactory);
        Map<String, Map<String, List<IomObject>>> objectsByOutputAndBasket = pass2BuildTargets(config);
        resolveDeferredRefs();
        writeOutputs(config, writersByOutputId, objectsByOutputAndBasket);
    }

    private void pass1Index(JobConfig config, Function<JobConfig.InputSpec, IoxReader> readerFactory) throws Exception {
        for (JobConfig.InputSpec input : config.job.inputs) {
            IoxReader reader = readerFactory.apply(input);
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
                        stateStore.indexSourceObject(source.getobjecttag(), input.id, basketId, source);
                        stateStore.addSourceRecord(new SourceRecord(input.id, basketId, source.getobjecttag(), source));
                    }
                }
            } finally {
                reader.close();
            }
        }
    }

    private Map<String, Map<String, List<IomObject>>> pass2BuildTargets(JobConfig config) {
        Map<String, Map<String, List<IomObject>>> objectsByOutputAndBasket = new LinkedHashMap<>();
        for (SourceRecord record : stateStore.sourceRecords()) {
            for (JobConfig.RuleSpec rule : config.mapping.rules) {
                JobConfig.SourceSpec sourceSpec = rule.sources.stream()
                        .filter(spec -> spec.input.contains(record.sourceFileId()) && spec.clazz.equals(record.sourceClass()))
                        .findFirst().orElse(null);
                if (sourceSpec == null) {
                    continue;
                }
                Iom_jObject target = new Iom_jObject(rule.targetClass, Long.toString(stateStore.nextOid()));
                Map<String, IomObject> sources = Map.of(sourceSpec.alias, record.sourceObject());
                for (JobConfig.AttributeMapping attr : rule.attributes) {
                    Object value = expressionEngine.evaluate(attr.expr, sources);
                    if (value != null) {
                        target.setattrvalue(attr.target, value.toString());
                    }
                }
                for (JobConfig.RefMapping ref : rule.refs) {
                    RefCall call = parseRefCall(ref.expr);
                    if (call == null || !sourceSpec.alias.equals(call.alias())) {
                        continue;
                    }
                    String sourceRefOid = readSourceReferenceOid(record.sourceObject(), call.roleName());
                    if (sourceRefOid != null && !sourceRefOid.isBlank()) {
                        stateStore.addDeferredRef(new DeferredRef(
                                rule.targetClass,
                                target.getobjectoid(),
                                ref.target,
                                record.sourceClass(),
                                sourceRefOid,
                                record.sourceFileId(),
                                record.sourceBasketId(),
                                null
                        ));
                    }
                }

                stateStore.putIdMapping(
                        new SourceRefKey(record.sourceClass(), record.sourceObject().getobjectoid(), record.sourceFileId(), record.sourceBasketId()),
                        new TargetRefValue(rule.targetClass, target.getobjectoid(), rule.output, record.sourceBasketId())
                );
                stateStore.indexTargetObject(rule.targetClass, target.getobjectoid(), target);

                String basketKey = basketKey(extractTopic(rule.targetClass), record.sourceBasketId());
                objectsByOutputAndBasket
                        .computeIfAbsent(rule.output, ignored -> new LinkedHashMap<>())
                        .computeIfAbsent(basketKey, ignored -> new ArrayList<>())
                        .add(target);
            }
        }
        return objectsByOutputAndBasket;
    }

    private void writeOutputs(JobConfig config, Map<String, IoxWriter> writersByOutputId, Map<String, Map<String, List<IomObject>>> objectsByOutputAndBasket) throws Exception {
        for (JobConfig.OutputSpec output : config.job.outputs) {
            IoxWriter writer = writersByOutputId.get(output.id);
            writer.write(new ch.interlis.iox_j.StartTransferEvent("ilinexus", null, null));
            Map<String, List<IomObject>> byBasket = objectsByOutputAndBasket.getOrDefault(output.id, Map.of());
            for (Map.Entry<String, List<IomObject>> basketEntry : byBasket.entrySet()) {
                String[] parts = basketEntry.getKey().split("::", 2);
                String topic = parts[0];
                String basketId = parts.length > 1 ? parts[1] : null;
                writer.write(new ch.interlis.iox_j.StartBasketEvent(topic, basketId));
                for (IomObject target : basketEntry.getValue()) {
                    writer.write(new ch.interlis.iox_j.ObjectEvent(target));
                }
                writer.write(new ch.interlis.iox_j.EndBasketEvent());
            }
            writer.write(new ch.interlis.iox_j.EndTransferEvent());
            writer.flush();
            writer.close();
        }
    }

    private void resolveDeferredRefs() {
        for (DeferredRef deferredRef : stateStore.deferredRefs()) {
            List<TargetRefValue> candidates = stateStore.findIdMappings(
                    deferredRef.sourceClass(),
                    deferredRef.sourceReferencedOid(),
                    deferredRef.sourceFileId(),
                    deferredRef.sourceBasketId());
            if (candidates.isEmpty()) {
                diagnostics.add(new Diagnostic("ILIMAP-REF-UNRESOLVED", Severity.WARNING,
                        "Could not resolve reference " + deferredRef.sourceReferencedOid(),
                        deferredRef.ownerTargetClass() + "/" + deferredRef.ownerTargetOid(),
                        "Check source OID / basket routing"));
                continue;
            }
            if (candidates.size() > 1) {
                diagnostics.add(new Diagnostic("ILIMAP-REF-AMBIGUOUS", Severity.ERROR,
                        "Ambiguous reference " + deferredRef.sourceReferencedOid(),
                        deferredRef.ownerTargetClass() + "/" + deferredRef.ownerTargetOid(),
                        "Constrain mapping by file or basket"));
                continue;
            }
            TargetRefValue resolved = candidates.get(0);
            stateStore.findTargetObject(deferredRef.ownerTargetClass(), deferredRef.ownerTargetOid()).ifPresent(owner -> {
                IomObject ref = owner.addattrobj(deferredRef.ownerAttribute(), Iom_jObject.REF);
                ref.setobjectrefoid(resolved.targetOid());
            });
        }
    }

    private String readSourceReferenceOid(IomObject source, String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return null;
        }
        if (source.getattrvaluecount(roleName) > 0) {
            IomObject refObj = source.getattrobj(roleName, 0);
            if (refObj != null && refObj.getobjectrefoid() != null) {
                return refObj.getobjectrefoid();
            }
        }
        return source.getattrvalue(roleName);
    }

    private RefCall parseRefCall(String expr) {
        if (expr == null) {
            return null;
        }
        String trimmed = expr.trim();
        if (!trimmed.startsWith("ref(") || !trimmed.endsWith(")")) {
            return null;
        }
        String argsPart = trimmed.substring(4, trimmed.length() - 1);
        String[] args = argsPart.split(",", 2);
        if (args.length != 2) {
            return null;
        }
        return new RefCall(stripQuotes(args[0].trim()), stripQuotes(args[1].trim()));
    }

    private String stripQuotes(String value) {
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private String extractTopic(String qualifiedClassName) {
        String[] parts = qualifiedClassName.split("\\.");
        if (parts.length >= 2) {
            return parts[0] + "." + parts[1];
        }
        return qualifiedClassName;
    }

    private String basketKey(String topic, String basketId) {
        return topic + "::" + (basketId == null ? "" : basketId);
    }

    private record RefCall(String alias, String roleName) {
    }
}
