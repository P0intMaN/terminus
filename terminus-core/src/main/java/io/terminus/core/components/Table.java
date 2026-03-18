package io.terminus.core.components;

import io.terminus.core.Bounds;
import io.terminus.core.Cell;
import io.terminus.core.Constraint;
import io.terminus.core.Leaf;
import io.terminus.core.event.Event;
import io.terminus.core.event.KeyEvent;

import java.util.Arrays;
import java.util.Comparator;
import java.util.function.Consumer;

/**
 * A scrollable, sortable data table with keyboard navigation.
 *
 * FEATURES:
 *   - Virtual scrolling: renders only visible rows regardless of dataset size
 *   - Column width distribution: fixed + flex columns via three-pass algorithm
 *   - Keyboard navigation: Up/Down/Home/End/PageUp/PageDown
 *   - Sorting: press 's' or Enter on a column header (TODO: column focus)
 *              currently: 's' sorts by next column, 'S' reverses
 *   - Sort indicators: ▲ / ▼ in column headers
 *   - Scrollbar: visual indicator of position in large datasets
 *   - Selection callback: onSelect fired when selected row changes
 *
 * VIRTUAL SCROLLING EXPLAINED:
 * The table has N rows of data but only H visible rows (component height
 * minus 2 for header + divider). We maintain scrollRow as the index of
 * the first visible row. On each render, we draw rows:
 *   [scrollRow, scrollRow + H)
 * mapped through rowOrder[] for sort ordering.
 *
 * SORT WITHOUT MUTATING DATA:
 * rowOrder[i] = the data index of display row i.
 * Sorting reorders rowOrder, not the data.
 * selectedRow tracks a display row index — always relative to rowOrder.
 *
 * PATTERN: Composite (inherits from Leaf), Command (each key = one action)
 */
public class Table extends Leaf {

    // ── Sort direction ────────────────────────────────────────────────────

    public enum SortDir { NONE, ASC, DESC }

    // ── Configuration ─────────────────────────────────────────────────────

    private final ColumnDef[]        columns;
    private final TableModel         model;
    private final int                headerFg;
    private final int                headerBg;
    private final int                rowFg;
    private final int                rowBg;
    private final int                altRowBg;       // alternating row bg
    private final int                selectedFg;
    private final int                selectedBg;
    private final int                borderFg;
    private final boolean            showScrollbar;
    private final boolean            showRowNumbers;
    private Consumer<Integer>        onSelect;       // receives data row index

    // ── Mutable state ─────────────────────────────────────────────────────

    private int      selectedRow  = 0;    // display row index (in rowOrder)
    private int      scrollRow    = 0;    // first visible display row
    private int      sortColumn   = -1;   // -1 = unsorted
    private SortDir  sortDir      = SortDir.NONE;
    private int[]    rowOrder;            // display→data index mapping
    private boolean  focused      = false;

    // ── Column widths (computed each render when bounds change) ───────────
    private int[]    colWidths;
    private int      lastRenderWidth = -1;

    // ── Constructor ───────────────────────────────────────────────────────

    private Table(Builder b) {
        this.columns        = b.columns;
        this.model          = b.model;
        this.headerFg       = b.headerFg;
        this.headerBg       = b.headerBg;
        this.rowFg          = b.rowFg;
        this.rowBg          = b.rowBg;
        this.altRowBg       = b.altRowBg;
        this.selectedFg     = b.selectedFg;
        this.selectedBg     = b.selectedBg;
        this.borderFg       = b.borderFg;
        this.showScrollbar  = b.showScrollbar;
        this.showRowNumbers = b.showRowNumbers;
        this.onSelect       = b.onSelect;
        rebuildRowOrder();
    }

    // ── Builder ───────────────────────────────────────────────────────────

    public static Builder builder(TableModel model, ColumnDef... columns) {
        return new Builder(model, columns);
    }

    public static class Builder {
        private final TableModel  model;
        private final ColumnDef[] columns;
        private int     headerFg      = 0xAFA9EC;   // purple-200
        private int     headerBg      = 0x26215C;   // purple-900
        private int     rowFg         = 0xF0EFF8;
        private int     rowBg         = Cell.DEFAULT_COLOR;
        private int     altRowBg      = 0x111118;
        private int     selectedFg    = 0x0a0a0f;
        private int     selectedBg    = 0x7F77DD;
        private int     borderFg      = 0x444441;
        private boolean showScrollbar = true;
        private boolean showRowNumbers = false;
        private Consumer<Integer> onSelect = null;

