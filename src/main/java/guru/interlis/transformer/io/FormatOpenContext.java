package guru.interlis.transformer.io;

import guru.interlis.transformer.diag.DiagnosticCollector;

import ch.interlis.ili2c.metamodel.TransferDescription;

import java.nio.file.Path;

/**
 * Immutable context passed to an {@link IoxFormatProvider} when opening a reader or writer.
 *
 * <p>Format options are intentionally not stored here; they belong to the
 * {@link guru.interlis.transformer.mapping.plan.InputBinding} /
 * {@link guru.interlis.transformer.mapping.plan.OutputBinding}.
 */
public record FormatOpenContext(
        Path baseDirectory, TransferDescription transferDescription, DiagnosticCollector diagnostics) {}
