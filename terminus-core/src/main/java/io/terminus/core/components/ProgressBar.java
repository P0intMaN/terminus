package io.terminus.core.components;

import io.terminus.core.Bounds;
import io.terminus.core.Cell;
import io.terminus.core.Constraint;
import io.terminus.core.Leaf;

/**
 * A horizontal progress bar component.
 *
 * Renders a visual representation of a value in the range [0.0, 1.0].
 *
 * PATTERN: Builder
 * Configuration (style, colors, label) is set once at construction
 * via ProgressBar.builder().style(...).fg(...).build().
 * Runtime state (value) is updated via setValue().
 *
 * WHY BUILDER HERE?
 * A ProgressBar has 7 configuration options. A constructor with 7
 * parameters is unreadable:
 *   new ProgressBar(0.5, Style.EIGHTHS, 0x7F77DD, -1, "Loading", true, RIGHT)
 * vs
 *   ProgressBar.builder().style(EIGHTHS).fg(0x7F77DD).label("Loading").build()
 *
 * The Builder pattern also enforces that configuration is set before
 * the component is used — you can't forget to set the style because
 * build() can validate completeness.
 *
 * THREAD SAFETY:
 * setValue() is called from the UI thread only.
 * If you update value from a background thread, post a StateChangeEvent
 * and call setValue() from onEvent() on the UI thread.
 */
public class ProgressBar extends Leaf {

    // ── Style ─────────────────────────────────────────────────────────────

    /**
     * The visual style of the bar — which character set to use.
     *
     * Each style is an array of characters from "empty" to "full".
     * The EIGHTHS and BRAILLE styles have 9 entries (index 0 = empty,
     * index 8 = full) to support sub-character precision.
     */
    public enum Style {
        /**
         * Unicode block elements — 8 sub-character steps per cell.
         * Best general-purpose choice.
         * Empty char is a space; full char is █.
         */
        EIGHTHS(
            new char[]{ ' ', '▏', '▎', '▍', '▌', '▋', '▊', '▉', '█' },
            '█'
        ),

        /**
         * Block shading characters — simple fill/empty distinction.
         * Uses ░ for empty (more visible than a space).
         */
        BLOCK(
            new char[]{ '░', '░', '░', '░', '░', '░', '░', '░', '█' },
            '█'
        ),

        /**
         * Plain ASCII — maximum compatibility.
         * Works on any terminal, SSH session, or legacy system.
         */
        ASCII(
            new char[]{ '-', '-', '-', '-', '-', '-', '-', '-', '=' },
            '='
        ),

        /**
         * Braille dot patterns — high visual density.
         * Gives a "filled" feel with fine-grained precision.
         */
        BRAILLE(
            new char[]{ '⠀', '⡀', '⡄', '⡆', '⡇', '⣇', '⣧', '⣷', '⣿' },
            '⣿'
        );

        /**
         * The 9-entry character array.
         * Index 0 = completely empty cell.
         * Index 8 = completely full cell.
         * Indices 1-7 = intermediate partial fill states.
         */
        final char[] chars;

        /** The character used for fully-filled cells (same as chars[8]). */
        final char fullChar;

        Style(char[] chars, char fullChar) {
            this.chars    = chars;
            this.fullChar = fullChar;
        }
    }

    /**
     * Where to display the label relative to the bar.
     */
    public enum LabelPosition {
        LEFT,    // label drawn before the bar
        RIGHT,   // label drawn after the bar (default)
        INSIDE   // label overlaid on top of the bar, centered
    }

    // ── Configuration (immutable after build) ─────────────────────────────

    private final Style         style;
    private final int           fg;            // bar fill color
    private final int           emptyFg;       // unfilled portion color
    private final int           bg;            // background color
    private final String        label;         // static label text, or null
    private final boolean       showPercentage;
    private final LabelPosition labelPosition;

    // ── Runtime state (mutable) ───────────────────────────────────────────

    /**
     * Current progress value in [0.0, 1.0].
     * 0.0 = empty, 1.0 = completely full.
     */
    private double value;

    // ── Constructor (package-private — use Builder) ───────────────────────

