package io.terminus.core.components;

/**
 * The data interface between a Table and its backing data source.
 *
 * PATTERN: Repository / Data Source
 * The Table never knows whether data comes from a List, a database,
 * a network call, or a live process monitor. It only calls these
 * three methods. This decoupling means:
 *   - You can swap data sources without touching the Table
 *   - You can page data from a database without loading everything
 *   - You can push live updates by calling table.refresh()
 *
 * WHY AN INTERFACE AND NOT AN ABSTRACT CLASS?
 * An interface declares a contract with no implementation bias.
 * Any existing class (ArrayList wrapper, JDBC ResultSet adapter,
 * etc.) can implement TableModel without extending a base class.
 * Java allows implementing multiple interfaces — this maximises
 * flexibility for callers.
 *
 * THREAD SAFETY: getRowCount() and getCell() may be called from
 * the UI thread during render(). If your data is updated from a
 * background thread, either synchronize access or post a
 * StateChangeEvent and update from onEvent() on the UI thread.
 */
public interface TableModel {

    /**
     * Total number of rows in the dataset.
     * Called every render frame — must be O(1).
     */
    int getRowCount();

    /**
     * The display value for a specific cell.
     *
     * @param row    0-indexed row in the ORIGINAL data (before sort)
     * @param column 0-indexed column
     * @return display string — never null (return "" for empty cells)
     */
    String getCell(int row, int column);

    /**
     * The raw comparable value for sorting a column.
     *
     * The Table calls this during sort to compare rows.
     * Return a Comparable that reflects the natural sort order
     * of the column — e.g. Long for numeric columns so "100" sorts
     * after "99" rather than between "10" and "11" (lexicographic).
     *
     * DEFAULT: returns getCell(row, column) — string sort.
     * Override for numeric, date, or custom sort orders.
     *
     * @param row    0-indexed row in the ORIGINAL data
     * @param column 0-indexed column
     */
    default Comparable<?> getSortValue(int row, int column) {
        return getCell(row, column);
    }
}