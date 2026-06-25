package guru.interlis.transformer.io;

import guru.interlis.transformer.geometry.ItfGeometryWriter;
import guru.interlis.transformer.mapping.plan.InputBinding;
import guru.interlis.transformer.mapping.plan.OutputBinding;

import ch.interlis.iom_j.itf.ItfReader2;
import ch.interlis.iom_j.itf.ItfWriter;
import ch.interlis.iom_j.xtf.Xtf24Reader;
import ch.interlis.iom_j.xtf.XtfWriter;
import ch.interlis.iox.IoxReader;
import ch.interlis.iox.IoxWriter;
import ch.interlis.iox_j.IoxIliReader;

import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Built-in provider for the native INTERLIS transfer formats {@code itf}, {@code xtf} and {@code
 * xml}.
 *
 * <p>This class carries the dispatch logic formerly hosted in {@code InterlisIoFactory}. Selection is
 * driven by the file extension so the observable behavior (reader/writer type and rejection of
 * unsupported extensions) stays identical.
 */
public final class BuiltInInterlisFormatProvider implements IoxFormatProvider {

    private static final Set<String> FORMAT_IDS =
            Collections.unmodifiableSet(new LinkedHashSet<>(List.of("itf", "xtf", "xml")));

    @Override
    public String id() {
        return "interlis";
    }

    @Override
    public Set<String> formatIds() {
        return FORMAT_IDS;
    }

    @Override
    public FormatCapabilities capabilities() {
        return FormatCapabilities.readWritePathModel();
    }

    @Override
    public boolean supportsInput(InputBinding binding) {
        return hasInterlisExtension(binding.path());
    }

    @Override
    public boolean supportsOutput(OutputBinding binding) {
        return hasInterlisExtension(binding.path());
    }

    @Override
    public IoxReader openReader(InputBinding binding, FormatOpenContext context) throws Exception {
        Path path = binding.path();
        String lowerName = path.getFileName().toString().toLowerCase();
        IoxReader reader;
        if (lowerName.endsWith(".itf")) {
            // ItfReader2 merges AREA/SURFACE helper tables into canonical geometry objects.
            reader = new ItfReader2(path.toFile(), false);
        } else if (lowerName.endsWith(".xtf") || lowerName.endsWith(".xml")) {
            reader = Xtf24Reader.createReader(path.toFile());
        } else {
            throw new IllegalArgumentException("Unsupported input file type: " + path);
        }
        if (reader instanceof IoxIliReader iliReader) {
            iliReader.setModel(context.transferDescription());
        }
        return new EndTransferAwareReader(reader);
    }

    @Override
    public IoxWriter openWriter(OutputBinding binding, FormatOpenContext context) throws Exception {
        Path path = binding.path();
        String lowerName = path.getFileName().toString().toLowerCase();
        if (lowerName.endsWith(".itf")) {
            return new ItfGeometryWriter(
                    new ItfWriter(path.toFile(), context.transferDescription()),
                    context.transferDescription(),
                    context.diagnostics());
        }
        if (lowerName.endsWith(".xtf") || lowerName.endsWith(".xml")) {
            return new XtfWriter(path.toFile(), context.transferDescription());
        }
        throw new IllegalArgumentException("Unsupported output file type: " + path);
    }

    private static boolean hasInterlisExtension(Path path) {
        if (path == null) {
            return false;
        }
        String lowerName = path.getFileName().toString().toLowerCase();
        return lowerName.endsWith(".itf") || lowerName.endsWith(".xtf") || lowerName.endsWith(".xml");
    }
}