        private Builder(TableModel model, ColumnDef[] columns) {
            this.model   = model;
            this.columns = columns;
        }

        public Builder headerColors(int fg, int bg) {
            this.headerFg = fg; this.headerBg = bg; return this;
        }
        public Builder rowColors(int fg, int bg, int altBg) {
            this.rowFg = fg; this.rowBg = bg; this.altRowBg = altBg; return this;
        }
        public Builder selectedColors(int fg, int bg) {
            this.selectedFg = fg; this.selectedBg = bg; return this;
        }
        public Builder borderFg(int fg)              { this.borderFg = fg; return this; }
        public Builder showScrollbar(boolean show)   { this.showScrollbar = show; return this; }
        public Builder showRowNumbers(boolean show)  { this.showRowNumbers = show; return this; }
        public Builder onSelect(Consumer<Integer> cb){ this.onSelect = cb; return this; }

        public Table build() { return new Table(this); }
    }

    // ── Public API ────────────────────────────────────────────────────────

    public void setFocused(boolean focused) {
        if (this.focused != focused) {
            this.focused = focused;
            markDirty();
        }
    }

    public boolean isFocused() { return focused; }

    /** Call after model data changes to rebuild sort order. */
    public void refresh() {
        rebuildRowOrder();
        clampSelection();
        markDirty();
    }

    /** Returns the currently selected data row index, or -1 if no data. */
    public int getSelectedDataRow() {
        if (rowOrder.length == 0 || selectedRow >= rowOrder.length) return -1;
        return rowOrder[selectedRow];
    }

    public void setOnSelect(Consumer<Integer> cb) { this.onSelect = cb; }

    // ── Event handling ────────────────────────────────────────────────────

    @Override
    public boolean onEvent(Event event) {
        if (!focused) return false;
        if (!(event instanceof KeyEvent k)) return false;

        return switch (k.key()) {
            case "UP"       -> { moveSelection(-1);                yield true; }
            case "DOWN"     -> { moveSelection(1);                 yield true; }
            case "HOME"     -> { jumpToRow(0);                     yield true; }
            case "END"      -> { jumpToRow(rowOrder.length - 1);   yield true; }
            case "PAGE_UP"  -> { moveSelection(-visibleRows());    yield true; }
            case "PAGE_DOWN"-> { moveSelection(visibleRows());     yield true; }
            case "ENTER"    -> { fireOnSelect();                   yield true; }
            default -> {
                if (!k.ctrl() && !k.alt()) {
                    yield switch (k.key()) {
                        // 's' cycles to next sortable column
                        case "s" -> { cycleSortColumn(1);  yield true; }
                        // 'S' (shift+s) reverses sort direction
                        case "S" -> { reverseSortDir();    yield true; }
                        // 'r' resets sort to original order
                        case "r" -> { resetSort();         yield true; }
                        default  -> false;
                    };
                }
                yield false;
            }
        };
    }

    // ── Keyboard actions ──────────────────────────────────────────────────

    private void moveSelection(int delta) {
        if (rowOrder.length == 0) return;
        selectedRow = clamp(selectedRow + delta, 0, rowOrder.length - 1);
        ensureVisible();
        fireOnSelect();
        markDirty();
    }

    private void jumpToRow(int displayRow) {
        if (rowOrder.length == 0) return;
        selectedRow = clamp(displayRow, 0, rowOrder.length - 1);
        ensureVisible();
        fireOnSelect();
        markDirty();
    }

    private void cycleSortColumn(int direction) {
        if (columns.length == 0) return;
        int next = (sortColumn + direction + columns.length) % columns.length;
        sortByColumn(next);
    }

    private void reverseSortDir() {
        if (sortColumn < 0) { sortByColumn(0); return; }
        sortDir = (sortDir == SortDir.ASC) ? SortDir.DESC : SortDir.ASC;
        applySort();
        markDirty();
    }

    private void resetSort() {
        sortColumn = -1;
        sortDir    = SortDir.NONE;
        rebuildRowOrder();
        markDirty();
    }

