package guru.interlis.transformer.api;

import guru.interlis.transformer.interlis.InterlisModelLoader;
import guru.interlis.transformer.mapping.ilimap.editor.IlimapEditorDocument;
import guru.interlis.transformer.model.IliModelService;
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
        for (Iterator<Model> models = td.iterator(); models.hasNext(); ) {
            Model definition = models.next();
            if (!definition.getName().equals(model)) continue;
            for (Iterator<Element> topics = definition.iterator(); topics.hasNext(); ) {
                if (!(topics.next() instanceof Topic topic)) continue;
                for (Iterator<Element> members = topic.iterator(); members.hasNext(); ) {
                    Element member = members.next();
                    if (member instanceof Table table
                            && !table.isImplicit()
                            && !table.isIli1LineAttrStruct()
                            && !table.isAbstract()) {
                        entities.add(entity(types, table, !table.isIdentifiable()));
                    } else if (member instanceof AssociationDef association
                            && !association.isAbstract()
                            && !association.isLightweight()) {
                        entities.add(entity(types, association, false));
                    }
                }
            }
        }
        // Add referenced structures declared at model level.
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
                    .filter(e -> e.name().equals(targetName))
                    .findFirst()
                    .orElse(null);
            if (other == null) {
                differences.add(new Difference(
                        entity.name(), "", "", Status.NOT_MAPPED, "Add a mapping rule or an explicit class loss rule"));
                continue;
            }
            if (other.structure()) {
                differences.add(new Difference(
                        entity.name(),
                        other.name(),
                        "",
                        Status.DECISION,
                        "The matching target name is a structure. Choose a target class explicitly"));
                continue;
            }
            var sourceClass = source.types().resolveClass(entity.name());
            var targetClass = target.types().resolveClass(other.name());
            if (sourceClass instanceof AssociationDef || targetClass instanceof AssociationDef) {
                if (!(sourceClass instanceof AssociationDef from
                        && targetClass instanceof AssociationDef to
                        && matchingAssociation(source, target, entity, other, from, to))) {
                    differences.add(new Difference(
                            entity.name(),
                            other.name(),
                            "",
                            Status.DECISION,
                            "Map the standalone association and both roles explicitly"));
                    continue;
                }
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
            if (difference.status() == Status.DECISION || difference.status() == Status.NOT_MAPPED) {
                text.append("  // REVIEW ")
                        .append(
                                difference.sourceClass().isEmpty()
                                        ? difference.targetClass()
                                        : difference.sourceClass());
                if (!difference.attribute().isEmpty()) text.append('.').append(difference.attribute());
                text.append(": ").append(difference.detail()).append("\n");
            }
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
                        field.required() || field.referenceTarget() != null ? Status.DECISION : Status.COMPATIBLE,
                        field.required() || field.referenceTarget() != null
                                ? "New field needs an explicit mapping decision"
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
            if (field.referenceTarget() != null || original.referenceTarget() != null) {
                if (matchingReference(source, target, from, to, original, field)) {
                    structures
                            .append(indent)
                            .append("ref ")
                            .append(field.name())
                            .append(" { role ")
                            .append(q(field.name()))
                            .append(field.required() ? "; required; target rule " : "; target rule ")
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
                        from.name(),
                        to.name(),
                        field.name(),
                        Status.NOT_MAPPED,
                        "Map this source attribute or declare a loss"));
        if (!assignments.isEmpty())
            text.append(indent)
                    .append("assign {\n")
                    .append(assignments)
                    .append(indent)
                    .append("}\n");
        text.append(structures);
        active.remove(pair);
    }

    private static boolean matchingAssociation(
            Schema source,
            Schema target,
            Entity from,
            Entity to,
            AssociationDef sourceClass,
            AssociationDef targetClass) {
        if (TypeSystemFacade.unsupportedAssociation(sourceClass) != null
                || TypeSystemFacade.unsupportedAssociation(targetClass) != null
                || TypeSystemFacade.hasAssociationOid(sourceClass) != TypeSystemFacade.hasAssociationOid(targetClass)) {
            return false;
        }
        for (RoleDef role : targetClass.getRoles()) {
            if (sourceClass.findRole(role.getName()) == null) return false;
            Field original = from.fields().stream()
                    .filter(f -> f.name().equals(role.getName()))
                    .findFirst()
                    .orElseThrow();
            Field field = to.fields().stream()
                    .filter(f -> f.name().equals(role.getName()))
                    .findFirst()
                    .orElseThrow();
            if (!matchingReference(source, target, from, to, original, field)) return false;
        }
        return true;
    }

    private static boolean matchingReference(
            Schema source, Schema target, Entity from, Entity to, Field original, Field field) {
        if (original.referenceTarget() == null
                || !original.referenceTarget().startsWith(source.model() + ".")
                || field.referenceTarget() == null
                || field.required() && !original.required()) return false;
        String expected = target.model()
                + original.referenceTarget().substring(source.model().length());
        if (!field.referenceTarget().equals(expected)
                || !source.entities().stream()
                        .anyMatch(e -> e.name().equals(original.referenceTarget()) && !e.structure())
                || !target.entities().stream().anyMatch(e -> e.name().equals(expected) && !e.structure())) return false;
        var originalRef = source.types().resolveReference(from.name(), original.name());
        var targetRef = target.types().resolveReference(to.name(), field.name());
        return originalRef.external() == targetRef.external()
                && originalRef.attributeReference() == targetRef.attributeReference();
    }

    private static Entity entity(TypeSystemFacade types, AbstractClassDef<?> definition, boolean structure) {
        String path = TypeSystemFacade.getScopedName(definition);
        List<Field> fields = new ArrayList<>();
        var attributes = definition.getAttributes();
        while (attributes.hasNext()) {
            if (!(attributes.next() instanceof AttributeDef attr)) continue;
            Type type = attr.getDomainResolvingAliases();
            var reference =
                    !structure && type instanceof ReferenceType ? types.resolveReference(path, attr.getName()) : null;
            String label = definition.getContainer() instanceof Model
                    ? type.getClass().getSimpleName()
                    : IliModelService.formatTypeString(attr.getDomain());
            var cardinality = attr.getCardinality();
            fields.add(new Field(
                    attr.getName(),
                    reference != null ? "REFERENCE" : label,
                    cardinality != null && cardinality.getMinimum() > 0,
                    type instanceof CompositionType c ? TypeSystemFacade.getScopedName(c.getComponentType()) : null,
                    reference != null ? reference.targetClass() : null,
                    type instanceof EnumerationType en ? List.copyOf(en.getValues()) : List.of()));
        }
        if (!structure) {
            var elements = definition.getAttributesAndRoles2();
            while (elements.hasNext()) {
                if (elements.next().obj instanceof RoleDef role
                        && (!(definition instanceof AssociationDef association)
                                || association.findRole(role.getName()) != null)) {
                    var reference = types.resolveReference(path, role.getName());
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
        }
        return new Entity(path, structure, List.copyOf(fields));
    }

    private Entity structureEntity(Schema schema, String name) {
        return schema.entities().stream()
                .filter(e -> e.name().equals(name))
                .findFirst()
                .orElseGet(() -> {
                    if (!(schema.types().resolveClass(name) instanceof Table table))
                        throw new IllegalArgumentException("Unknown structure: " + name);
                    return entity(schema.types(), table, true);
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
