package io.terminus.core.render;

import io.terminus.core.Cell;

/**
 * A double-buffered grid of Cells representing the terminal screen.
 *
 * PATTERN: Double Buffer (from Game Programming Patterns by Robert Nystrom)
 * We keep two full-screen Cell grids:
 *   - backBuffer:  the frame currently being composed by the Renderer
 *   - frontBuffer: the frame last written to the terminal
 *
 * After compositing, the ScreenDiffer compares back vs front.
 * After diffing, we swap: back becomes front, front becomes the new back.
 *
 * WHY SWAP INSTEAD OF COPY?
 * Copying a full screen grid every frame (e.g. 200×50 = 10,000 cells)
 * is wasteful. Swapping just exchanges two references — O(1) regardless
 * of screen size. The old front buffer becomes the new back buffer and
 * gets overwritten in place next frame.
 *
 * WHY NOT ONE BUFFER?
 * If we read and write the same buffer simultaneously (read for diff,
 * write for next frame), we'd see torn state — half old frame, half
 * new. Double buffering eliminates this class of bug entirely.
 */
public class ScreenBuffer {

    private Cell[][] frontBuffer;
    private Cell[][] backBuffer;

    private int cols;
    private int rows;

    /**
     * Create a ScreenBuffer for the given terminal dimensions.
     *
     * Both buffers are initialized to BLANK cells — a clean slate
     * that the first diff will compare against.
     */
    public ScreenBuffer(int cols, int rows) {
        this.cols = cols;
        this.rows = rows;
        this.frontBuffer = allocate(cols, rows);
        this.backBuffer  = allocate(cols, rows);
    }

    // ── Back buffer write API (used by Renderer) ──────────────────────────

    /**
     * Write a single cell into the back buffer at (col, row).
     *
     * Out-of-bounds writes are silently ignored — components are
     * clipped to screen boundaries. This is safer than throwing:
     * a component that renders slightly outside its bounds shouldn't
     * crash the entire UI.
     *
     * WHY SILENT CLIP INSTEAD OF EXCEPTION?
     * In a TUI framework, partial renders are common during resize events
     * (the terminal reports a new size but the layout hasn't caught up yet).
     * Crashing here would make the app unrecoverable. Clipping is correct.
     */
    public void setCell(int col, int row, Cell cell) {
        if (row < 0 || row >= rows || col < 0 || col >= cols) return;
        backBuffer[row][col] = cell;
    }

    /**
     * Write an entire local cell grid into the back buffer,
     * offset to global screen position (originCol, originRow).
     *
     * This is the primary method the Renderer calls — it takes a
     * component's local Cell[][] and composites it into the global screen.
     *
     * @param grid      the component's local cell output
     * @param originCol the component's x position in global screen space
     * @param originRow the component's y position in global screen space
     */
    public void composite(Cell[][] grid, int originCol, int originRow) {
        for (int localRow = 0; localRow < grid.length; localRow++) {
            for (int localCol = 0; localCol < grid[localRow].length; localCol++) {
                int screenCol = originCol + localCol;
                int screenRow = originRow + localRow;
                Cell cell = grid[localRow][localCol];
                // setCell handles clipping — no bounds check needed here
                setCell(screenCol, screenRow, cell);
            }
        }
    }

    /**
     * Fill the entire back buffer with BLANK cells.
     *
     * Called at the START of each render pass, before any component
     * writes its cells. This ensures stale cells from a previous frame
     * don't "show through" if a component shrinks or disappears.
     *
     * WHY CLEAR THE WHOLE BUFFER?
     * Alternative: only clear regions where dirty components live.
     * That optimization is valid but complex. Full clear is correct,
     * simple, and fast enough for terminal-sized grids (rarely > 220×60).
     */
    public void clearBack() {
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                backBuffer[row][col] = Cell.BLANK;
            }
        }
    }

    // ── Buffer access (used by ScreenDiffer) ──────────────────────────────

    /**
     * Returns the back buffer — the frame currently being composed.
     * The ScreenDiffer reads this after the Renderer finishes.
     *
     * WARNING: Do not hold a reference to this across a swap().
     * After swap(), this reference points to the (now recycled) old front.
     */
    public Cell[][] getBackBuffer()  { return backBuffer; }

    /**
     * Returns the front buffer — the last frame sent to the terminal.
     * The ScreenDiffer compares this against the back buffer.
     */
    public Cell[][] getFrontBuffer() { return frontBuffer; }

    // ── Swap ──────────────────────────────────────────────────────────────

    /**
     * Swap back and front buffers.
     *
     * Called AFTER the ScreenDiffer has read both buffers and emitted
     * ANSI sequences. The back buffer (just rendered) becomes the new
     * front (it IS now what the terminal shows). The old front becomes
     * the new back (ready to be overwritten next frame).
     *
     * This is O(1) — just two reference swaps. No copying.
     */
    public void swap() {
        Cell[][] temp = frontBuffer;
        frontBuffer   = backBuffer;
        backBuffer    = temp;
    }

    // ── Resize ────────────────────────────────────────────────────────────

    /**
     * Resize both buffers to match a new terminal size.
     *
     * Called when a ResizeEvent is received from the EventLoop.
     * Existing cell data is discarded — after a resize, the layout
     * engine re-runs and a full redraw happens anyway.
     *
     * WHY REALLOCATE INSTEAD OF REUSE?
     * Terminal resizes are rare (user manually drags the window).
     * Correctness over micro-optimization here — fresh allocation
     * guarantees no stale data at the new dimensions.
     */
    public void resize(int newCols, int newRows) {
        this.cols = newCols;
        this.rows = newRows;
        this.frontBuffer = allocate(newCols, newRows);
        this.backBuffer  = allocate(newCols, newRows);
    }

    // ── Accessors ─────────────────────────────────────────────────────────

    public int getCols() { return cols; }
    public int getRows() { return rows; }

    /**
     * Get a specific cell from the front buffer.
     * Used by tests and the ScreenDiffer for debugging/inspection.
     */
    public Cell getFrontCell(int col, int row) {
        if (row < 0 || row >= rows || col < 0 || col >= cols) {
            return Cell.BLANK;
        }
        return frontBuffer[row][col];
    }

    // ── Private helpers ───────────────────────────────────────────────────

    /**
     * Allocate a new rows×cols Cell grid filled with BLANK.
     *
     * WHY Cell[][] AND NOT Cell[]?
     * A flat array (Cell[rows * cols]) is slightly faster for cache
     * locality, but Cell[][] makes the row/col mental model explicit
     * in the code. For terminal sizes, the difference is negligible.
     * Clarity wins.
     */
    private Cell[][] allocate(int cols, int rows) {
        Cell[][] grid = new Cell[rows][cols];
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                grid[row][col] = Cell.BLANK;
            }
        }
        return grid;
    }
}