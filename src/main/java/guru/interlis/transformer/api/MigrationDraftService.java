package guru.interlis.transformer.api;

import guru.interlis.transformer.interlis.InterlisModelLoader;
import guru.interlis.transformer.mapping.ilimap.editor.IlimapEditorDocument;
import guru.interlis.transformer.model.TypeSystemFacade;

import ch.interlis.ili2c.metamodel.*;

import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Model comparison and deterministic explicit mapping proposals, shared by CLI and GUI hosts. */
public final class MigrationDraftService {
    public enum Status {
        COMPATIBLE,
        CHECK_DATA,
        DECISION,
        NOT_MAPPED
    }

    public record Field(
            String name,
            String type,
            boolean required,
            String structureType,
            String referenceTarget,
            List<String> enumValues) {}

    public record Entity(String name, boolean structure, List<Field> fields) {}

    public record Schema(String model, String fingerprint, List<Entity> entities, TypeSystemFacade types) {
        public Entity entity(String name) {
            return entities.stream()
                    .filter(e -> e.name().equals(name))
                    .findFirst()
                    .orElseThrow();
        }
    }

    public record Difference(String sourceClass, String targetClass, String attribute, Status status, String detail) {}

    public record Draft(String text, Schema source, Schema target, List<Difference> differences) {}

    public Schema inspect(String model, String directories) throws Exception {
        TransferDescription td = new InterlisModelLoader().compileModel(model, directories);
        if (td == null) throw new IllegalArgumentException("Cannot compile model: " + model);
        return schema(model, td);
    }

    public Schema schema(String model, TransferDescription td) throws Exception {
        var types = new TypeSystemFacade(td);
        List<Entity> entities = new ArrayList<>();
        for (var inventory : List.of(new guru.interlis.transformer.model.IliModelService().buildInventory(td, model))) {
            if (!inventory.modelName().equals(model)) continue;
            for (var topic : inventory.topics())
                for (var entity : topic.classes()) {
                    Table table = types.resolveClass(entity.path());
                    if (table == null || entity.isAbstract()) continue;
                    List<Field> fields = new ArrayList<>();
                    for (var attribute : entity.attributes()) {
                        AttributeDef attr = types.findAttribute(table, attribute.name());
                        if (attr == null) continue;
                        Type type = attr.getDomainResolvingAliases();
                        String structure = type instanceof CompositionType c
                                ? TypeSystemFacade.getScopedName(c.getComponentType())
                                : null;
                        fields.add(new Field(
                                attribute.name(),
                                attribute.typeString(),
                                attribute.mandatory(),
                                structure,
                                null,
                                type instanceof EnumerationType en ? List.copyOf(en.getValues()) : List.of()));
                    }
                    var transferElements = table.getAttributesAndRoles2();
                    while (transferElements.hasNext()) {
                        var transferElement = transferElements.next();
                        if (transferElement.obj instanceof RoleDef role) {
                            var reference = types.resolveReference(entity.path(), role.getName());
                            if (reference != null)
                                fields.add(new Field(
                                        role.getName(),
                                        "REFERENCE",
                                        reference.minCardinality() > 0,
                                        null,
                                        reference.targetClass(),
                                        List.of()));
                        }
                    }
                    entities.add(new Entity(entity.path(), !table.isIdentifiable(), List.copyOf(fields)));
                }
        }
        // Model-level (rather than topic-level) structures are absent from the class inventory.
        // Include every referenced structure so the editor uses exactly the runtime's types.
        Set<String> known = new HashSet<>();
        entities.forEach(e -> known.add(e.name()));
        for (int i = 0; i < entities.size(); i++) {
            for (Field field : entities.get(i).fields()) {
                if (field.structureType() != null && known.add(field.structureType())) {
                    entities.add(structureEntity(new Schema(model, "", entities, types), field.structureType()));
                }
            }
        }
        entities.sort(Comparator.comparing(Entity::name));
        return new Schema(model, fingerprint(td), List.copyOf(entities), types);
    }

