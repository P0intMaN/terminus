package io.terminus.core;

/**
 * A Component with no children. Renders itself directly.
 *
 * All visual primitives extend Leaf:
 *   Text, ProgressBar, Sparkline, TextInput, etc.
 *
 * WHY AN ABSTRACT CLASS AND NOT JUST COMPONENT?
 * 1. It explicitly documents intent: "this component will never have children"
 * 2. It can provide shared helper utilities for leaf rendering
 *    (e.g., padding a cell grid to exact bounds size)
 * 3. It prevents the accidental creation of a component that is neither
 *    a true leaf nor a proper container
 *
 * Subclasses must implement:
 *   - render()   → produce the visual Cell[][]
 *   - measure()  → report preferred size
 *
 * Subclasses MAY override:
 *   - onEvent()  → if the component handles input (TextInput, Button)
 */
public abstract class Leaf extends Component {

    /**
     * Utility: create an empty cell grid of exactly the current bounds size.
     *
     * WHY IS THIS HERE?
     * Every leaf starts its render() by allocating a grid and filling
     * it with BLANK cells. If we don't provide this helper, every
     * subclass writes the same boilerplate loop. Extract once, use always.
     *
     * Called as the first line of most render() implementations:
     *   Cell[][] grid = blankGrid();
     */
    protected Cell[][] blankGrid() {
        int w = Math.max(1, getWidth());
        int h = Math.max(1, getHeight());
        Cell[][] grid = new Cell[h][w];
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                grid[row][col] = Cell.BLANK;
            }
        }
        return grid;
    }

    /**
     * Utility: safely write a string into a cell grid row, clipping to width.
     *
     * Terminal rendering means we MUST clip — strings longer than the
     * component width would overflow into adjacent components' cells.
     * This utility handles clipping and color application in one place.
     *
     * @param grid    the cell grid to write into
     * @param row     which row to write to
     * @param col     starting column
     * @param text    the string to write
     * @param fg      foreground color (Cell.DEFAULT_COLOR for terminal default)
     * @param bg      background color (Cell.DEFAULT_COLOR for terminal default)
     * @param attrs   style bit flags (Cell.ATTR_NONE for plain)
     */
    protected void writeString(Cell[][] grid, int row, int col,
                                String text, int fg, int bg, byte attrs) {
        if (row < 0 || row >= grid.length) return;
        int maxCol = grid[row].length;
        int[] codepoints = text.codePoints().toArray();

        for (int i = 0; i < codepoints.length && col < maxCol; i++) {
            int cp = codepoints[i];
            // Determine display width: 2 for wide chars (CJK/emoji), 1 otherwise
            byte cellWidth = isWideChar(cp) ? (byte) 2 : (byte) 1;

            if (cellWidth == 2 && col + 1 >= maxCol) break; // no room for wide char

            grid[row][col] = new Cell(cp, fg, bg, attrs, cellWidth);
            col++;

            // Wide characters occupy 2 columns; fill second col with BLANK
            // so the differ doesn't try to render the second "half"
            if (cellWidth == 2 && col < maxCol) {
                grid[row][col] = Cell.BLANK;
                col++;
            }
        }
    }

    /**
     * Returns true if the Unicode codepoint is a "wide" character that
     * occupies 2 terminal columns (East Asian Width: Wide or Fullwidth).
     *
     * This is a simplified check. A production implementation would use
     * a full Unicode East Asian Width table (ICU4J or hand-rolled).
     */
    private boolean isWideChar(int codepoint) {
        // CJK Unified Ideographs block
        if (codepoint >= 0x4E00 && codepoint <= 0x9FFF) return true;
        // CJK Extension A
        if (codepoint >= 0x3400 && codepoint <= 0x4DBF) return true;
        // Fullwidth ASCII variants
        if (codepoint >= 0xFF01 && codepoint <= 0xFF60) return true;
        // Common wide emoji range (simplified)
        if (codepoint >= 0x1F300 && codepoint <= 0x1F9FF) return true;
        return false;
    }
}