    private void sortByColumn(int col) {
        if (sortColumn == col) {
            // Same column: cycle NONE → ASC → DESC → NONE
            sortDir = switch (sortDir) {
                case NONE -> SortDir.ASC;
                case ASC  -> SortDir.DESC;
                case DESC -> { sortColumn = -1; yield SortDir.NONE; }
            };
        } else {
            sortColumn = col;
            sortDir    = SortDir.ASC;
        }
        applySort();
        markDirty();
    }

    private void fireOnSelect() {
        if (onSelect != null) {
            int dataRow = getSelectedDataRow();
            if (dataRow >= 0) onSelect.accept(dataRow);
        }
    }

    // ── Sort implementation ───────────────────────────────────────────────

    /**
     * Reset rowOrder to identity mapping [0, 1, 2, ..., n-1].
     * Called when data changes or sort is reset.
     */
    private void rebuildRowOrder() {
        int n = model.getRowCount();
        rowOrder = new int[n];
        for (int i = 0; i < n; i++) rowOrder[i] = i;
        if (sortColumn >= 0 && sortDir != SortDir.NONE) {
            applySort();
        }
    }

    /**
     * Sort rowOrder by the current sort column and direction.
     *
     * WHY NOT SORT THE DATA DIRECTLY?
     * The original data order should be preserved — the user may want
     * to reset sort, or the data may have meaningful original order
     * (insertion order, priority order, etc.).
     * Sorting the view (rowOrder) instead of the data is the correct
     * separation of concerns. Android's SortedList, Java's TableRowSorter,
     * and every serious table widget use this approach.
     *
     * WHY Integer[] FOR SORTING AND int[] FOR STORAGE?
     * Arrays.sort() with a Comparator requires Object arrays (boxed types).
     * We sort an Integer[] then unbox back to int[].
     * The boxing cost is O(n) and happens only on sort — not on every render.
     */
    @SuppressWarnings("unchecked")
    private void applySort() {
        if (sortColumn < 0 || sortDir == SortDir.NONE) return;
        int col = sortColumn;

        Integer[] boxed = new Integer[rowOrder.length];
        for (int i = 0; i < rowOrder.length; i++) boxed[i] = rowOrder[i];

        Comparator<Integer> comparator = (a, b) -> {
            Comparable<Object> valA =
                (Comparable<Object>) model.getSortValue(a, col);
            Comparable<Object> valB =
                (Comparable<Object>) model.getSortValue(b, col);
            int cmp;
            try {
                cmp = valA.compareTo(valB);
            } catch (ClassCastException e) {
                // Fallback to string comparison if types are incompatible
                cmp = valA.toString().compareTo(valB.toString());
            }
            return sortDir == SortDir.DESC ? -cmp : cmp;
        };

        Arrays.sort(boxed, comparator);
        for (int i = 0; i < rowOrder.length; i++) rowOrder[i] = boxed[i];

        // Try to keep the same data row selected after sort
        int selectedDataRow = getSelectedDataRow();
        if (selectedDataRow >= 0) {
            for (int i = 0; i < rowOrder.length; i++) {
                if (rowOrder[i] == selectedDataRow) {
                    selectedRow = i;
                    break;
                }
            }
        }
        ensureVisible();
    }

    // ── Scroll management ─────────────────────────────────────────────────

    /**
     * Ensure the selected row is within the visible viewport.
     * Adjusts scrollRow if necessary.
     */
    private void ensureVisible() {
        int vr = visibleRows();
        if (selectedRow < scrollRow) {
            scrollRow = selectedRow;
        } else if (selectedRow >= scrollRow + vr) {
            scrollRow = selectedRow - vr + 1;
        }
        scrollRow = clamp(scrollRow, 0,
            Math.max(0, rowOrder.length - vr));
    }

    private void clampSelection() {
        if (rowOrder.length == 0) {
            selectedRow = 0; scrollRow = 0; return;
        }
        selectedRow = clamp(selectedRow, 0, rowOrder.length - 1);
        ensureVisible();
    }

    /**
     * Number of data rows visible at one time.
     * Total height minus 2 (header row + divider line).
     */
    private int visibleRows() {
        return Math.max(1, getHeight() - 2);
    }

    // ── Column width distribution ─────────────────────────────────────────

