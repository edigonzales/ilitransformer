package guru.interlis.transformer.mapping.ilimap.ide;

import guru.interlis.transformer.mapping.ilimap.ast.IlimapAssignment;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapAssignmentBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapAstNode;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapBagBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapDefaultsBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapDocument;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapEnumBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapInputBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapLossBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapOutputBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRefBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRuleBlock;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapRuleElement;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapSourceStmt;
import guru.interlis.transformer.mapping.ilimap.ast.IlimapTargetStmt;
import guru.interlis.transformer.mapping.ilimap.lexer.IlimapSourceRange;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IlimapNavigationService {

    private static final Pattern ALIAS_MEMBER_PATTERN = Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_-]*)\\.([a-zA-Z_][a-zA-Z0-9_-]*)");

    private final IlimapPositionResolver positionResolver;

    public IlimapNavigationService() {
        this(new IlimapPositionResolver());
    }

    IlimapNavigationService(IlimapPositionResolver positionResolver) {
        this.positionResolver = Objects.requireNonNull(positionResolver, "positionResolver");
    }

    public Optional<IlimapNavigationNode> nodeAtPosition(IlimapAnalysis analysis, IlimapIdePosition position) {
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(position, "position");

        if (!analysis.hasDocument()) {
            return Optional.empty();
        }

        Optional<IlimapAstNode> smallestNode = positionResolver.smallestNodeAt(analysis, position);
        if (smallestNode.isEmpty()) {
            return Optional.empty();
        }

        IlimapAstNode node = smallestNode.get();
        int offset = analysis.lineMap().positionToOffset(position.line(), position.character());

        if (node instanceof IlimapInputBlock input) {
            return Optional.of(buildNode(IlimapOverviewNodeIds.input(input.id()), "input",
                    "input " + input.id(), null, toOverviewLocation(analysis, input.range())));
        }
        if (node instanceof IlimapOutputBlock output) {
            return Optional.of(buildNode(IlimapOverviewNodeIds.output(output.id()), "output",
                    "output " + output.id(), null, toOverviewLocation(analysis, output.range())));
        }
        if (node instanceof IlimapEnumBlock enumBlock) {
            return Optional.of(buildNode(IlimapOverviewNodeIds.enumMap(enumBlock.id()), "enum",
                    "enum " + enumBlock.id(), null, toOverviewLocation(analysis, enumBlock.range())));
        }
        if (node instanceof IlimapRuleBlock rule) {
            return Optional.of(buildNode(IlimapOverviewNodeIds.rule(rule.id()), "rule",
                    "rule " + rule.id(), null, toOverviewLocation(analysis, rule.range())));
        }

        String parentRuleId = findParentRuleId(analysis.document(), node);
        if (parentRuleId == null) {
            return Optional.empty();
        }

        return nodeInRule(analysis, node, parentRuleId, offset);
    }

    public IlimapNavigationTarget targetForNodeId(IlimapAnalysis analysis, String nodeId) {
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(nodeId, "nodeId");

        if (!analysis.hasDocument() || nodeId.isBlank()) {
            return IlimapNavigationTarget.unavailable(nodeId, "No analysis available or empty nodeId.");
        }

        IlimapOverviewLocation location = resolveToLocation(analysis, nodeId);
        if (location != null) {
            return new IlimapNavigationTarget(true, "", nodeId, location, List.of());
        }
        return IlimapNavigationTarget.unavailable(nodeId, "Unknown node: " + nodeId);
    }

    public List<IlimapNavigationNode> relatedNodes(IlimapAnalysis analysis, String nodeId) {
        Objects.requireNonNull(analysis, "analysis");
        Objects.requireNonNull(nodeId, "nodeId");

        if (!analysis.hasDocument()) {
            return List.of();
        }
        return List.of();
    }

    private Optional<IlimapNavigationNode> nodeInRule(IlimapAnalysis analysis, IlimapAstNode node,
                                                       String parentRuleId, int offset) {
        if (node instanceof IlimapTargetStmt target) {
            return Optional.of(buildNode(
                    IlimapOverviewNodeIds.ruleTarget(parentRuleId), "target",
                    "target " + target.outputId(), null,
                    toOverviewLocation(analysis, target.range())));
        }
        if (node instanceof IlimapSourceStmt source) {
            return Optional.of(buildNode(
                    IlimapOverviewNodeIds.ruleSource(parentRuleId, source.alias()), "source",
                    "source " + source.alias(), source.inputIds() + " -> " + source.sourceClass(),
                    toOverviewLocation(analysis, source.range())));
        }
        if (node instanceof IlimapAssignment assignment) {
            return assignmentNode(analysis, assignment, parentRuleId, offset);
        }
        if (node instanceof IlimapRefBlock ref) {
            return Optional.of(buildNode(
                    IlimapOverviewNodeIds.ruleRef(parentRuleId, ref.id()), "ref",
                    "ref " + ref.id(), ref.targetRuleId() != null ? "-> rule " + ref.targetRuleId() : null,
                    toOverviewLocation(analysis, ref.range())));
        }
        if (node instanceof IlimapBagBlock bag) {
            return Optional.of(buildNode(
                    IlimapOverviewNodeIds.ruleBag(parentRuleId, bag.id()), "bag",
                    "bag " + bag.id(), null, toOverviewLocation(analysis, bag.range())));
        }
        if (node instanceof IlimapLossBlock loss) {
            int lossIdx = lossIndex(analysis.document(), parentRuleId, loss);
            return Optional.of(buildNode(
                    IlimapOverviewNodeIds.ruleLoss(parentRuleId, lossIdx), "loss",
                    "loss", null, toOverviewLocation(analysis, loss.range())));
        }
        return Optional.empty();
    }

    private Optional<IlimapNavigationNode> assignmentNode(IlimapAnalysis analysis, IlimapAssignment assignment,
                                                           String parentRuleId, int offset) {
        int exprStart = assignment.expression().range().start().offset();
        if (offset < exprStart) {
            return Optional.of(buildNode(
                    IlimapOverviewNodeIds.ruleAssignment(parentRuleId, assignment.targetAttribute()),
                    "assignment", assignment.targetAttribute(), null,
                    toOverviewLocation(analysis, assignment.range())));
        }

        String exprText = analysis.text().substring(exprStart, assignment.expression().range().end().offset());
        String identifier = identifierAtOffset(analysis, offset);
        if (identifier == null) {
            return Optional.empty();
        }

        Matcher matcher = ALIAS_MEMBER_PATTERN.matcher(exprText);
        while (matcher.find()) {
            if (matcher.group(1).equals(identifier) || matcher.group(2).equals(identifier)) {
                return Optional.of(buildNode(
                        IlimapOverviewNodeIds.ruleSourceMember(parentRuleId, matcher.group(1), matcher.group(2)),
                        "sourceMember", matcher.group(1) + "." + matcher.group(2),
                        assignment.targetAttribute(), toOverviewLocation(analysis, assignment.range())));
            }
        }
        return Optional.empty();
    }

    private IlimapOverviewLocation resolveToLocation(IlimapAnalysis analysis, String nodeId) {
        IlimapDocument document = analysis.document();
        if (document == null) {
            return null;
        }

        if (nodeId.startsWith("input:")) {
            String id = nodeId.substring("input:".length());
            return document.inputs().stream()
                    .filter(i -> i.id().equals(id))
                    .map(i -> toOverviewLocation(analysis, i.range()))
                    .findFirst().orElse(null);
        }
        if (nodeId.startsWith("output:")) {
            String id = nodeId.substring("output:".length());
            return document.outputs().stream()
                    .filter(o -> o.id().equals(id))
                    .map(o -> toOverviewLocation(analysis, o.range()))
                    .findFirst().orElse(null);
        }
        if (nodeId.startsWith("enum:")) {
            String id = nodeId.substring("enum:".length());
            return document.enums().stream()
                    .filter(e -> e.id().equals(id))
                    .map(e -> toOverviewLocation(analysis, e.range()))
                    .findFirst().orElse(null);
        }

        String remaining = nodeId;
        if (!remaining.startsWith("rule:")) {
            return null;
        }
        remaining = remaining.substring("rule:".length());

        String ruleId = prefixBeforeColon(remaining);
        IlimapRuleBlock rule = document.rules().stream()
                .filter(r -> r.id().equals(ruleId))
                .findFirst().orElse(null);
        if (rule == null) {
            return null;
        }

        String suffix = remaining.substring(ruleId.length());

        if (suffix.isEmpty()) {
            return toOverviewLocation(analysis, rule.range());
        }

        return resolveRuleSuffixLocation(analysis, rule, suffix);
    }

    private IlimapOverviewLocation resolveRuleSuffixLocation(IlimapAnalysis analysis, IlimapRuleBlock rule, String suffix) {
        if (suffix.equals(":target")) {
            return rule.elements().stream()
                    .filter(e -> e instanceof IlimapTargetStmt)
                    .map(e -> toOverviewLocation(analysis, ((IlimapTargetStmt) e).range()))
                    .findFirst().orElse(null);
        }

        if (suffix.startsWith(":target:")) {
            String attr = suffix.substring(":target:".length());
            for (IlimapRuleElement element : rule.elements()) {
                if (element instanceof IlimapAssignmentBlock assign) {
                    for (IlimapAssignment a : assign.assignments()) {
                        if (a.targetAttribute().equals(attr)) {
                            return toOverviewLocation(analysis, a.range());
                        }
                    }
                }
                if (element instanceof IlimapDefaultsBlock defs) {
                    for (IlimapAssignment a : defs.assignments()) {
                        if (a.targetAttribute().equals(attr)) {
                            return toOverviewLocation(analysis, a.range());
                        }
                    }
                }
            }
            return null;
        }

        if (suffix.startsWith(":source:")) {
            String afterSource = suffix.substring(":source:".length());
            if (afterSource.contains(":member:")) {
                String alias = afterSource.substring(0, afterSource.indexOf(":member:"));
                return rule.elements().stream()
                        .filter(e -> e instanceof IlimapSourceStmt && ((IlimapSourceStmt) e).alias().equals(alias))
                        .map(e -> toOverviewLocation(analysis, ((IlimapSourceStmt) e).range()))
                        .findFirst().orElse(null);
            }
            return rule.elements().stream()
                    .filter(e -> e instanceof IlimapSourceStmt && ((IlimapSourceStmt) e).alias().equals(afterSource))
                    .map(e -> toOverviewLocation(analysis, ((IlimapSourceStmt) e).range()))
                    .findFirst().orElse(null);
        }

        if (suffix.startsWith(":assign:")) {
            String attr = suffix.substring(":assign:".length());
            return findAssignmentInRule(analysis, rule, attr);
        }

        if (suffix.startsWith(":bag:")) {
            String bagId = suffix.substring(":bag:".length());
            return findBagInRule(analysis, rule, bagId);
        }

        if (suffix.startsWith(":ref:")) {
            String refId = suffix.substring(":ref:".length());
            return rule.elements().stream()
                    .filter(e -> e instanceof IlimapRefBlock && ((IlimapRefBlock) e).id().equals(refId))
                    .map(e -> toOverviewLocation(analysis, ((IlimapRefBlock) e).range()))
                    .findFirst().orElse(null);
        }

        return null;
    }

    private IlimapOverviewLocation findAssignmentInRule(IlimapAnalysis analysis, IlimapRuleBlock rule,
                                                         String targetAttribute) {
        for (IlimapRuleElement element : rule.elements()) {
            if (element instanceof IlimapAssignmentBlock assign) {
                for (IlimapAssignment a : assign.assignments()) {
                    if (a.targetAttribute().equals(targetAttribute)) {
                        return toOverviewLocation(analysis, a.range());
                    }
                }
            }
            if (element instanceof IlimapDefaultsBlock defs) {
                for (IlimapAssignment a : defs.assignments()) {
                    if (a.targetAttribute().equals(targetAttribute)) {
                        return toOverviewLocation(analysis, a.range());
                    }
                }
            }
            if (element instanceof IlimapBagBlock bag) {
                IlimapOverviewLocation loc = findAssignmentInBag(analysis, bag, targetAttribute);
                if (loc != null) {
                    return loc;
                }
            }
        }
        return null;
    }

    private IlimapOverviewLocation findAssignmentInBag(IlimapAnalysis analysis, IlimapBagBlock bag,
                                                        String targetAttribute) {
        if (bag.assign() != null) {
            for (IlimapAssignment a : bag.assign().assignments()) {
                if (a.targetAttribute().equals(targetAttribute)) {
                    return toOverviewLocation(analysis, a.range());
                }
            }
        }
        for (IlimapBagBlock nested : bag.nestedBags()) {
            IlimapOverviewLocation loc = findAssignmentInBag(analysis, nested, targetAttribute);
            if (loc != null) {
                return loc;
            }
        }
        return null;
    }

    private IlimapOverviewLocation findBagInRule(IlimapAnalysis analysis, IlimapRuleBlock rule, String bagId) {
        for (IlimapRuleElement element : rule.elements()) {
            if (element instanceof IlimapBagBlock bag) {
                IlimapOverviewLocation loc = findBagById(analysis, bag, bagId);
                if (loc != null) {
                    return loc;
                }
            }
        }
        return null;
    }

    private IlimapOverviewLocation findBagById(IlimapAnalysis analysis, IlimapBagBlock bag, String bagId) {
        if (bag.id().equals(bagId)) {
            return toOverviewLocation(analysis, bag.range());
        }
        for (IlimapBagBlock nested : bag.nestedBags()) {
            IlimapOverviewLocation loc = findBagById(analysis, nested, bagId);
            if (loc != null) {
                return loc;
            }
        }
        return null;
    }

    private static String findParentRuleId(IlimapDocument document, IlimapAstNode node) {
        for (IlimapRuleBlock rule : document.rules()) {
            if (nodeIsWithin(rule, node)) {
                return rule.id();
            }
        }
        return null;
    }

    private static boolean nodeIsWithin(IlimapRuleBlock rule, IlimapAstNode node) {
        if (node instanceof IlimapTargetStmt t) {
            return rule.elements().contains(t);
        }
        if (node instanceof IlimapSourceStmt s) {
            return rule.elements().contains(s);
        }
        if (node instanceof IlimapRefBlock r) {
            return rule.elements().contains(r);
        }
        if (node instanceof IlimapLossBlock l) {
            return rule.elements().contains(l);
        }
        if (node instanceof IlimapBagBlock bag) {
            for (IlimapRuleElement element : rule.elements()) {
                if (element instanceof IlimapBagBlock rbag && containsBag(rbag, bag)) {
                    return true;
                }
            }
        }
        if (node instanceof IlimapAssignment) {
            for (IlimapRuleElement element : rule.elements()) {
                if (element instanceof IlimapAssignmentBlock assign
                        && assign.assignments().contains(node)) {
                    return true;
                }
                if (element instanceof IlimapDefaultsBlock defs
                        && defs.assignments().contains(node)) {
                    return true;
                }
                if (element instanceof IlimapBagBlock rbag
                        && containsAssignment(rbag, (IlimapAssignment) node)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsBag(IlimapBagBlock container, IlimapBagBlock target) {
        if (container == target) {
            return true;
        }
        for (IlimapBagBlock nested : container.nestedBags()) {
            if (containsBag(nested, target)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAssignment(IlimapBagBlock bag, IlimapAssignment target) {
        if (bag.assign() != null && bag.assign().assignments().contains(target)) {
            return true;
        }
        for (IlimapBagBlock nested : bag.nestedBags()) {
            if (containsAssignment(nested, target)) {
                return true;
            }
        }
        return false;
    }

    private static int lossIndex(IlimapDocument document, String ruleId, IlimapLossBlock target) {
        for (IlimapRuleBlock rule : document.rules()) {
            if (rule.id().equals(ruleId)) {
                int idx = 0;
                for (IlimapRuleElement element : rule.elements()) {
                    if (element instanceof IlimapLossBlock) {
                        if (element == target) {
                            return idx;
                        }
                        idx++;
                    }
                }
            }
        }
        return 0;
    }

    private IlimapOverviewLocation toOverviewLocation(IlimapAnalysis analysis, IlimapSourceRange range) {
        IlimapIdeRange ideRange = analysis.lineMap().toIdeRange(range);
        return new IlimapOverviewLocation(
                ideRange.start().line(), ideRange.start().character(),
                ideRange.end().line(), ideRange.end().character());
    }

    private String identifierAtOffset(IlimapAnalysis analysis, int offset) {
        String text = analysis.text();
        if (text.isEmpty()) {
            return null;
        }

        int clamped = Math.max(0, Math.min(offset, text.length()));
        int probe = clamped < text.length() ? clamped : text.length() - 1;
        if (!isIdentifierPart(text.charAt(probe))) {
            if (clamped > 0 && isIdentifierPart(text.charAt(clamped - 1))) {
                probe = clamped - 1;
            } else {
                return null;
            }
        }

        int start = probe;
        while (start > 0 && isIdentifierPart(text.charAt(start - 1))) {
            start--;
        }
        return text.substring(start, Math.min(probe + 1, text.length()));
    }

    private static boolean isIdentifierPart(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
    }

    private static String prefixBeforeColon(String s) {
        int idx = s.indexOf(':');
        if (idx >= 0) {
            return s.substring(0, idx);
        }
        return s;
    }

    private static IlimapNavigationNode buildNode(String nodeId, String kind, String label,
                                                   String detail, IlimapOverviewLocation location) {
        return new IlimapNavigationNode(nodeId, kind, label, detail, location, List.of());
    }
}
