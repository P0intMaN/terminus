package io.terminus.core.render;

import io.terminus.core.Cell;
import java.util.Arrays;

/**
 * Compares two Cell[][] grids and produces the minimal ANSI
 * escape sequence string needed to transform one into the other.
 *
 * PATTERN: This is a specialised form of the Myers diff algorithm —
 * but applied to a 2D grid rather than text lines. Because terminal
 * cells are positional (not sequential like text), our diff is
 * simpler: we compare cell-by-cell at fixed coordinates.
 *
 * THE CORE OPTIMIZATION — CURSOR TRACKING:
 * Moving the cursor costs bytes: ESC[{row};{col}H is 6-10 bytes.
 * If two changed cells are adjacent in the same row, we don't need
 * to emit a cursor move for the second one — after writing the first
 * character, the cursor is already at the next column.
 *
 * We track the "expected cursor position" as we emit sequences.
 * If the cursor is already where we need it, we skip the move.
 * This optimization alone can halve the output size on dense updates.
 *
 * THREAD SAFETY: Not thread-safe. Call only from the UI thread.
 */
public class ScreenDiffer {

    /**
     * Compute the ANSI sequences to transform the front buffer
     * into the back buffer.
     *
     * After calling this, the caller should:
     *   1. Write the returned string to stdout (via AnsiWriter)
     *   2. Call screenBuffer.swap() so front becomes the new "current"
     *
     * @param front the currently displayed frame
     * @param back  the desired next frame
     * @param rows  number of rows in both buffers
     * @param cols  number of columns in both buffers
     * @return ANSI escape sequence string — empty string if no changes
     */
    public String diff(Cell[][] front, Cell[][] back, int rows, int cols) {
        StringBuilder sb = new StringBuilder();

        // Hide cursor during update — prevents the cursor from visibly
        // jumping around the screen as we move it to write each cell.
        sb.append(Ansi.HIDE_CURSOR);

        // Track where the cursor currently is after our last write.
        // Start at (-1, -1) meaning "unknown" — force a move on first write.
        int cursorRow = -1;
        int cursorCol = -1;

        for (int row = 0; row < rows; row++) {

            // ROW-LEVEL OPTIMIZATION:
            // Arrays.equals() on a Cell[] is a JVM intrinsic — very fast.
            // If the entire row is unchanged, skip all per-cell work.
            // On a typical frame, 90%+ of rows are unchanged.
            if (Arrays.equals(front[row], back[row])) continue;

            for (int col = 0; col < cols; col++) {
                Cell prevCell = front[row][col];
                Cell nextCell = back[row][col];

                // Cell unchanged — skip it. The cursor position does NOT
                // advance here because we're not writing anything.
                // This means after a skip, we MUST emit a cursor move
                // before the next write — handled by the position check below.
                if (prevCell.equals(nextCell)) continue;

                // CURSOR POSITIONING:
                // Are we already at (row, col)? If so, no move needed.
                // This happens when the previous iteration wrote the cell
                // immediately to the left of this one (same row, col-1).
                boolean cursorAlreadyHere =
                    (cursorRow == row && cursorCol == col);

                if (!cursorAlreadyHere) {
                    sb.append(Ansi.moveTo(row, col));
                }

                // SGR: reset + new attributes + colors
                sb.append(Ansi.sgrFor(nextCell.fg(), nextCell.bg(), nextCell.attrs()));

                // The actual character — use codePointAt-aware conversion
                // so wide chars (CJK, emoji) render as their full Unicode form.
                sb.appendCodePoint(nextCell.glyph());

                // After writing one character, the terminal cursor advances
                // one column automatically. For normal (width=1) chars, we're
                // now at (row, col+1). For wide (width=2) chars, we're at
                // (row, col+2) — but we also need to account for the "phantom"
                // second column that the wide char occupies.
                cursorRow = row;
                cursorCol = col + nextCell.width(); // width is 1 or 2
            }
        }

        // Restore cursor visibility.
        sb.append(Ansi.SHOW_CURSOR);

        // If nothing changed, we still emit HIDE+SHOW cursor, which is
        // harmless but slightly wasteful. Optimization: check if we
        // wrote anything between HIDE and SHOW, and return "" if not.
        // We implement this check efficiently:
        if (sb.length() == Ansi.HIDE_CURSOR.length() + Ansi.SHOW_CURSOR.length()) {
            return ""; // nothing changed — don't write anything
        }

        return sb.toString();
    }

    /**
     * Produce the ANSI sequence for a full initial clear + redraw.
     *
     * Called on the very first frame, or after a terminal resize,
     * when we can't trust the terminal's current state at all.
     *
     * This clears the screen and positions the cursor at home.
     * The subsequent normal diff() call will then write every cell.
     */
    public String fullClear() {
        return Ansi.CLEAR_SCREEN + Ansi.CURSOR_HOME + Ansi.RESET;
    }
}