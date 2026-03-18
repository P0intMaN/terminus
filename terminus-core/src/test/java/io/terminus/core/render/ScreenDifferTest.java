package io.terminus.core.render;

import io.terminus.core.Cell;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ScreenDiffer")
class ScreenDifferTest {

    private ScreenDiffer differ;

    // 5 cols × 3 rows screen for tests
    private static final int COLS = 5;
    private static final int ROWS = 3;

    @BeforeEach
    void setUp() {
        differ = new ScreenDiffer();
    }

    /** Build a blank Cell[][] of given dimensions. */
    private Cell[][] blank(int cols, int rows) {
        Cell[][] grid = new Cell[rows][cols];
        for (int r = 0; r < rows; r++)
            for (int c = 0; c < cols; c++)
                grid[r][c] = Cell.BLANK;
        return grid;
    }

    @Nested
    @DisplayName("diff()")
    class Diff {

        @Test
        @DisplayName("identical buffers produce empty string — no output")
        void identicalBuffers_emptyOutput() {
            Cell[][] front = blank(COLS, ROWS);
            Cell[][] back  = blank(COLS, ROWS);

            String result = differ.diff(front, back, ROWS, COLS);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("single changed cell produces cursor move + SGR + glyph")
        void singleChange_producesMoveAndGlyph() {
            Cell[][] front = blank(COLS, ROWS);
            Cell[][] back  = blank(COLS, ROWS);
            back[1][2] = Cell.of('X', 0xFF0000); // changed cell at row 1, col 2

            String result = differ.diff(front, back, ROWS, COLS);

            // Must contain a cursor move to row=1, col=2 (ANSI: row 2, col 3)
            assertThat(result).contains("\033[2;3H");
            // Must contain the character
            assertThat(result).contains("X");
            // Must contain red foreground
            assertThat(result).contains("\033[38;2;255;0;0m");
        }

        @Test
        @DisplayName("adjacent changed cells share cursor position — only one move emitted")
        void adjacentChanges_oneCursorMove() {
            Cell[][] front = blank(COLS, ROWS);
            Cell[][] back  = blank(COLS, ROWS);
            // Two adjacent cells in row 0 — cols 0 and 1
            back[0][0] = Cell.of('A');
            back[0][1] = Cell.of('B');

            String result = differ.diff(front, back, ROWS, COLS);

            // Should contain cursor move to (0,0) — ANSI (1,1)
            assertThat(result).contains("\033[1;1H");
            assertThat(result).contains("A");
            assertThat(result).contains("B");

            // Should NOT contain a second cursor move to (0,1) — ANSI (1,2)
            // because after writing 'A', the cursor is already at col 1
            assertThat(result).doesNotContain("\033[1;2H");
        }

        @Test
        @DisplayName("unchanged cells are skipped — no sequence emitted for them")
        void unchangedCells_skipped() {
            Cell[][] front = blank(COLS, ROWS);
            Cell[][] back  = blank(COLS, ROWS);
            // Only change cell (0,4) — the last column
            back[0][4] = Cell.of('Z');

            String result = differ.diff(front, back, ROWS, COLS);

            // Cursor must jump directly to (0,4) — ANSI (1,5)
            assertThat(result).contains("\033[1;5H");
            // Must NOT contain cursor positions for cols 0-3
            assertThat(result).doesNotContain("\033[1;1H");
            assertThat(result).doesNotContain("\033[1;2H");
            assertThat(result).doesNotContain("\033[1;3H");
            assertThat(result).doesNotContain("\033[1;4H");
        }

        @Test
        @DisplayName("diff contains HIDE_CURSOR at start and SHOW_CURSOR at end")
        void wrapsCursorHideShow_whenChangesExist() {
            Cell[][] front = blank(COLS, ROWS);
            Cell[][] back  = blank(COLS, ROWS);
            back[0][0] = Cell.of('A');

            String result = differ.diff(front, back, ROWS, COLS);

            assertThat(result).startsWith(Ansi.HIDE_CURSOR);
            assertThat(result).endsWith(Ansi.SHOW_CURSOR);
        }

        @Test
        @DisplayName("cell changed back to BLANK — RESET with space character emitted")
        void cellChangedToBlank_emitsResetAndSpace() {
            Cell[][] front = blank(COLS, ROWS);
            Cell[][] back  = blank(COLS, ROWS);
            // Front has a red 'X', back is blank (erasing it)
            front[0][0] = Cell.of('X', 0xFF0000);
            back[0][0]  = Cell.BLANK;

            String result = differ.diff(front, back, ROWS, COLS);

            // Must emit a reset (to clear the red color)
            assertThat(result).contains(Ansi.RESET);
            // Must emit a space (the BLANK glyph)
            assertThat(result).contains(" ");
        }
    }
}