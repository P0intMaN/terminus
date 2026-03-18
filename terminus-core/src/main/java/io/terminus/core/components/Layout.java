package io.terminus.core.components;

import io.terminus.core.Bounds;
import io.terminus.core.Cell;
import io.terminus.core.Component;
import io.terminus.core.Constraint;
import io.terminus.core.Container;
import io.terminus.core.LayoutAccess;
import io.terminus.core.layout.FlexConfig;
import io.terminus.core.layout.FlexConfig.CrossAxisAlignment;

import java.util.ArrayList;
import java.util.List;

/**
 * A flex container that arranges children in a row or column.
 *
 * PATTERN: Composite (Container) + Strategy (row vs column axis logic)
 *
 * This replaces the LayoutEngine stub with a real implementation
 * for the most common layout case. The three-pass algorithm:
 *
 *   Pass 1 — Measure all FIXED children (flex=0).
 *             Sum their sizes to find total fixed consumption.
 *
 *   Pass 2 — Compute remaining space after fixed children + gaps.
 *             Distribute proportionally among FLEX children.
 *
 *   Pass 3 — Assign final Bounds to every child in order,
 *             advancing the cursor along the main axis.
 *
 * DIRECTION:
 *   ROW    — children arranged left to right (main axis = X)
 *   COLUMN — children arranged top to bottom (main axis = Y)
 */
public class Layout extends Container {

    public enum Direction { ROW, COLUMN }

    // ── Configuration ─────────────────────────────────────────────────────

    private final Direction direction;
    private final int       gap;
    private final int       paddingTop;
    private final int       paddingRight;
    private final int       paddingBottom;
    private final int       paddingLeft;

    // ── Child metadata ────────────────────────────────────────────────────

    /**
     * Parallel list to getChildren() — stores FlexConfig for each child.
     * Index i in configs corresponds to index i in getChildren().
     *
     * WHY A PARALLEL LIST AND NOT A MAP?
     * Insertion order matters in layout. A List preserves order naturally.
     * A Map would require an IdentityHashMap (since components use reference
     * equality) — more complex for no benefit.
     */
    private final List<FlexConfig> configs = new ArrayList<>();

    // ── Constructor ───────────────────────────────────────────────────────

    private Layout(Builder b) {
        this.direction     = b.direction;
        this.gap           = b.gap;
        this.paddingTop    = b.paddingTop;
        this.paddingRight  = b.paddingRight;
        this.paddingBottom = b.paddingBottom;
        this.paddingLeft   = b.paddingLeft;
    }

    // ── Builder ───────────────────────────────────────────────────────────

    public static Builder row()    { return new Builder(Direction.ROW); }
    public static Builder column() { return new Builder(Direction.COLUMN); }

    public static class Builder {
        private final Direction direction;
        private int gap           = 0;
        private int paddingTop    = 0;
        private int paddingRight  = 0;
        private int paddingBottom = 0;
        private int paddingLeft   = 0;

        private Builder(Direction dir) { this.direction = dir; }

        public Builder gap(int gap) {
            this.gap = gap; return this;
        }

        public Builder padding(int all) {
            this.paddingTop = this.paddingRight =
            this.paddingBottom = this.paddingLeft = all;
            return this;
        }

        public Builder padding(int vertical, int horizontal) {
            this.paddingTop    = this.paddingBottom = vertical;
            this.paddingRight  = this.paddingLeft   = horizontal;
            return this;
        }

        public Builder padding(int top, int right, int bottom, int left) {
            this.paddingTop = top; this.paddingRight = right;
            this.paddingBottom = bottom; this.paddingLeft = left;
            return this;
        }

        public Layout build() { return new Layout(this); }
    }

    // ── Child management ──────────────────────────────────────────────────

    /**
     * Add a fixed-size child (uses its measure() result).
     */
    public void add(Component child) {
        add(child, FlexConfig.FIXED);
    }

