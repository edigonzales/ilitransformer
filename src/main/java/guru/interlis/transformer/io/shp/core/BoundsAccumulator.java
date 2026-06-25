package guru.interlis.transformer.io.shp.core;

/**
 * Accumulates the overall bounding box of a Shapefile dataset while records are written
 * sequentially.
 *
 * <p>{@code NULL} shapes (passed as a {@code null} {@link Bounds}) do not contribute to the box. An
 * empty dataset yields a {@code (0, 0, 0, 0)} box, which is the conventional placeholder used by the
 * Shapefile header for datasets without geometry.
 */
public final class BoundsAccumulator {

    private boolean empty = true;
    private double xmin;
    private double ymin;
    private double xmax;
    private double ymax;

    public void expand(Bounds bounds) {
        if (bounds == null) {
            return;
        }
        if (empty) {
            xmin = bounds.xmin();
            ymin = bounds.ymin();
            xmax = bounds.xmax();
            ymax = bounds.ymax();
            empty = false;
            return;
        }
        xmin = Math.min(xmin, bounds.xmin());
        ymin = Math.min(ymin, bounds.ymin());
        xmax = Math.max(xmax, bounds.xmax());
        ymax = Math.max(ymax, bounds.ymax());
    }

    public boolean isEmpty() {
        return empty;
    }

    public Bounds toBounds() {
        if (empty) {
            return new Bounds(0.0, 0.0, 0.0, 0.0);
        }
        return new Bounds(xmin, ymin, xmax, ymax);
    }
}
