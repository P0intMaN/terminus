package io.terminus.core.components;

/**
 * Visual definition of a table column — header text, width, alignment.
 *
 * Separates the VISUAL definition (how it looks) from the DATA
 * definition (what it contains, in ListTableModel).
 * A ColumnDef knows nothing about data — it only knows about display.
 *
 * WHY NOT PUT THIS ON TableModel?
 * Column definitions are a VIEW concern, not a DATA concern.
 * The same data model could be displayed in a narrow table
 * (fewer, wider columns) or a wide table (many, narrow columns).
 * Separating them lets you change the layout without touching data.
 *
 * PATTERN: Value Object (immutable record)
 */
public record ColumnDef(
    String    header,       // column header text
    int       minWidth,     // minimum column width in chars
    int       maxWidth,     // maximum column width (Integer.MAX_VALUE = unlimited)
    int       flex,         // 0=fixed at minWidth, >0=flex growth factor
    Alignment alignment     // text alignment within the column
) {
    public enum Alignment { LEFT, RIGHT, CENTER }

    public static final int FLEX_NONE = 0;
    public static final int FLEX_1    = 1;

    // ── Factory methods for common patterns ───────────────────────────────

    /** A fixed-width left-aligned column. */
    public static ColumnDef fixed(String header, int width) {
        return new ColumnDef(header, width, width, FLEX_NONE, Alignment.LEFT);
    }

    /** A fixed-width column with explicit alignment. */
    public static ColumnDef fixed(String header, int width, Alignment alignment) {
        return new ColumnDef(header, width, width, FLEX_NONE, alignment);
    }

    /** A flex column that grows to fill remaining space. */
    public static ColumnDef flex(String header, int minWidth) {
        return new ColumnDef(
            header, minWidth, Integer.MAX_VALUE, FLEX_1, Alignment.LEFT);
    }

    /** A flex column with explicit flex factor. */
    public static ColumnDef flex(String header, int minWidth, int flexFactor) {
        return new ColumnDef(
            header, minWidth, Integer.MAX_VALUE, flexFactor, Alignment.LEFT);
    }

    public boolean isFixed() { return flex == FLEX_NONE; }
    public boolean isFlex()  { return flex > FLEX_NONE; }
}