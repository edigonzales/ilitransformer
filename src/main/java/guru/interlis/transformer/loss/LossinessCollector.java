package guru.interlis.transformer.loss;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class LossinessCollector {
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private final List<LossEvent> events = new ArrayList<>();

    public void record(LossEvent event) {
        if (event != null) {
            events.add(event);
        }
    }

    public List<LossEvent> events() {
        return List.copyOf(events);
    }

    public boolean isEmpty() {
        return events.isEmpty();
    }

    public void writeJson(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        JSON_MAPPER.writeValue(path.toFile(), sortedEvents());
    }

    public void writeMarkdown(Path path) throws IOException {
        Files.createDirectories(path.getParent());
        StringBuilder sb = new StringBuilder();
        sb.append("# Lossiness Report\n\n");
        if (events.isEmpty()) {
            sb.append("No loss events recorded.\n");
        } else {
            sb.append("| Rule | Source class | Source OID | Source path | Reason | Description |\n");
            sb.append("|------|--------------|------------|-------------|--------|-------------|\n");
            for (LossEvent event : sortedEvents()) {
                sb.append("| ").append(escape(event.ruleId()))
                        .append(" | ").append(escape(event.sourceClass()))
                        .append(" | ").append(escape(event.sourceOid()))
                        .append(" | ").append(escape(event.sourcePath()))
                        .append(" | ").append(escape(event.reasonCode()))
                        .append(" | ").append(escape(event.description()))
                        .append(" |\n");
            }
        }
        Files.writeString(path, sb.toString());
    }

    private List<LossEvent> sortedEvents() {
        return events.stream()
                .sorted(Comparator
                        .comparing(LossEvent::ruleId, Comparator.nullsLast(String::compareTo))
                        .thenComparing(LossEvent::sourceClass, Comparator.nullsLast(String::compareTo))
                        .thenComparing(LossEvent::sourceOid, Comparator.nullsLast(String::compareTo))
                        .thenComparing(LossEvent::sourcePath, Comparator.nullsLast(String::compareTo))
                        .thenComparing(LossEvent::reasonCode, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    private static String escape(String text) {
        if (text == null) return "";
        return text.replace("|", "\\|").replace("\n", " ");
    }
}
