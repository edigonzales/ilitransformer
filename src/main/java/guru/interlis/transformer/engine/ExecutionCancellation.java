package guru.interlis.transformer.engine;

/** Cooperative cancellation shared by interactive and workflow hosts. The interrupt flag is retained. */
public final class ExecutionCancellation {
    private ExecutionCancellation() {}

    public static void check() {
        if (Thread.currentThread().isInterrupted())
            throw new java.util.concurrent.CancellationException("Migration cancelled");
    }
}
