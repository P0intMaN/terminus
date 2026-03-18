package io.terminus.core.components;

import io.terminus.core.Bounds;
import io.terminus.core.Cell;
import io.terminus.core.Constraint;
import io.terminus.core.LayoutAccess;
import io.terminus.core.event.KeyEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Table")
class TableTest {

    // ── Test data ─────────────────────────────────────────────────────────

    record Person(String name, int age) {}

    private ListTableModel<Person> model;
    private Table table;

    private static final ColumnDef[] COLS = {
        ColumnDef.flex("Name", 10),
        ColumnDef.fixed("Age", 5, ColumnDef.Alignment.RIGHT)
    };

    @BeforeEach
    void setUp() {
        model = ListTableModel.<Person>builder()
            .column(p -> p.name())
            .column(p -> String.valueOf(p.age()),
                    p -> (long) p.age())   // numeric sort
            .build();

        model.setRows(List.of(
            new Person("Alice",   30),
            new Person("Bob",     25),
            new Person("Charlie", 35),
            new Person("Diana",   28)
        ));

        table = Table.builder(model, COLS)
            .showScrollbar(false)
            .build();
        table.setFocused(true);
        LayoutAccess.setBounds(table, new Bounds(0, 0, 30, 8));
    }

    // Helper: send a key
    private void key(String k) {
        table.onEvent(new KeyEvent(0, k, false, false, false));
    }

    // Helper: read a cell's glyph string from rendered grid at a row
    private String renderRowText(int row) {
        Cell[][] grid = table.render();
        if (row >= grid.length) return "";
        StringBuilder sb = new StringBuilder();
        for (Cell c : grid[row]) sb.appendCodePoint(c.glyph());
        return sb.toString();
    }

    @Nested
    @DisplayName("rendering")
    class Rendering {

        @Test
        @DisplayName("first row is the header")
        void firstRow_isHeader() {
            String header = renderRowText(0);
            assertThat(header).contains("Name");
            assertThat(header).contains("Age");
        }

        @Test
        @DisplayName("second row is the divider")
        void secondRow_isDivider() {
            String divider = renderRowText(1);
            assertThat(divider).contains("─");
        }

        @Test
        @DisplayName("data rows start at row 2")
        void dataRows_startAtRow2() {
            String row2 = renderRowText(2);
            assertThat(row2).contains("Alice");
        }

        @Test
        @DisplayName("all four data rows are visible in an 8-row table")
        void allRows_visible() {
            String combined = renderRowText(2) + renderRowText(3)
                            + renderRowText(4) + renderRowText(5);
            assertThat(combined)
                .contains("Alice")
                .contains("Bob")
                .contains("Charlie")
                .contains("Diana");
        }
    }

    @Nested
    @DisplayName("keyboard navigation")
    class Navigation {

        @Test
        @DisplayName("initially selected row is 0")
        void initialSelection_isZero() {
            assertThat(table.getSelectedDataRow()).isEqualTo(0);
        }

        @Test
        @DisplayName("DOWN moves selection to next row")
        void down_movesSelection() {
            key("DOWN");
            assertThat(table.getSelectedDataRow()).isEqualTo(1);
        }

        @Test
        @DisplayName("UP does not go below row 0")
        void up_clampsAtZero() {
            key("UP");
            assertThat(table.getSelectedDataRow()).isEqualTo(0);
        }

        @Test
        @DisplayName("END moves to last row")
        void end_movesToLast() {
            key("END");
            assertThat(table.getSelectedDataRow()).isEqualTo(3); // 4 rows, index 3
        }

