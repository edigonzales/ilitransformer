package guru.interlis.transformer.mapping.ilimap.format;

import guru.interlis.transformer.mapping.ilimap.ast.*;

import java.util.List;
import java.util.regex.Pattern;

final class IlimapPrinter {

    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_-]*");

    private final StringBuilder sb = new StringBuilder();
    private final IlimapFormatOptions options;
    private int indentLevel;

    IlimapPrinter(IlimapFormatOptions options) {
        this.options = options;
    }

    String print(IlimapDocument document) {
        printDocument(document);
        String result = sb.toString();
        if (!options.finalNewline() && result.endsWith("\n")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private void printDocument(IlimapDocument document) {
        StringBuilder header = new StringBuilder("mapping v2");
        if (document.name() != null) {
            header.append(' ').append(quoted(document.name()));
        }
        header.append(" {");
        line(header.toString());

        indent(() -> {
            boolean needsBlank = false;

            if (document.job() != null) {
                if (needsBlank) blankLine();
                printJob(document.job());
                needsBlank = true;
            }

            for (IlimapInputBlock input : document.inputs()) {
                if (needsBlank) blankLine();
                printInput(input);
                needsBlank = true;
            }

            for (IlimapOutputBlock output : document.outputs()) {
                if (needsBlank) blankLine();
                printOutput(output);
                needsBlank = true;
            }

            if (document.oid() != null) {
                if (needsBlank) blankLine();
                printOid(document.oid());
                needsBlank = true;
            }

            if (document.basket() != null) {
                if (needsBlank) blankLine();
                printBasket(document.basket());
                needsBlank = true;
            }

            for (IlimapEnumBlock enumBlock : document.enums()) {
                if (needsBlank) blankLine();
                printEnum(enumBlock);
                needsBlank = true;
            }

            if (document.defaults() != null) {
                if (needsBlank) blankLine();
                printDefaultsBlock("defaults", document.defaults());
                needsBlank = true;
            }

            for (IlimapRuleBlock rule : document.rules()) {
                if (needsBlank) blankLine();
                printRule(rule);
                needsBlank = true;
            }
        });

        line("}");
    }

    private void printJob(IlimapJobBlock job) {
        line("job {");
        indent(() -> {
            if (job.name() != null) {
                line("name " + quoted(job.name()) + ";");
            }
            if (job.description() != null) {
                line("description " + quoted(job.description()) + ";");
            }
            if (job.direction() != null) {
                line("direction " + identifierOrQuoted(job.direction()) + ";");
            }
            if (job.failPolicy() != null) {
                line("failPolicy " + identifierOrQuoted(job.failPolicy()) + ";");
            }
            if (job.compileMode() != null) {
                line("compileMode " + identifierOrQuoted(job.compileMode()) + ";");
            }
            for (String modeldir : job.modeldirs()) {
                line("modeldir " + quoted(modeldir) + ";");
            }
        });
        line("}");
    }

    private void printInput(IlimapInputBlock input) {
        line("input " + input.id() + " {");
        indent(() -> {
            if (input.path() != null) {
                line("path " + quoted(input.path()) + ";");
            }
            if (input.model() != null) {
                line("model " + quoted(input.model()) + ";");
            }
            if (input.format() != null) {
                line("format " + identifierOrQuoted(input.format()) + ";");
            }
        });
        line("}");
    }

    private void printOutput(IlimapOutputBlock output) {
        line("output " + output.id() + " {");
        indent(() -> {
            if (output.path() != null) {
                line("path " + quoted(output.path()) + ";");
            }
            if (output.model() != null) {
                line("model " + quoted(output.model()) + ";");
            }
            if (output.format() != null) {
                line("format " + identifierOrQuoted(output.format()) + ";");
            }
        });
        line("}");
    }

    private void printOid(IlimapOidBlock oid) {
        line("oid {");
        indent(() -> {
            if (oid.strategy() != null) {
                line("strategy " + identifierOrQuoted(oid.strategy()) + ";");
            }
            if (oid.namespace() != null) {
                line("namespace " + quoted(oid.namespace()) + ";");
            }
        });
        line("}");
    }

    private void printBasket(IlimapBasketStmt basket) {
        line("basket " + identifierOrQuoted(basket.strategy()) + ";");
    }

    private void printEnum(IlimapEnumBlock enumBlock) {
        line("enum " + enumBlock.id() + " {");
        indent(() -> {
            for (IlimapEnumEntry entry : enumBlock.entries()) {
                line(formatLiteral(entry.source()) + " => " + formatLiteral(entry.target()) + ";");
            }
        });
        line("}");
    }

    private void printDefaultsBlock(String keyword, IlimapDefaultsBlock defaults) {
        line(keyword + " {");
        indent(() -> printAssignments(defaults.assignments()));
        line("}");
    }

    private void printRule(IlimapRuleBlock rule) {
        line("rule " + rule.id() + " {");
        indent(() -> {
            boolean needsBlank = false;
            for (IlimapRuleElement element : rule.elements()) {
                if (needsBlank && isBlockElement(element)) {
                    blankLine();
                }
                printRuleElement(element);
                needsBlank = true;
            }
        });
        line("}");
    }

    private boolean isBlockElement(IlimapRuleElement element) {
        return element instanceof IlimapAssignmentBlock
                || element instanceof IlimapDefaultsBlock
                || element instanceof IlimapBagBlock
                || element instanceof IlimapRefBlock
                || element instanceof IlimapCreateBlock
                || element instanceof IlimapLossBlock
                || element instanceof IlimapMetadataBlock;
    }

    private void printRuleElement(IlimapRuleElement element) {
        switch (element) {
            case IlimapTargetStmt target -> printTarget(target);
            case IlimapSourceStmt source -> printSource(source);
            case IlimapWhereStmt where -> printWhere(where);
            case IlimapJoinStmt join -> printJoin(join);
            case IlimapIdentityStmt identity -> printIdentity(identity);
            case IlimapAssignmentBlock assign -> printAssignBlock(assign);
            case IlimapDefaultsBlock defaults -> printDefaultsBlock("defaults", defaults);
            case IlimapBagBlock bag -> printBag(bag);
            case IlimapRefBlock ref -> printRef(ref);
            case IlimapCreateBlock create -> printCreate(create);
            case IlimapLossBlock loss -> printLoss(loss);
            case IlimapMetadataBlock metadata -> printMetadata(metadata);
        }
    }

    private void printTarget(IlimapTargetStmt target) {
        line("target " + target.outputId() + " class " + quoted(target.targetClass()) + ";");
    }

    private void printSource(IlimapSourceStmt source) {
        StringBuilder sb = new StringBuilder("source ");
        sb.append(source.alias()).append(" from ");
        sb.append(String.join(", ", source.inputIds()));
        sb.append(" class ").append(quoted(source.sourceClass()));
        if (source.where() != null) {
            sb.append(" where ").append(source.where().text().strip());
        }
        sb.append(';');
        line(sb.toString());
    }

    private void printWhere(IlimapWhereStmt where) {
        line("where " + where.expression().text().strip() + ";");
    }

    private void printJoin(IlimapJoinStmt join) {
        line("join " + join.joinType() + " " + join.leftAlias()
                + " to " + join.rightAlias() + " on " + join.on().text().strip() + ";");
    }

    private void printIdentity(IlimapIdentityStmt identity) {
        List<String> parts = identity.expressions().stream()
                .map(e -> e.text().strip())
                .toList();
        line("identity " + String.join(", ", parts) + ";");
    }

    private void printAssignBlock(IlimapAssignmentBlock assign) {
        line("assign {");
        indent(() -> printAssignments(assign.assignments()));
        line("}");
    }

    private void printAssignments(List<IlimapAssignment> assignments) {
        for (IlimapAssignment assignment : assignments) {
            line(assignment.targetAttribute() + " = " + assignment.expression().text().strip() + ";");
        }
    }

    private void printBag(IlimapBagBlock bag) {
        line("bag " + bag.id() + " {");
        indent(() -> {
            if (bag.from() != null) {
                printBagFrom(bag.from());
            }
            if (bag.structure() != null) {
                line("structure " + quoted(bag.structure()) + ";");
            }
            if (bag.mode() != null) {
                line("mode " + bag.mode() + ";");
            }
            if (bag.maxItems() != null) {
                line("maxItems " + bag.maxItems() + ";");
            }
            if (bag.parentRef() != null) {
                printParentRef(bag.parentRef());
            }
            if (bag.assign() != null) {
                printAssignBlock(bag.assign());
            }
            for (IlimapBagBlock nested : bag.nestedBags()) {
                printBag(nested);
            }
        });
        line("}");
    }

    private void printBagFrom(IlimapBagFromStmt from) {
        StringBuilder sb = new StringBuilder("from ");
        sb.append(from.alias()).append(" in ").append(from.inputId());
        sb.append(" class ").append(quoted(from.sourceClass()));
        if (from.where() != null) {
            sb.append(" where ").append(from.where().text().strip());
        }
        sb.append(';');
        line(sb.toString());
    }

    private void printParentRef(IlimapParentRefStmt parentRef) {
        line("parentRef " + parentRef.kind() + " " + quoted(parentRef.name())
                + " parent " + parentRef.parentAlias() + ";");
    }

    private void printRef(IlimapRefBlock ref) {
        line("ref " + ref.id() + " {");
        indent(() -> {
            if (ref.association() != null) {
                line("association " + quoted(ref.association()) + ";");
            }
            if (ref.role() != null) {
                line("role " + quoted(ref.role()) + ";");
            }
            if (ref.required()) {
                line("required;");
            }
            if (ref.targetRuleId() != null && ref.sourceRef() != null) {
                line("target rule " + ref.targetRuleId()
                        + " sourceRef " + ref.sourceRef().text().strip() + ";");
            }
        });
        line("}");
    }

    private void printCreate(IlimapCreateBlock create) {
        line("create class " + quoted(create.targetClass()) + " {");
        indent(() -> {
            if (create.assign() != null) {
                printAssignBlock(create.assign());
            }
        });
        line("}");
    }

    private void printLoss(IlimapLossBlock loss) {
        line("loss {");
        indent(() -> {
            if (loss.sourcePath() != null) {
                line("sourcePath " + loss.sourcePath().text().strip() + ";");
            }
            if (loss.reasonCode() != null) {
                line("reasonCode " + quoted(loss.reasonCode()) + ";");
            }
            if (loss.description() != null) {
                line("description " + quoted(loss.description()) + ";");
            }
            if (loss.when() != null) {
                line("when " + loss.when().text().strip() + ";");
            }
        });
        line("}");
    }

    private void printMetadata(IlimapMetadataBlock metadata) {
        line("metadata {");
        indent(() -> {
            if (metadata.direction() != null) {
                line("direction " + identifierOrQuoted(metadata.direction()) + ";");
            }
            if (metadata.roundtrip() != null) {
                line("roundtrip " + identifierOrQuoted(metadata.roundtrip()) + ";");
            }
            if (metadata.lossiness() != null) {
                line("lossiness " + identifierOrQuoted(metadata.lossiness()) + ";");
            }
        });
        line("}");
    }

    private String formatLiteral(IlimapLiteral literal) {
        return switch (literal) {
            case IlimapLiteral.StringLit s -> quoted(s.value());
            case IlimapLiteral.BooleanLit b -> Boolean.toString(b.value());
            case IlimapLiteral.NumberLit n -> n.text();
            case IlimapLiteral.NullLit ignored -> "null";
            case IlimapLiteral.HashLit h -> h.value();
        };
    }

    private void line(String text) {
        for (int i = 0; i < indentLevel * options.indentSize(); i++) {
            sb.append(' ');
        }
        sb.append(text).append('\n');
    }

    private void blankLine() {
        sb.append('\n');
    }

    private void indent(Runnable block) {
        indentLevel++;
        block.run();
        indentLevel--;
    }

    static String quoted(String value) {
        return "\"" + escapeString(value) + "\"";
    }

    static String escapeString(String value) {
        StringBuilder sb = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    static String identifierOrQuoted(String value) {
        if (value != null && IDENTIFIER_PATTERN.matcher(value).matches()) {
            return value;
        }
        return quoted(value);
    }
}
