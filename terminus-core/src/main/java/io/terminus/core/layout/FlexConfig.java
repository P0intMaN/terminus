package io.terminus.core.layout;

/**
 * Per-child layout configuration for the flex layout engine.
 *
 * When you add a child to a Layout container, you optionally
 * provide a FlexConfig that tells the layout engine how to
 * treat that child during the three-pass layout algorithm.
 *
 * WHY A SEPARATE CLASS AND NOT PROPERTIES ON COMPONENT?
 * Layout config is a concern of the CONTAINER, not the COMPONENT.
 * A ProgressBar doesn't know (or care) whether it's in a row or
 * column, or whether it should flex. The container is the one that
 * decides. Keeping layout config separate lets the same component
 * be used in different layouts with different sizing behaviour.
 *
 * This is the same design Flutter uses (BoxDecoration, Flexible,
 * Expanded are all wrapper/config objects, not properties of Widget).
 *
 * PATTERN: Value Object (immutable record)
 */
public record FlexConfig(
    /**
     * Flex growth factor.
     * 0 = fixed size (use measure() result)
     * 1 = grow to fill remaining space
     * 2 = grow twice as fast as flex=1 children
     *
     * Example: two children both with flex=1 split space equally.
     * One with flex=2 and one with flex=1 split space 2:1.
     */
    int flex,

    /**
     * Cross-axis alignment.
     * In a ROW layout, cross-axis is vertical (height).
     * In a COLUMN layout, cross-axis is horizontal (width).
     */
    CrossAxisAlignment crossAxisAlignment,

    /** Minimum size in the main axis. 0 = no minimum. */
    int minSize,

    /** Maximum size in the main axis. Integer.MAX_VALUE = no maximum. */
    int maxSize
) {
    public enum CrossAxisAlignment {
        START,   // align to top (row) or left (column)
        CENTER,  // center on the cross axis
        END,     // align to bottom (row) or right (column)
        STRETCH  // fill the full cross-axis size (default)
    }

    /** Fixed size child — uses measure() result, no flex growth. */
    public static final FlexConfig FIXED = new FlexConfig(
        0, CrossAxisAlignment.STRETCH, 0, Integer.MAX_VALUE
    );

    /** Flex child — grows to fill remaining space. */
    public static FlexConfig flex(int factor) {
        return new FlexConfig(
            factor, CrossAxisAlignment.STRETCH, 0, Integer.MAX_VALUE
        );
    }

    /** Flex=1 shorthand — the most common case. */
    public static final FlexConfig FLEX = flex(1);

    /** Convenience: fixed size with specific cross-axis alignment. */
    public static FlexConfig fixed(CrossAxisAlignment alignment) {
        return new FlexConfig(0, alignment, 0, Integer.MAX_VALUE);
    }
}