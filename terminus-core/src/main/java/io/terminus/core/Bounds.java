package io.terminus.core;

/**
 * An immutable rectangle in terminal-space coordinates.
 *
 * Coordinates are measured in character cells, not pixels.
 * Origin (0,0) is the top-left corner of the terminal.
 * x increases to the right. y increases downward.
 *
 * WHY A RECORD?
 * Same reasoning as Cell — Bounds is a value object. Two Bounds with
 * the same x,y,width,height are interchangeable. Records express this.
 *
 * WHY SEPARATE FROM Cell?
 * Cell answers "what does this position look like?"
 * Bounds answers "where is this component, and how big is it?"
 * These are different concerns at different abstraction levels.
 * Mixing them would violate the Single Responsibility Principle.
 */

public record Bounds(int x, int y, int width, int height) {

    // Compact constructor validates invariants at construction time
    public Bounds {
        if (width < 0) throw new IllegalArgumentException(
            "Bounds width cannot be negative: " + width);
        if (height < 0) throw new IllegalArgumentException(
            "Bounds height cannot be negative: " + height);
    }

    // ── Factory methods ───────────────────────────────────────────────────

    /** A zero-size Bounds at the origin. Useful as a "not yet measured" sentinel. */
    public static final Bounds ZERO = new Bounds(0, 0, 0, 0);

    /** Create Bounds starting at origin — common for top-level layout. */
    public static Bounds of(int width, int height) {
        return new Bounds(0, 0, width, height);
    }

    // ── Derived properties ────────────────────────────────────────────────

    /** The column index of the rightmost character (exclusive). */
    public int right()  { return x + width; }

    /** The row index below the last row (exclusive). */
    public int bottom() { return y + height; }

    /** True if width and height are both zero. */
    public boolean isEmpty() { return width == 0 || height == 0; }

    // ── Spatial operations ────────────────────────────────────────────────
    // WHY THESE OPERATIONS?
    // The layout engine needs to carve up space — split a row bounds into
    // left and right halves, or shrink bounds by a padding amount.
    // These operations return NEW Bounds objects (immutability).

    /** Shrink by uniform padding on all sides. */
    public Bounds inset(int padding) {
        return inset(padding, padding, padding, padding);
    }

    /** Shrink by individual padding: top, right, bottom, left. */
    public Bounds inset(int top, int right, int bottom, int left) {
        int newWidth  = Math.max(0, width  - left - right);
        int newHeight = Math.max(0, height - top  - bottom);
        return new Bounds(x + left, y + top, newWidth, newHeight);
    }

    /** Translate (move) by dx columns and dy rows. */
    public Bounds translate(int dx, int dy) {
        return new Bounds(x + dx, y + dy, width, height);
    }

    /**
     * Returns true if the given column and row fall within this bounds.
     * Used by the event dispatcher to hit-test mouse clicks.
     */
    public boolean contains(int col, int row) {
        return col >= x && col < right() && row >= y && row < bottom();
    }

    /**
     * Returns the intersection of this and another Bounds, or ZERO
     * if they don't overlap. Used by the renderer to clip child output.
     */
    public Bounds intersect(Bounds other) {
        int ix = Math.max(x, other.x);
        int iy = Math.max(y, other.y);
        int iw = Math.min(right(), other.right()) - ix;
        int ih = Math.min(bottom(), other.bottom()) - iy;
        if (iw <= 0 || ih <= 0) return ZERO;
        return new Bounds(ix, iy, iw, ih);
    }
    
}