    private ProgressBar(Builder b) {
        this.style          = b.style;
        this.fg             = b.fg;
        this.emptyFg        = b.emptyFg;
        this.bg             = b.bg;
        this.label          = b.label;
        this.showPercentage = b.showPercentage;
        this.labelPosition  = b.labelPosition;
        this.value          = b.initialValue;
    }

    // ── Builder ───────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private Style         style          = Style.EIGHTHS;
        private int           fg             = 0x7F77DD; // terminus purple
        private int           emptyFg        = 0x444441; // dark gray
        private int           bg             = Cell.DEFAULT_COLOR;
        private String        label          = null;
        private boolean       showPercentage = true;
        private LabelPosition labelPosition  = LabelPosition.RIGHT;
        private double        initialValue   = 0.0;

        public Builder style(Style style) {
            this.style = style; return this;
        }

        public Builder fg(int color) {
            this.fg = color; return this;
        }

        public Builder emptyFg(int color) {
            this.emptyFg = color; return this;
        }

        public Builder bg(int color) {
            this.bg = color; return this;
        }

        public Builder label(String label) {
            this.label = label; return this;
        }

        public Builder showPercentage(boolean show) {
            this.showPercentage = show; return this;
        }

        public Builder labelPosition(LabelPosition position) {
            this.labelPosition = position; return this;
        }

        public Builder initialValue(double value) {
            this.initialValue = clamp(value); return this;
        }

