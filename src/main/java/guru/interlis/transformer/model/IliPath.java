package guru.interlis.transformer.model;

import java.util.ArrayList;
import java.util.List;

public final class IliPath {

    private final String model;
    private final String topic;
    private final String className;
    private final String member; // attribute, role, or structure attribute
    private final List<String> parts;

    private IliPath(String model, String topic, String className, String member, List<String> parts) {
        this.model = model;
        this.topic = topic;
        this.className = className;
        this.member = member;
        this.parts = parts;
    }

    public static IliPath parse(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("Path must not be null or blank");
        }
        String trimmed = path.trim();
        String[] segments = trimmed.split("\\.");
        List<String> parts = new ArrayList<>();
        for (String s : segments) {
            String cleaned = s.trim();
            if (cleaned.isEmpty()) {
                throw new IllegalArgumentException("Path contains empty segment: " + path);
            }
            parts.add(cleaned);
        }
        if (parts.size() < 3) {
            throw new IllegalArgumentException("Path must contain at least Model.Topic.Class, got: " + path);
        }
        String model = parts.get(0);
        String topic = parts.get(1);
        String className = parts.get(2);
        String member = parts.size() >= 4 ? parts.get(3) : null;
        return new IliPath(model, topic, className, member, parts);
    }

    public String model() {
        return model;
    }

    public String topic() {
        return topic;
    }

    public String className() {
        return className;
    }

    public String member() {
        return member;
    }

    public int length() {
        return parts.size();
    }

    public String part(int index) {
        return parts.get(index);
    }

    public List<String> parts() {
        return List.copyOf(parts);
    }

    public String qualifiedClass() {
        return model + "." + topic + "." + className;
    }

    @Override
    public String toString() {
        return String.join(".", parts);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IliPath other)) return false;
        return parts.equals(other.parts);
    }

    @Override
    public int hashCode() {
        return parts.hashCode();
    }
}
