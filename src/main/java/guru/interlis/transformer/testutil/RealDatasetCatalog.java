package guru.interlis.transformer.testutil;

import ch.interlis.iom_j.itf.ItfReader2;
import ch.interlis.iom_j.xtf.Xtf24Reader;
import ch.interlis.iox.EndTransferEvent;
import ch.interlis.iox.IoxEvent;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox.ObjectEvent;
import ch.interlis.iox.StartTransferEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public final class RealDatasetCatalog {

    private RealDatasetCatalog() {}

    public static List<TransferDatasetDescriptor> scan(Path root) {
        List<TransferDatasetDescriptor> result = new ArrayList<>();
        if (!Files.exists(root)) return result;
        try (Stream<Path> files = Files.walk(root)) {
            files.filter(RealDatasetCatalog::isTransferFile).forEach(f -> {
                try {
                    classify(f).ifPresent(result::add);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
        return result;
    }

    public static TransferDatasetDescriptor requireSingleItf(Path root) {
        List<TransferDatasetDescriptor> all = scan(root).stream()
                .filter(d -> d.format() == TransferFormat.ITF)
                .toList();
        if (all.isEmpty()) {
            throw new IllegalStateException("No ITF transfer found under " + root);
        }
        if (all.size() > 1) {
            throw new IllegalStateException("Multiple ITF transfers found under " + root + ": " + all);
        }
        return all.get(0);
    }

    public static TransferDatasetDescriptor requireSingleXtf(Path root) {
        List<TransferDatasetDescriptor> all = scan(root).stream()
                .filter(d -> d.format() == TransferFormat.XTF)
                .toList();
        if (all.isEmpty()) {
            throw new IllegalStateException("No XTF transfer found under " + root);
        }
        if (all.size() > 1) {
            throw new IllegalStateException("Multiple XTF transfers found under " + root + ": " + all);
        }
        return all.get(0);
    }

    private static boolean isTransferFile(Path path) {
        if (!Files.isRegularFile(path)) return false;
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".itf") || name.endsWith(".xtf");
    }

    private static java.util.Optional<TransferDatasetDescriptor> classify(Path path) {
        String name = path.getFileName().toString();
        String lowerName = name.toLowerCase();
        TransferFormat format;
        IoxReader reader = null;
        try {
            if (lowerName.endsWith(".itf")) {
                format = TransferFormat.ITF;
                reader = new ItfReader2(path.toFile(), false);
            } else {
                format = TransferFormat.XTF;
                reader = Xtf24Reader.createReader(path.toFile());
            }

            Set<String> models = new LinkedHashSet<>();
            IoxEvent event;
            while ((event = reader.read()) != null) {
                if (event instanceof StartTransferEvent) {
                    continue;
                } else if (event instanceof ObjectEvent obj) {
                    String tag = obj.getIomObject().getobjecttag();
                    if (tag != null && !tag.isBlank()) {
                        String modelPart = extractModelName(tag);
                        if (modelPart != null) models.add(modelPart);
                    }
                    break;
                } else if (event instanceof EndTransferEvent) {
                    break;
                }
            }

            long size = Files.size(path);
            return java.util.Optional.of(new TransferDatasetDescriptor(
                    name, path.toAbsolutePath(), format, List.copyOf(models), List.of(), size));
        } catch (Exception e) {
            return java.util.Optional.empty();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static String extractModelName(String qualifiedClassName) {
        if (qualifiedClassName == null) return null;
        int firstDot = qualifiedClassName.indexOf('.');
        if (firstDot <= 0) return null;
        return qualifiedClassName.substring(0, firstDot);
    }
}