    /**
     * Add a child with explicit flex configuration.
     *
     * @param child  the component to add
     * @param config how the layout engine should size this child
     */
    public void add(Component child, FlexConfig config) {
        addChild(child);          // Container.addChild() manages parent pointer
        configs.add(config);
    }

    /**
     * Add a flex=1 child — the most common case.
     * "This child should take all remaining space."
     */
    public void addFlex(Component child) {
        add(child, FlexConfig.FLEX);
    }

    /**
     * Add a flex=N child — for proportional sizing.
     */
    public void addFlex(Component child, int factor) {
        add(child, FlexConfig.flex(factor));
    }

    // ── Container.render() — Layout draws nothing itself ──────────────────

    /**
     * Layout containers have no visual of their own.
     * They just position children. The Renderer draws the children.
     *
     * We return a blank grid so the Renderer has something to call
     * render() on without crashing.
     */
    @Override
    public Cell[][] render() {
        int h = Math.max(1, getHeight());
        int w = Math.max(1, getWidth());
        Cell[][] grid = new Cell[h][w];
        for (Cell[] row : grid)
            java.util.Arrays.fill(row, Cell.BLANK);
        return grid;
    }

    // ── measure() — report our preferred size ────────────────────────────

    @Override
    public Bounds measure(Constraint constraint) {
        // Run a "dry" layout to find total preferred size
        // We do this by measuring all children and summing along the main axis
        List<Component> children = getChildren();
        if (children.isEmpty()) {
            return Bounds.of(
                paddingLeft + paddingRight,
                paddingTop  + paddingBottom
            );
        }

        int mainSize  = 0;
        int crossSize = 0;
        int gapTotal  = gap * Math.max(0, children.size() - 1);

        Constraint childConstraint = innerConstraint(constraint);

        for (int i = 0; i < children.size(); i++) {
            Component child  = children.get(i);
            FlexConfig config = configs.get(i);
            if (config.flex() > 0) continue; // skip flex children in measure

            Bounds preferred = child.measure(childConstraint);
            int childMain  = mainAxisSize(preferred);
            int childCross = crossAxisSize(preferred);

            mainSize  += childMain;
            crossSize  = Math.max(crossSize, childCross);
        }

        mainSize += gapTotal;

        // Add padding
        int totalWidth  = direction == Direction.ROW
            ? mainSize  + paddingLeft + paddingRight
            : crossSize + paddingLeft + paddingRight;
        int totalHeight = direction == Direction.COLUMN
            ? mainSize  + paddingTop  + paddingBottom
            : crossSize + paddingTop  + paddingBottom;

        return Bounds.of(totalWidth, totalHeight);
    }

    // ── The real layout engine — three-pass algorithm ─────────────────────