    /**
     * Three-pass column width algorithm — identical to Layout's flex algorithm
     * but applied horizontally to columns.
     *
     * WHY RECOMPUTE ONLY WHEN WIDTH CHANGES?
     * Column widths only change when the terminal is resized.
     * Computing them on every render frame would be wasteful.
     * We cache the result and recompute only when totalWidth changes.
     */
    private void computeColumnWidths(int totalWidth) {
        if (totalWidth == lastRenderWidth && colWidths != null) return;
        lastRenderWidth = totalWidth;

        // Reserve 1 col for scrollbar if shown
        int available = showScrollbar ? totalWidth - 1 : totalWidth;

        colWidths = new int[columns.length];
        int fixedTotal = 0;
        int totalFlex  = 0;

        // Pass 1: measure fixed columns
        for (int i = 0; i < columns.length; i++) {
            if (columns[i].isFixed()) {
                colWidths[i] = columns[i].minWidth();
                fixedTotal  += colWidths[i];
            } else {
                colWidths[i] = columns[i].minWidth(); // minimum for now
                fixedTotal  += colWidths[i];           // counts minimum
                totalFlex   += columns[i].flex();
            }
        }

        // Pass 2: distribute remaining space to flex columns
        int remaining = Math.max(0, available - fixedTotal);
        if (totalFlex > 0 && remaining > 0) {
            for (int i = 0; i < columns.length; i++) {
                if (columns[i].isFlex()) {
                    int extra  = (remaining * columns[i].flex()) / totalFlex;
                    int capped = Math.min(
                        colWidths[i] + extra, columns[i].maxWidth());
                    colWidths[i] = capped;
                }
            }
        }
    }

    // ── Rendering ─────────────────────────────────────────────────────────

    @Override
    public Bounds measure(Constraint c) {
        int minWidth = Arrays.stream(columns)
            .mapToInt(ColumnDef::minWidth).sum()
            + (showScrollbar ? 1 : 0);
        int width  = c.isWidthUnbounded()  ? Math.max(minWidth, 40)
                   : Math.max(minWidth, c.maxWidth());
        int height = c.isHeightUnbounded() ? 10 : Math.max(3, c.maxHeight());
        return Bounds.of(width, height);
    }

    @Override
    public Cell[][] render() {
        Cell[][] grid = blankGrid();
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return grid;

        // Sync row order if model changed
        if (rowOrder == null || rowOrder.length != model.getRowCount()) {
            rebuildRowOrder();
            clampSelection();
        }

        computeColumnWidths(w);

        // Row 0: header
        renderHeader(grid[0], w);

        // Row 1: divider
        if (h > 1) {
            renderDivider(grid[1], w);
        }

        // Rows 2..h-1: data rows
        int vr = visibleRows();
        for (int displayOffset = 0; displayOffset < vr; displayOffset++) {
            int gridRow     = displayOffset + 2;
            if (gridRow >= h) break;

            int displayRow  = scrollRow + displayOffset;
            if (displayRow >= rowOrder.length) break;

            int dataRow     = rowOrder[displayRow];
            boolean isSelected = (displayRow == selectedRow && focused)
                              || (displayRow == selectedRow);

            renderDataRow(grid[gridRow], w, dataRow, displayRow, isSelected);
        }

        // Scrollbar (rightmost column)
        if (showScrollbar && rowOrder.length > vr) {
            renderScrollbar(grid, h, vr);
        }

        return grid;
    }

    // ── Row renderers ─────────────────────────────────────────────────────

    private void renderHeader(Cell[] row, int totalWidth) {
        int col = 0;
        for (int c = 0; c < columns.length; c++) {
            int colW = colWidths[c];
            String headerText = columns[c].header();

            // Sort indicator
            if (c == sortColumn) {
                headerText += (sortDir == SortDir.ASC ? " ▲" : " ▼");
            }

            String cell = padOrTruncate(headerText, colW,
                ColumnDef.Alignment.LEFT);

            for (int i = 0; i < cell.length() && col < totalWidth; i++, col++) {
                row[col] = new Cell(
                    cell.charAt(i), headerFg, headerBg,
                    Cell.ATTR_BOLD, (byte) 1
                );
            }
            // Column separator
            if (c < columns.length - 1 && col < totalWidth) {
                row[col++] = new Cell('│', borderFg,
                    headerBg, Cell.ATTR_NONE, (byte) 1);
            }
        }
        // Fill remaining header cells
        while (col < totalWidth) {
            row[col++] = new Cell(' ', headerFg, headerBg,
                Cell.ATTR_NONE, (byte) 1);
        }
    }