        public ProgressBar build() {
            return new ProgressBar(this);
        }
    }

    // ── Public API ────────────────────────────────────────────────────────

    /**
     * Update the progress value and trigger a re-render.
     *
     * @param value progress in [0.0, 1.0]. Clamped if out of range.
     */
    public void setValue(double value) {
        double clamped = clamp(value);
        if (Double.compare(this.value, clamped) != 0) {
            this.value = clamped;
            markDirty(); // tells the render pipeline to re-draw us
        }
    }

    public double getValue() { return value; }

    // ── Leaf contract ─────────────────────────────────────────────────────

    @Override
    public Bounds measure(Constraint c) {
        // A ProgressBar is always 1 row tall.
        // Width: take all available space up to max, min 10.
        int width = c.isWidthUnbounded()
            ? 40
            : Math.max(10, c.maxWidth());
        return Bounds.of(width, 1);
    }

    @Override
    public Cell[][] render() {
        Cell[][] grid = blankGrid();
        if (grid[0].length == 0) return grid;

        // The full width available to us
        int totalWidth = grid[0].length;

        // Build the right-side annotation (percentage and/or label for RIGHT position)
        String rightAnnotation = buildRightAnnotation();
        String leftAnnotation  = buildLeftAnnotation();

        // Calculate how much width the bar itself gets
        int leftWidth  = leftAnnotation.isEmpty()  ? 0 : leftAnnotation.length() + 1;
        int rightWidth = rightAnnotation.isEmpty() ? 0 : rightAnnotation.length() + 1;
        int barWidth   = totalWidth - leftWidth - rightWidth;

        if (barWidth <= 0) {
            // Not enough space — just write percentage if we can
            if (totalWidth >= 4) {
                writeString(grid, 0, 0,
                    String.format("%3.0f%%", value * 100),
                    fg, bg, Cell.ATTR_NONE);
            }
            return grid;
        }

        int col = 0;

        // Draw left annotation (label on left)
        if (!leftAnnotation.isEmpty()) {
            writeString(grid, 0, col, leftAnnotation,
                Cell.DEFAULT_COLOR, bg, Cell.ATTR_NONE);
            col += leftAnnotation.length() + 1;
        }

        // Draw the bar itself
        if (labelPosition == LabelPosition.INSIDE) {
            renderBarWithInsideLabel(grid, col, barWidth);
        } else {
            renderBar(grid, col, barWidth);
        }
        col += barWidth;

        // Draw right annotation (percentage / label on right)
        if (!rightAnnotation.isEmpty()) {
            col++; // one space gap between bar and annotation
            writeString(grid, 0, col, rightAnnotation,
                Cell.DEFAULT_COLOR, bg, Cell.ATTR_NONE);
        }

        return grid;
    }

    // ── Private rendering ─────────────────────────────────────────────────

    /**
     * Render the bar fill into the grid row starting at startCol.
     *
     * THE FRACTIONAL FILL ALGORITHM:
     *   totalFill  = value × barWidth           (e.g. 7.52 for 37.6% of 20)
     *   fullBlocks = floor(totalFill)            (e.g. 7)
     *   remainder  = totalFill - fullBlocks      (e.g. 0.52)
     *   partialIdx = floor(remainder × 8)        (e.g. 4 → '▌')
     *
     * This gives us 8× sub-character precision, making a 20-char bar
     * effectively have 160 distinct visual positions.
     */
    private void renderBar(Cell[][] grid, int startCol, int barWidth) {
        double totalFill   = value * barWidth;
        int    fullBlocks  = (int) totalFill;
        double remainder   = totalFill - fullBlocks;
        int    partialIdx  = (int) Math.floor(remainder * 8); // 0-7

        int col = startCol;

        // Full blocks
        for (int i = 0; i < fullBlocks && col < startCol + barWidth; i++, col++) {
            grid[0][col] = new Cell(
                style.fullChar, fg, bg, Cell.ATTR_NONE, (byte) 1
            );
        }

        // Partial block (only for EIGHTHS and BRAILLE — they have intermediate chars)
        boolean hasPartials = (style == Style.EIGHTHS || style == Style.BRAILLE);
        if (hasPartials && partialIdx > 0 && col < startCol + barWidth) {
            grid[0][col] = new Cell(
                style.chars[partialIdx], fg, bg, Cell.ATTR_NONE, (byte) 1
            );
            col++;
        }

        // Empty portion
        for (; col < startCol + barWidth; col++) {
            grid[0][col] = new Cell(
                style.chars[0], emptyFg, bg, Cell.ATTR_NONE, (byte) 1
            );
        }
    }

    /**
     * Render the bar with a label overlaid centered on the fill.
     * Text shows through the fill in a contrasting color.
     */
    private void renderBarWithInsideLabel(Cell[][] grid, int startCol, int barWidth) {
        // First render the bar normally
        renderBar(grid, startCol, barWidth);

        // Then overlay the label centered on the bar
        String overlay = buildInsideLabel(barWidth);
        if (overlay.isEmpty()) return;

        int labelStart = startCol + (barWidth - overlay.length()) / 2;
        double fillEnd = value * barWidth;

        for (int i = 0; i < overlay.length(); i++) {
            int col       = labelStart + i;
            boolean inFill = (col - startCol) < fillEnd;

            // Text on filled portion: dark text on colored background
            // Text on empty portion: colored text on dark background
            int textFg = inFill ? 0x1a1a2e : fg;
            int textBg = inFill ? fg       : bg;

            if (col >= 0 && col < grid[0].length) {
                grid[0][col] = new Cell(
                    overlay.charAt(i), textFg, textBg, Cell.ATTR_BOLD, (byte) 1
                );
            }
        }
    }

    // ── Label builders ────────────────────────────────────────────────────

    private String buildLeftAnnotation() {
        if (labelPosition != LabelPosition.LEFT) return "";
        return buildLabelString();
    }

    private String buildRightAnnotation() {
        if (labelPosition == LabelPosition.LEFT
         || labelPosition == LabelPosition.INSIDE) return "";
        return buildLabelString();
    }

    private String buildLabelString() {
        StringBuilder sb = new StringBuilder();
        if (label != null && !label.isEmpty()) {
            sb.append(label);
        }
        if (showPercentage) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(String.format("%3.0f%%", value * 100));
        }
        return sb.toString();
    }

    private String buildInsideLabel(int barWidth) {
        String pct = String.format("%.0f%%", value * 100);
        if (label != null && !label.isEmpty()) {
            String full = label + " " + pct;
            return full.length() <= barWidth ? full : pct;
        }
        return pct.length() <= barWidth ? pct : "";
    }

    // ── Utility ───────────────────────────────────────────────────────────

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(1.0, v));
    }
}