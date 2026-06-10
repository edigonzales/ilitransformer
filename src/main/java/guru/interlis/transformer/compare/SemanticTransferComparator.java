package guru.interlis.transformer.compare;

import ch.interlis.iom.IomObject;
import guru.interlis.transformer.loss.LossEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class SemanticTransferComparator {

    public ComparisonReport compare(
            Collection<IomObject> left,
            Collection<IomObject> right,
            ComparisonProfile profile) {
        return compare(left, right, profile, List.of());
    }

    public ComparisonReport compare(
            Collection<IomObject> left,
            Collection<IomObject> right,
            ComparisonProfile profile,
            Collection<LossEvent> observedLossEvents) {
        Objects.requireNonNull(left, "left");
        Objects.requireNonNull(right, "right");
        Objects.requireNonNull(profile, "profile");

        List<ComparisonReport.ComparisonIssue> issues = new ArrayList<>();
        Map<String, List<IomObject>> leftIndex = index(left, profile, "left", issues);
        Map<String, List<IomObject>> rightIndex = index(right, profile, "right", issues);

        int matched = 0;
        Set<String> allKeys = new LinkedHashSet<>();
        allKeys.addAll(leftIndex.keySet());
        allKeys.addAll(rightIndex.keySet());

        for (String key : allKeys) {
            List<IomObject> leftObjects = leftIndex.getOrDefault(key, List.of());
            List<IomObject> rightObjects = rightIndex.getOrDefault(key, List.of());
            if (leftObjects.isEmpty()) {
                issues.add(error(key, "missing left object", null, describe(rightObjects.get(0))));
                continue;
            }
            if (rightObjects.isEmpty()) {
                issues.add(error(key, "missing right object", describe(leftObjects.get(0)), null));
                continue;
            }
            if (leftObjects.size() != 1 || rightObjects.size() != 1) {
                issues.add(error(key, "business key is not unique",
                        String.valueOf(leftObjects.size()), String.valueOf(rightObjects.size())));
                continue;
            }
            matched++;
            compareObject(key, leftObjects.get(0), rightObjects.get(0), profile, issues);
        }

        Set<String> observedReasons = observedLossEvents == null ? Set.of() : observedLossEvents.stream()
                .map(LossEvent::reasonCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        for (String expectedReason : profile.expectedLossReasonCodes()) {
            if (!observedReasons.contains(expectedReason)) {
                issues.add(error("lossiness", "expected loss reason was not observed",
                        expectedReason, observedReasons.toString()));
            }
        }

        return new ComparisonReport(left.size(), right.size(), matched, issues,
                observedReasons, profile.expectedLossReasonCodes());
    }

    private Map<String, List<IomObject>> index(
            Collection<IomObject> objects,
            ComparisonProfile profile,
            String side,
            List<ComparisonReport.ComparisonIssue> issues) {
        Map<String, List<IomObject>> index = new LinkedHashMap<>();
        for (IomObject object : objects) {
            String key = objectKey(object, profile);
            if (key == null) {
                issues.add(error(side, "object has no usable semantic key", null, describe(object)));
                continue;
            }
            index.computeIfAbsent(key, ignored -> new ArrayList<>()).add(object);
        }
        return index;
    }

    private String objectKey(IomObject object, ComparisonProfile profile) {
        if (object == null) {
            return null;
        }
        String tag = object.getobjecttag();
        List<String> keyAttrs = profile.businessKeyFor(tag);
        String type = shortName(tag);
        if (!keyAttrs.isEmpty()) {
            String values = keyAttrs.stream()
                    .map(attr -> attr + "=" + scalarValue(object, attr))
                    .collect(Collectors.joining("|"));
            return type + "[" + values + "]";
        }
        String oid = object.getobjectoid();
        if (oid != null && !oid.isBlank()) {
            return type + "[oid=" + oid + "]";
        }
        return type + "[sig=" + canonicalObject(object, profile, shortName(tag)) + "]";
    }

    private void compareObject(
            String path,
            IomObject left,
            IomObject right,
            ComparisonProfile profile,
            List<ComparisonReport.ComparisonIssue> issues) {
        if (left == null || right == null) {
            if (left != right) {
                issues.add(error(path, "one object is null", describe(left), describe(right)));
            }
            return;
        }
        if (!Objects.equals(shortName(left.getobjecttag()), shortName(right.getobjecttag()))
                && !profile.ignores(path + "._type")) {
            issues.add(error(path + "._type", "object type differs",
                    left.getobjecttag(), right.getobjecttag()));
        }

        Set<String> attrs = new LinkedHashSet<>();
        attrs.addAll(attributeNames(left));
        attrs.addAll(attributeNames(right));
        for (String attr : attrs.stream().sorted().toList()) {
            String attrPath = path + "." + attr;
            if (profile.ignores(attrPath)) {
                continue;
            }
            compareAttribute(attrPath, left, right, attr, profile, issues);
        }
    }

    private void compareAttribute(
            String path,
            IomObject left,
            IomObject right,
            String attr,
            ComparisonProfile profile,
            List<ComparisonReport.ComparisonIssue> issues) {
        int leftCount = safeValueCount(left, attr);
        int rightCount = safeValueCount(right, attr);
        if (leftCount != rightCount) {
            issues.add(error(path + "._count", "attribute value count differs",
                    String.valueOf(leftCount), String.valueOf(rightCount)));
            return;
        }
        if (leftCount == 0) {
            String leftValue = scalarValue(left, attr);
            String rightValue = scalarValue(right, attr);
            compareScalar(path, leftValue, rightValue, profile, issues);
            return;
        }

        List<ValueNode> leftValues = attributeValues(left, attr, profile, path);
        List<ValueNode> rightValues = attributeValues(right, attr, profile, path);
        leftValues.sort(Comparator.comparing(ValueNode::canonical));
        rightValues.sort(Comparator.comparing(ValueNode::canonical));
        for (int i = 0; i < leftValues.size(); i++) {
            compareValue(path + "[" + i + "]", leftValues.get(i), rightValues.get(i), profile, issues);
        }
    }

    private void compareValue(
            String path,
            ValueNode left,
            ValueNode right,
            ComparisonProfile profile,
            List<ComparisonReport.ComparisonIssue> issues) {
        if (left.referenceOid() != null || right.referenceOid() != null) {
            if (!Objects.equals(left.referenceOid(), right.referenceOid())) {
                issues.add(error(path, "reference differs", left.referenceOid(), right.referenceOid()));
            }
            return;
        }
        if (left.object() != null || right.object() != null) {
            compareObject(path, left.object(), right.object(), profile, issues);
            return;
        }
        compareScalar(path, left.scalar(), right.scalar(), profile, issues);
    }

    private void compareScalar(
            String path,
            String left,
            String right,
            ComparisonProfile profile,
            List<ComparisonReport.ComparisonIssue> issues) {
        if (Objects.equals(left, right)) {
            return;
        }
        if (left == null || right == null) {
            issues.add(error(path, "scalar value differs", left, right));
            return;
        }
        Double leftNumber = parseNumber(left);
        Double rightNumber = parseNumber(right);
        if (leftNumber != null && rightNumber != null
                && Math.abs(leftNumber - rightNumber) <= profile.numericTolerance()) {
            return;
        }
        issues.add(error(path, "scalar value differs", left, right));
    }

    private List<ValueNode> attributeValues(
            IomObject object,
            String attr,
            ComparisonProfile profile,
            String path) {
        List<ValueNode> values = new ArrayList<>();
        int count = safeValueCount(object, attr);
        for (int i = 0; i < count; i++) {
            IomObject child = childValue(object, attr, i);
            if (child != null) {
                if (child.getobjectrefoid() != null) {
                    values.add(new ValueNode(null, null, child.getobjectrefoid(),
                            "ref:" + child.getobjectrefoid()));
                } else {
                    values.add(new ValueNode(null, child, null,
                            canonicalObject(child, profile, path + "[" + i + "]")));
                }
            } else if (i == 0) {
                String scalar = object.getattrvalue(attr);
                values.add(new ValueNode(scalar, null, null, "scalar:" + scalar));
            } else {
                values.add(new ValueNode(null, null, null, "scalar:null"));
            }
        }
        return values;
    }

    private String canonicalObject(IomObject object, ComparisonProfile profile, String path) {
        if (object == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        if (object.getobjectrefoid() != null) {
            parts.add("@ref=" + object.getobjectrefoid());
        }
        for (String attr : attributeNames(object).stream().sorted().toList()) {
            String attrPath = path + "." + attr;
            if (profile.ignores(attrPath)) {
                continue;
            }
            int count = safeValueCount(object, attr);
            if (count == 0) {
                parts.add(attr + "=" + object.getattrvalue(attr));
                continue;
            }
            List<String> values = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                IomObject child = childValue(object, attr, i);
                if (child != null) {
                    values.add(canonicalObject(child, profile, attrPath + "[" + i + "]"));
                } else if (i == 0) {
                    values.add(String.valueOf(object.getattrvalue(attr)));
                }
            }
            values.sort(String::compareTo);
            parts.add(attr + "=[" + String.join(",", values) + "]");
        }
        return shortName(object.getobjecttag()) + "{" + String.join(";", parts) + "}";
    }

    private Set<String> attributeNames(IomObject object) {
        Set<String> names = new LinkedHashSet<>();
        if (object == null) {
            return names;
        }
        for (int i = 0; i < object.getattrcount(); i++) {
            String name = object.getattrname(i);
            if (name != null) {
                names.add(name);
            }
        }
        return names;
    }

    private int safeValueCount(IomObject object, String attr) {
        try {
            return object.getattrvaluecount(attr);
        } catch (RuntimeException ex) {
            return 0;
        }
    }

    private String scalarValue(IomObject object, String attr) {
        try {
            return object.getattrvalue(attr);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private IomObject childValue(IomObject object, String attr, int index) {
        try {
            return object.getattrobj(attr, index);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private Double parseNumber(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String describe(IomObject object) {
        if (object == null) {
            return null;
        }
        return shortName(object.getobjecttag()) + "#" + object.getobjectoid();
    }

    private static String shortName(String tag) {
        if (tag == null) {
            return "";
        }
        int idx = tag.lastIndexOf('.');
        return idx >= 0 ? tag.substring(idx + 1) : tag;
    }

    private ComparisonReport.ComparisonIssue error(
            String path,
            String message,
            String leftValue,
            String rightValue) {
        return new ComparisonReport.ComparisonIssue(
                ComparisonReport.Severity.ERROR, path, message, leftValue, rightValue);
    }

    private record ValueNode(
            String scalar,
            IomObject object,
            String referenceOid,
            String canonical
    ) {}
}
