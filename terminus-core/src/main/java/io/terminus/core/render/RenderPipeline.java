package io.terminus.core.render;

import io.terminus.core.Component;
import io.terminus.core.layout.LayoutEngine;

/**
 * Orchestrates the full render pipeline in the correct sequence:
 *
 *   1. Renderer    — walks the component tree → populates back buffer
 *   2. ScreenDiffer — diffs back vs front      → produces ANSI string
 *   3. AnsiWriter  — writes ANSI string        → terminal updated
 *   4. ScreenBuffer.swap() — back becomes front for next frame
 *
 * PATTERN: Facade
 * The EventLoop only needs to call pipeline.renderFrame(root).
 * It doesn't need to know about buffers, differs, or writers.
 * This keeps the EventLoop focused on its own concern: the event loop.
 *
 * PATTERN: Template Method (implicitly)
 * The pipeline sequence is fixed. Individual steps can be replaced
 * (e.g., swap AnsiWriter for a test recorder) via the constructor.
 */
public class RenderPipeline {

    private final ScreenBuffer   screenBuffer;
    private final Renderer       renderer;
    private final ScreenDiffer   differ;
    private final AnsiWriter     writer;

    private boolean firstFrame = true;

    public RenderPipeline(int cols, int rows) {
        this(cols, rows, AnsiWriter.toStdout());
    }

    /**
     * Constructor with injectable AnsiWriter — used by tests to
     * capture output without writing to a real terminal.
     */
    public RenderPipeline(int cols, int rows, AnsiWriter writer) {
        this.screenBuffer = new ScreenBuffer(cols, rows);
        this.renderer     = new Renderer(screenBuffer, new LayoutEngine());
        this.differ       = new ScreenDiffer();
        this.writer       = writer;
    }

    /**
     * Run the full render pipeline for one frame.
     *
     * Sequence:
     *   1. On first frame: clear the terminal completely
     *   2. Run layout + render into the back buffer
     *   3. Diff back vs front
     *   4. Write the diff to the terminal
     *   5. Swap buffers
     *
     * @param root the root component of the UI tree
     */
    public void renderFrame(Component root) {
        if (firstFrame) {
            writer.clearScreen();
            firstFrame = false;
        }

        // Step 1+2: layout and render into back buffer
        renderer.renderFrame(root);

        // Step 3: diff back vs front
        String ansiDiff = differ.diff(
            screenBuffer.getFrontBuffer(),
            screenBuffer.getBackBuffer(),
            screenBuffer.getRows(),
            screenBuffer.getCols()
        );

        // Step 4: write to terminal
        writer.write(ansiDiff);

        // Step 5: swap — back is now the "current" frame
        screenBuffer.swap();
    }

    /**
     * Handle a terminal resize event.
     * Reallocates buffers, forces a full redraw on next frame.
     */
    public void resize(int newCols, int newRows) {
        screenBuffer.resize(newCols, newRows);
        firstFrame = true; // force full clear on next frame
    }

    /** Shutdown: reset terminal attributes and show cursor. */
    public void shutdown() {
        writer.reset();
    }

    // ── Accessors for testing ─────────────────────────────────────────────

    public ScreenBuffer getScreenBuffer() { return screenBuffer; }
    public int getCols() { return screenBuffer.getCols(); }
    public int getRows() { return screenBuffer.getRows(); }
}