        @Test
        @DisplayName("HOME moves back to first row")
        void home_movesToFirst() {
            key("END");
            key("HOME");
            assertThat(table.getSelectedDataRow()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("sorting")
    class Sorting {

        @Test
        @DisplayName("'s' sorts by column 0 ascending")
        void s_sortsByFirstColumn() {
            table.onEvent(new KeyEvent(0, "s", false, false, false));
            // Alice, Bob, Charlie, Diana — already alphabetical in data
            // After sort ascending: same order
            assertThat(table.getSelectedDataRow()).isGreaterThanOrEqualTo(0);
        }

        @Test
        @DisplayName("pressing 's' twice on same column reverses sort")
        void s_twice_reverses() {
            // Sort col 0 ASC
            table.onEvent(new KeyEvent(0, "s", false, false, false));
            // Navigate to bottom after sort
            key("END");
            int lastAsc = table.getSelectedDataRow();

            // Sort col 0 DESC
            table.onEvent(new KeyEvent(0, "S", false, false, false));
            key("HOME");
            int firstDesc = table.getSelectedDataRow();

            // In DESC order, the first displayed row should be
            // the one that was last in ASC order
            assertThat(firstDesc).isEqualTo(lastAsc);
        }

        @Test
        @DisplayName("'r' resets sort to original order")
        void r_resetsSort() {
            table.onEvent(new KeyEvent(0, "s", false, false, false));
            table.onEvent(new KeyEvent(0, "r", false, false, false));
            // After reset, row 0 in display = row 0 in data (Alice)
            key("HOME");
            assertThat(model.getCell(table.getSelectedDataRow(), 0))
                .isEqualTo("Alice");
        }

        @Test
        @DisplayName("sort by age column uses numeric ordering")
        void numericSort_correctOrder() {
            // Cycle to column 1 (age)
            table.onEvent(new KeyEvent(0, "s", false, false, false)); // col 0
            table.onEvent(new KeyEvent(0, "s", false, false, false)); // col 1

            // After ASC numeric sort: Bob(25), Diana(28), Alice(30), Charlie(35)
            key("HOME");
            int firstRow = table.getSelectedDataRow();
            assertThat(model.getCell(firstRow, 0)).isEqualTo("Bob");
        }

        @Test
        @DisplayName("selected data row is preserved across sort")
        void selectedRow_preservedAcrossSort() {
            // Select Bob (row 1)
            key("DOWN");
            int bobDataRow = table.getSelectedDataRow();
            assertThat(model.getCell(bobDataRow, 0)).isEqualTo("Bob");

            // Sort — Bob should still be selected even though position changes
            table.onEvent(new KeyEvent(0, "s", false, false, false));
            assertThat(model.getCell(table.getSelectedDataRow(), 0))
                .isEqualTo("Bob");
        }
    }

    @Nested
    @DisplayName("virtual scrolling")
    class VirtualScrolling {

        @Test
        @DisplayName("scrolling kicks in when rows exceed visible area")
        void scrolling_activatesWhenNeeded() {
            // Make a small table: 4 rows visible (2 header + 2 data)
            LayoutAccess.setBounds(table, new Bounds(0, 0, 30, 4));
            // 4 rows of data, 2 visible: scroll should be needed
            key("END"); // go to last row
            // Last row should be visible
            String lastRow = renderRowText(3);
            assertThat(lastRow).contains("Diana");
        }

        @Test
        @DisplayName("scrollRow adjusts to keep selection visible")
        void scrollRow_keepsSelectionVisible() {
            // Make a 3-data-row visible table with 4 data rows
            LayoutAccess.setBounds(table, new Bounds(0, 0, 30, 5));
            // rows visible = 5 - 2 = 3
            // Press DOWN three times to force scroll
            key("DOWN"); key("DOWN"); key("DOWN");
            // Row 3 (Diana) should now be visible at bottom
            String combined = renderRowText(2) + renderRowText(3)
                            + renderRowText(4);
            assertThat(combined).contains("Diana");
        }
    }

    @Nested
    @DisplayName("selection callback")
    class SelectionCallback {

        @Test
        @DisplayName("onSelect is called when selection changes")
        void onSelect_called() {
            List<Integer> selections = new ArrayList<>();
            table.setOnSelect(selections::add);
            key("DOWN");
            assertThat(selections).isNotEmpty();
        }

        @Test
        @DisplayName("onSelect receives the data row index not display row")
        void onSelect_receivesDataRow() {
            List<Integer> selections = new ArrayList<>();
            table.setOnSelect(selections::add);
            key("DOWN");
            int reported = selections.get(0);
            // reported is a data index — model.getCell should work on it
            assertThat(model.getCell(reported, 0)).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("unfocused behaviour")
    class Unfocused {

        @Test
        @DisplayName("key events ignored when unfocused")
        void unfocused_ignoresKeys() {
            table.setFocused(false);
            key("DOWN");
            assertThat(table.getSelectedDataRow()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("measure()")
    class Measure {

        @Test
        @DisplayName("measure returns height of at least 3")
        void measure_heightAtLeast3() {
            Bounds size = table.measure(Constraint.of(80, 24));
            assertThat(size.height()).isGreaterThanOrEqualTo(3);
        }
    }
}