    private void renderDivider(Cell[] row, int totalWidth) {
        int col = 0;
        for (int c = 0; c < columns.length; c++) {
            int colW = colWidths[c];
            for (int i = 0; i < colW && col < totalWidth; i++, col++) {
                row[col] = new Cell('─', borderFg,
                    Cell.DEFAULT_COLOR, Cell.ATTR_NONE, (byte) 1);
            }
            if (c < columns.length - 1 && col < totalWidth) {
                row[col++] = new Cell('┼', borderFg,
                    Cell.DEFAULT_COLOR, Cell.ATTR_NONE, (byte) 1);
            }
        }
        while (col < totalWidth) {
            row[col++] = new Cell('─', borderFg,
                Cell.DEFAULT_COLOR, Cell.ATTR_NONE, (byte) 1);
        }
    }

    private void renderDataRow(Cell[] row, int totalWidth,
                                int dataRow, int displayRow,
                                boolean isSelected) {
        // Alternating row background
        int bg = isSelected ? selectedBg
               : (displayRow % 2 == 0 ? rowBg : altRowBg);
        int fg = isSelected ? selectedFg : rowFg;
        byte attrs = isSelected && focused
            ? Cell.ATTR_BOLD : Cell.ATTR_NONE;

        int col = 0;
        for (int c = 0; c < columns.length; c++) {
            int colW  = colWidths[c];
            String value = model.getCell(dataRow, c);
            String cell  = padOrTruncate(value, colW, columns[c].alignment());

            for (int i = 0; i < cell.length() && col < totalWidth; i++, col++) {
                row[col] = new Cell(cell.charAt(i), fg, bg, attrs, (byte) 1);
            }
            // Column separator
            if (c < columns.length - 1 && col < totalWidth) {
                row[col++] = new Cell('│', borderFg, bg, Cell.ATTR_NONE, (byte) 1);
            }
        }
        // Fill remaining
        while (col < totalWidth) {
            row[col++] = new Cell(' ', fg, bg, Cell.ATTR_NONE, (byte) 1);
        }
    }

    /**
     * Render a proportional scrollbar in the rightmost column.
     *
     * The scrollbar thumb height is proportional to the ratio of
     * visible rows to total rows. Its position is proportional to
     * how far we've scrolled.
     */
    private void renderScrollbar(Cell[][] grid, int totalHeight, int vr) {
        int totalRows   = rowOrder.length;
        int trackHeight = vr; // scrollbar track = visible data rows

        // Thumb height: at least 1 row, proportional to visible/total
        int thumbH = Math.max(1,
            (int) Math.round((double) vr / totalRows * trackHeight));

        // Thumb position: where in the track should the thumb start?
        int thumbTop = (totalRows > vr)
            ? (int) Math.round((double) scrollRow / (totalRows - vr)
                * (trackHeight - thumbH))
            : 0;

        int sbCol = getWidth() - 1;

        for (int r = 0; r < trackHeight; r++) {
            int gridRow = r + 2; // offset by header + divider
            if (gridRow >= totalHeight) break;

            boolean inThumb = (r >= thumbTop && r < thumbTop + thumbH);
            char ch  = inThumb ? '█' : '░';
            int  sbFg = inThumb ? 0x7F77DD : 0x333344;

            if (sbCol < grid[gridRow].length) {
                grid[gridRow][sbCol] = new Cell(
                    ch, sbFg, Cell.DEFAULT_COLOR, Cell.ATTR_NONE, (byte) 1);
            }
        }
    }

    // ── Text utilities ────────────────────────────────────────────────────

    /**
     * Pad or truncate a string to exactly `width` characters.
     * Applies alignment: left-pad spaces for RIGHT, center for CENTER.
     * Truncates with '…' if too long.
     */
    private String padOrTruncate(String text, int width,
                                  ColumnDef.Alignment alignment) {
        if (width <= 0) return "";

        // Truncate if too long
        if (text.length() > width) {
            if (width <= 1) return text.substring(0, width);
            return text.substring(0, width - 1) + "…";
        }

        int pad = width - text.length();
        return switch (alignment) {
            case LEFT   -> text + " ".repeat(pad);
            case RIGHT  -> " ".repeat(pad) + text;
            case CENTER -> {
                int leftPad  = pad / 2;
                int rightPad = pad - leftPad;
                yield " ".repeat(leftPad) + text + " ".repeat(rightPad);
            }
        };
    }

    // ── Utilities ─────────────────────────────────────────────────────────

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}