    public Draft create(String sourceModel, String targetModel, String dirs, String input, String output)
            throws Exception {
        Schema source = inspect(sourceModel, dirs), target = inspect(targetModel, dirs);
        List<Difference> differences = new ArrayList<>();
        StringBuilder text = new StringBuilder("mapping v2 \"Model migration\" {\n  job {\n");
        for (String directory : dirs.split(";"))
            if (!directory.isBlank())
                text.append("    modeldir ").append(q(directory)).append(";\n");
        text.append("  }\n  input original { path ")
                .append(q(input))
                .append("; model ")
                .append(q(sourceModel))
                .append("; option migrationReviewed false; option modelFingerprint ")
                .append(q(source.fingerprint()))
                .append("; }\n  output migrated { path ")
                .append(q(output))
                .append("; model ")
                .append(q(targetModel))
                .append("; option modelFingerprint ")
                .append(q(target.fingerprint()))
                .append("; }\n  oid { strategy preserve; }\n  basket preserve;\n");
        Set<String> mappedTargets = new HashSet<>();
        for (Entity entity : source.entities()) {
            if (entity.structure()) continue;
            String targetName = targetModel + entity.name().substring(sourceModel.length());
            Entity other = target.entities().stream()
                    .filter(e -> e.name().equals(targetName) && !e.structure())
                    .findFirst()
                    .orElse(null);
            if (other == null) {
                differences.add(new Difference(
                        entity.name(),
                        "",
                        "",
                        Status.NOT_MAPPED,
                        "Choose a target class or explicitly record the loss"));
                text.append("  // Unmapped source class: ")
                        .append(entity.name())
                        .append('\n');
                continue;
            }
            mappedTargets.add(other.name());
            text.append("  rule ")
                    .append(ruleId(entity.name()))
                    .append(" {\n    target migrated class ")
                    .append(q(other.name()))
                    .append(";\n    source s from original class ")
                    .append(q(entity.name()))
                    .append(";\n");
            appendFields(text, source, target, entity, other, "s", "    ", differences, new HashSet<>());
            text.append("  }\n");
        }
        for (Entity entity : target.entities())
            if (!entity.structure() && !mappedTargets.contains(entity.name()))
                differences.add(
                        new Difference("", entity.name(), "", Status.DECISION, "Target class has no source rule"));
        for (Difference difference : differences) {
            if (difference.status() == Status.DECISION || difference.status() == Status.NOT_MAPPED)
                text.append("  // REVIEW ")
                        .append(difference.sourceClass())
                        .append(".")
                        .append(difference.attribute())
                        .append(": ")
                        .append(difference.detail())
                        .append("\n");
        }
        text.append("}\n");
        return new Draft(text.toString(), source, target, List.copyOf(differences));
    }

    private void appendFields(
            StringBuilder text,
            Schema source,
            Schema target,
            Entity from,
            Entity to,
            String alias,
            String indent,
            List<Difference> differences,
            Set<String> active) {
        String pair = from.name() + "->" + to.name();
        if (!active.add(pair))
            throw new IllegalArgumentException("Recursive structures require explicit mapping: " + pair);
        StringBuilder assignments = new StringBuilder(), structures = new StringBuilder();
        Set<String> used = new HashSet<>();
        for (Field field : to.fields()) {
            Field original = from.fields().stream()
                    .filter(f -> f.name().equals(field.name()))
                    .findFirst()
                    .orElse(null);
            if (original == null) {
                differences.add(new Difference(
                        from.name(),
                        to.name(),
                        field.name(),
                        field.required() ? Status.DECISION : Status.COMPATIBLE,
                        field.required()
                                ? "New mandatory attribute needs a value"
                                : "New optional attribute remains undefined"));
                continue;
            }
            used.add(original.name());
            if (field.structureType() != null && original.structureType() != null) {
                var fromStructure = structureEntity(source, original.structureType());
                var toStructure = structureEntity(target, field.structureType());
                String childAlias = alias + "c";
                structures
                        .append(indent)
                        .append("bag ")
                        .append(field.name())
                        .append(" {\n")
                        .append(indent)
                        .append("  from ")
                        .append(childAlias)
                        .append(" in ")
                        .append(alias)
                        .append(" attribute ")
                        .append(q(original.name()))
                        .append(";\n");
                appendFields(
                        structures,
                        source,
                        target,
                        fromStructure,
                        toStructure,
                        childAlias,
                        indent + "  ",
                        differences,
                        active);
                structures.append(indent).append("}\n");
                continue;
            }
            if (field.referenceTarget() != null && original.referenceTarget() != null) {
                String expected = original.referenceTarget().startsWith(source.model() + ".")
                        ? target.model()
                                + original.referenceTarget()
                                        .substring(source.model().length())
                        : original.referenceTarget();
                if (field.referenceTarget().equals(expected)
                        && target.entities().stream().anyMatch(e -> e.name().equals(expected))) {
                    structures
                            .append(indent)
                            .append("ref ")
                            .append(field.name())
                            .append(" { role ")
                            .append(q(field.name()))
                            .append("; target rule ")
                            .append(ruleId(original.referenceTarget()))
                            .append(" sourceRef ")
                            .append(alias)
                            .append('.')
                            .append(original.name())
                            .append("; }\n");
                    differences.add(new Difference(
                            from.name(),
                            to.name(),
                            field.name(),
                            Status.COMPATIBLE,
                            "Reference resolves through target rule"));
                } else
                    differences.add(new Difference(
                            from.name(), to.name(), field.name(), Status.DECISION, "Choose referenced target rule"));
                continue;
            }
            Status status = compatibility(source, target, from.name(), to.name(), field.name());
            differences.add(new Difference(
                    from.name(),
                    to.name(),
                    field.name(),
                    status,
                    status == Status.COMPATIBLE
                            ? "Compatible copy"
                            : status == Status.CHECK_DATA
                                    ? "Target constraints require data validation"
                                    : "Explicit conversion or mapping required"));
            if (status == Status.COMPATIBLE || status == Status.CHECK_DATA)
                assignments
                        .append(indent)
                        .append("  ")
                        .append(field.name())
                        .append(" = ")
                        .append(alias)
                        .append('.')
                        .append(original.name())
                        .append(";\n");
        }
        for (Field field : from.fields())
            if (!used.contains(field.name()))
                differences.add(new Difference(
                        from.name(), to.name(), field.name(), Status.NOT_MAPPED, "Source attribute not mapped"));
        if (!assignments.isEmpty())
            text.append(indent)
                    .append("assign {\n")
                    .append(assignments)
                    .append(indent)
                    .append("}\n");
        text.append(structures);
        active.remove(pair);
    }

