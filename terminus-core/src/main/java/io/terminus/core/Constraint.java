package io.terminus.core;

/**
 * Represents the space available to a component during the measure pass.
 *
 * WHY NOT JUST PASS A Bounds?
 * Bounds has x and y — a position. During measurement, a component
 * doesn't have a position yet. Passing Bounds would imply a position
 * exists, which is misleading. Constraint only carries width and height
 * — the two things that actually constrain a component's size preference.
 *
 * This distinction is borrowed from Flutter's constraint model and
 * Android's MeasureSpec. It's the right abstraction.
 *
 * SPECIAL VALUE: UNBOUNDED (-1)
 * Means "take as much space as you want." Used for scrollable containers
 * measuring their children — the child reports its natural size, and
 * the parent clips or scrolls to fit.
 */

public record Constraint(int maxWidth, int maxHeight) {

    /** Sentinel value meaning "no limit in this dimension." */
    public static final int UNBOUNDED = -1;

    public Constraint {
        if (maxWidth < 0 && maxWidth != UNBOUNDED) {
            throw new IllegalArgumentException(
                "maxWidth must be >= 0 or UNBOUNDED (-1), got: " + maxWidth);
        }
        if (maxHeight < 0 && maxHeight != UNBOUNDED) {
            throw new IllegalArgumentException(
                "maxHeight must be >= 0 or UNBOUNDED (-1), got: " + maxHeight);
        }
    }

    // ── Factory methods ───────────────────────────────────────────────────

    /** Constrain to exactly this terminal size — used for the root component. */
    public static Constraint of(int maxWidth, int maxHeight) {
        return new Constraint(maxWidth, maxHeight);
    }

    /** No constraint in either dimension — used by ScrollView for its children. */
    public static final Constraint UNBOUNDED_BOTH =
        new Constraint(UNBOUNDED, UNBOUNDED);

    /** No width constraint — used for horizontally scrolling content. */
    public static Constraint unboundedWidth(int maxHeight) {
        return new Constraint(UNBOUNDED, maxHeight);
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    public boolean isWidthUnbounded()  { return maxWidth  == UNBOUNDED; }
    public boolean isHeightUnbounded() { return maxHeight == UNBOUNDED; }

    /**
     * Shrink the constraint by padding. Used by Container to give
     * children a constraint that accounts for the container's own padding.
     * Clamps to zero — a constraint can never be negative.
     */
    public Constraint inset(int horizontal, int vertical) {
        int w = isWidthUnbounded()  ? UNBOUNDED : Math.max(0, maxWidth  - horizontal);
        int h = isHeightUnbounded() ? UNBOUNDED : Math.max(0, maxHeight - vertical);
        return new Constraint(w, h);
    }
}