    /**
     * Run the full three-pass layout, assigning Bounds to all children.
     *
     * Called by the LayoutEngine (or parent Layout) after this
     * container's own bounds have been set.
     *
     * NOTE: This is called by the LayoutEngine stub via Container
     * inheritance. We override it here with the real implementation.
     */
    public void performLayout() {
        Bounds myBounds = getBounds();
        List<Component> children = getChildren();

        if (children.isEmpty()) return;

        // Inner area after padding
        int innerX = myBounds.x() + paddingLeft;
        int innerY = myBounds.y() + paddingTop;
        int innerW = Math.max(0, myBounds.width()  - paddingLeft - paddingRight);
        int innerH = Math.max(0, myBounds.height() - paddingTop  - paddingBottom);

        int mainAvailable  = direction == Direction.ROW ? innerW : innerH;
        int crossAvailable = direction == Direction.ROW ? innerH : innerW;
        int gapTotal       = gap * Math.max(0, children.size() - 1);

        Constraint innerConstraint = Constraint.of(innerW, innerH);

        // ── Pass 1: Measure fixed children ───────────────────────────────
        int[] mainSizes   = new int[children.size()];
        int[] crossSizes  = new int[children.size()];
        int   fixedTotal  = 0;
        int   totalFlex   = 0;

        for (int i = 0; i < children.size(); i++) {
            FlexConfig config = configs.get(i);
            if (config.flex() > 0) {
                totalFlex += config.flex();
                continue;
            }
            Bounds preferred = children.get(i).measure(innerConstraint);
            mainSizes[i] = clamp(mainAxisSize(preferred),
                config.minSize(), config.maxSize());

            // STRETCH: fill the full cross axis.
            // All other alignments: use preferred size, clamped to available.
            if (config.crossAxisAlignment() == FlexConfig.CrossAxisAlignment.STRETCH) {
                crossSizes[i] = crossAvailable;
            } else {
                crossSizes[i] = Math.min(crossAxisSize(preferred), crossAvailable);
            }

            fixedTotal += mainSizes[i];
        }

        // ── Pass 2: Distribute remaining space to flex children ──────────
        int remaining = Math.max(0, mainAvailable - fixedTotal - gapTotal);

        for (int i = 0; i < children.size(); i++) {
            FlexConfig config = configs.get(i);
            if (config.flex() == 0) continue;

            int share = (totalFlex > 0)
                ? (remaining * config.flex()) / totalFlex
                : 0;

            mainSizes[i] = clamp(share, config.minSize(), config.maxSize());

            // Apply same STRETCH logic as Pass 1
            if (config.crossAxisAlignment() == FlexConfig.CrossAxisAlignment.STRETCH) {
                crossSizes[i] = crossAvailable;
            } else {
                Bounds preferred = children.get(i).measure(innerConstraint);
                crossSizes[i] = Math.min(crossAxisSize(preferred), crossAvailable);
            }
        }

        // ── Pass 3: Assign final Bounds to each child ────────────────────
        int cursor = 0; // position along the main axis

        for (int i = 0; i < children.size(); i++) {
            Component child = children.get(i);
            FlexConfig config = configs.get(i);

            int mainSize  = mainSizes[i];
            int crossSize = crossSizes[i];

            // Cross-axis position based on alignment
            int crossOffset = computeCrossOffset(
                config.crossAxisAlignment(), crossSize, crossAvailable
            );

            Bounds childBounds;
            if (direction == Direction.ROW) {
                childBounds = new Bounds(
                    innerX + cursor,
                    innerY + crossOffset,
                    mainSize,
                    crossSize
                );
            } else {
                childBounds = new Bounds(
                    innerX + crossOffset,
                    innerY + cursor,
                    crossSize,
                    mainSize
                );
            }

            LayoutAccess.setBounds(child, childBounds);

            // Recurse into nested Layout containers
            if (child instanceof Layout nested) {
                nested.performLayout();
            }

            cursor += mainSize + (i < children.size() - 1 ? gap : 0);
        }
    }

    // ── Axis helpers ──────────────────────────────────────────────────────

    private int mainAxisSize(Bounds b) {
        return direction == Direction.ROW ? b.width() : b.height();
    }

    private int crossAxisSize(Bounds b) {
        return direction == Direction.ROW ? b.height() : b.width();
    }

    private Constraint innerConstraint(Constraint outer) {
        int w = outer.isWidthUnbounded()  ? Constraint.UNBOUNDED
            : Math.max(0, outer.maxWidth()  - paddingLeft - paddingRight);
        int h = outer.isHeightUnbounded() ? Constraint.UNBOUNDED
            : Math.max(0, outer.maxHeight() - paddingTop  - paddingBottom);
        return new Constraint(w, h);
    }

    private int computeCrossOffset(
            FlexConfig.CrossAxisAlignment alignment,
            int childSize, int available) {
        return switch (alignment) {
            case START   -> 0;
            case END     -> Math.max(0, available - childSize);
            case CENTER  -> Math.max(0, (available - childSize) / 2);
            case STRETCH -> 0; // child already sized to fill cross axis
        };
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}