    private Entity structureEntity(Schema schema, String name) {
        return schema.entities().stream()
                .filter(e -> e.name().equals(name))
                .findFirst()
                .orElseGet(() -> {
                    Table table = schema.types().resolveClass(name);
                    if (table == null) throw new IllegalArgumentException("Unknown structure: " + name);
                    List<Field> fields = new ArrayList<>();
                    var iterator = table.getAttributes();
                    while (iterator.hasNext()) {
                        var element = iterator.next();
                        if (!(element instanceof AttributeDef attr)) continue;
                        Type type = attr.getDomainResolvingAliases();
                        fields.add(new Field(
                                attr.getName(),
                                type.getClass().getSimpleName(),
                                type.isMandatory(),
                                type instanceof CompositionType c
                                        ? TypeSystemFacade.getScopedName(c.getComponentType())
                                        : null,
                                null,
                                type instanceof EnumerationType en ? List.copyOf(en.getValues()) : List.of()));
                    }
                    return new Entity(name, true, List.copyOf(fields));
                });
    }

    public Status compatibility(Schema source, Schema target, String fromClass, String toClass, String attribute) {
        return compatibility(source, target, fromClass, toClass, attribute, attribute);
    }

    public Status compatibility(
            Schema source,
            Schema target,
            String fromClass,
            String toClass,
            String sourceAttribute,
            String targetAttribute) {
        AttributeDef left = source.types().findAttribute(source.types().resolveClass(fromClass), sourceAttribute);
        AttributeDef right = target.types().findAttribute(target.types().resolveClass(toClass), targetAttribute);
        if (left == null || right == null) return Status.DECISION;
        Type a = left.getDomainResolvingAliases(), b = right.getDomainResolvingAliases();
        if (a.getClass() != b.getClass()) return Status.DECISION;
        if (a.getCardinality().getMaximum() > 1 || b.getCardinality().getMaximum() > 1) return Status.DECISION;
        if (a instanceof ReferenceType) return Status.DECISION;
        if (a instanceof EnumerationType x
                && b instanceof EnumerationType y
                && !y.getValues().containsAll(x.getValues())) return Status.DECISION;
        if (a instanceof NumericType x && b instanceof NumericType y && !java.util.Objects.equals(unit(x), unit(y)))
            return Status.DECISION;
        if (a instanceof TextType x
                && b instanceof TextType y
                && x.getMaxLength() <= y.getMaxLength()
                && x.isNormalized() == y.isNormalized()
                && (!b.isMandatory() || a.isMandatory())) return Status.COMPATIBLE;
        if (a instanceof AbstractCoordType x
                && b instanceof AbstractCoordType y
                && x.getDimensions().length != y.getDimensions().length) return Status.DECISION;
        return Status.CHECK_DATA;
    }

    private static String unit(NumericType type) {
        return type.getUnit() == null ? "" : type.getUnit().getScopedName(null);
    }

    public static String ruleId(String sourceClass) {
        return "r_"
                + UUID.nameUUIDFromBytes(sourceClass.getBytes(java.nio.charset.StandardCharsets.UTF_8))
                        .toString()
                        .replace('-', '_');
    }

    public static String fingerprint(TransferDescription td) throws Exception {
        var digest = MessageDigest.getInstance("SHA-256");
        SortedMap<String, String> files = new TreeMap<>();
        var iterator = td.iterator();
        while (iterator.hasNext()) {
            Model model = iterator.next();
            if (model.getFileName() != null) files.put(model.getName(), model.getFileName());
        }
        for (var entry : files.entrySet()) {
            String file = entry.getValue();
            Path path = Path.of(file);
            if (!Files.isRegularFile(path))
                throw new IllegalArgumentException("Model source unavailable for fingerprint: " + file);
            // Names determine order; moving the same model files must not invalidate a mapping.
            digest.update(entry.getKey().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(Files.readAllBytes(path));
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String q(String value) {
        return IlimapEditorDocument.quote(value);
    }
}
