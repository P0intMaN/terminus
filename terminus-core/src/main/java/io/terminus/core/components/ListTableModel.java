package io.terminus.core.components;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/**
 * A TableModel backed by an in-memory List of typed row objects.
 *
 * This is the most common TableModel implementation.
 * Usage:
 *
 *   record Process(String name, String status, double cpu) {}
 *
 *   ListTableModel<Process> model = ListTableModel.<Process>builder()
 *       .column("Name",   p -> p.name())
 *       .column("Status", p -> p.status())
 *       .column("CPU%",   p -> String.format("%.1f", p.cpu()),
 *                         p -> p.cpu())  // numeric sort
 *       .build();
 *
 *   model.setRows(myProcessList);
 *
 * PATTERN: Builder + Generic type parameter
 * Generic T lets this class work with any row type without
 * requiring a common supertype or reflection.
 *
 * WHY NOT JUST USE String[][] DIRECTLY?
 * A String[][] loses type information — you can't sort numerically,
 * you can't easily update individual rows, and you can't lazily
 * compute display strings. Typed row objects with extractor
 * functions preserve all of that.
 */
public class ListTableModel<T> implements TableModel {

    /**
     * Per-column configuration: display extractor + sort extractor.
     */
    private record ColumnExtractor<T>(
        Function<T, String>     display,
        Function<T, Comparable<?>> sort
    ) {}

    private final List<ColumnExtractor<T>> extractors;
    private List<T> rows = new ArrayList<>();

    private ListTableModel(List<ColumnExtractor<T>> extractors) {
        this.extractors = List.copyOf(extractors);
    }

    // ── Data API ──────────────────────────────────────────────────────────

    /** Replace the entire dataset. Call markDirty() on the Table after this. */
    public void setRows(List<T> rows) {
        this.rows = new ArrayList<>(rows);
    }

    /** Append a single row. Call markDirty() on the Table after this. */
    public void addRow(T row) {
        this.rows.add(row);
    }

    /** Clear all rows. */
    public void clear() {
        this.rows.clear();
    }

    // ── TableModel contract ───────────────────────────────────────────────

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public String getCell(int row, int column) {
        if (row < 0 || row >= rows.size()) return "";
        if (column < 0 || column >= extractors.size()) return "";
        return extractors.get(column).display().apply(rows.get(row));
    }

    @Override
    public Comparable<?> getSortValue(int row, int column) {
        if (row < 0 || row >= rows.size()) return "";
        if (column < 0 || column >= extractors.size()) return "";
        return extractors.get(column).sort().apply(rows.get(row));
    }

    // ── Builder ───────────────────────────────────────────────────────────

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public static class Builder<T> {
        private final List<ColumnExtractor<T>> extractors = new ArrayList<>();

        /** Add a column with string display and string sort. */
        public Builder<T> column(Function<T, String> display) {
            extractors.add(new ColumnExtractor<>(display, display::apply));
            return this;
        }

        /**
         * Add a column with separate display and sort extractors.
         * Use when the display string differs from the sort key
         * (e.g., "128 MB" displays as a string but sorts as a number).
         */
        public Builder<T> column(Function<T, String>        display,
                                  Function<T, Comparable<?>> sort) {
            extractors.add(new ColumnExtractor<>(display, sort));
            return this;
        }

        public ListTableModel<T> build() {
            return new ListTableModel<>(extractors);
        }
    }
}