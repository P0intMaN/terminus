package io.terminus.demo;

import io.terminus.core.*;
import io.terminus.core.components.ProgressBar;
import io.terminus.core.event.Event;
import io.terminus.core.event.StateChangeEvent;
import io.terminus.core.terminal.EventLoop;
import io.terminus.core.terminal.Terminal;
import io.terminus.core.render.RenderPipeline;
import io.terminus.core.terminal.EventDispatcher;

public class DemoApp {

    static class ProgressDemo extends Leaf {

        private final ProgressBar eighthsBar = ProgressBar.builder()
            .style(ProgressBar.Style.EIGHTHS)
            .fg(0x7F77DD).label("Downloading").build();

        private final ProgressBar blockBar = ProgressBar.builder()
            .style(ProgressBar.Style.BLOCK)
            .fg(0x1D9E75).label("Extracting").build();

        private final ProgressBar asciiBar = ProgressBar.builder()
            .style(ProgressBar.Style.ASCII)
            .fg(0xEF9F27).label("Installing").build();

        private final ProgressBar brailleBar = ProgressBar.builder()
            .style(ProgressBar.Style.BRAILLE)
            .fg(0xD4537E).label("Verifying").build();

        private double progress = 0.0;

        @Override
        public Cell[][] render() {
            // render() is PURE — only reads state, never mutates it
            Cell[][] grid = blankGrid();
            int w = getWidth();

            writeString(grid, 0, 2,
                "Terminus ProgressBar Demo",
                0x7F77DD, Cell.DEFAULT_COLOR, Cell.ATTR_BOLD);

            writeString(grid, 1, 2,
                "─".repeat(Math.min(w - 4, 40)),
                0x444441, Cell.DEFAULT_COLOR, Cell.ATTR_NONE);

            renderSubBar(grid, 3, eighthsBar, progress);
            renderSubBar(grid, 5, blockBar,   Math.min(1.0, progress * 1.2));
            renderSubBar(grid, 7, asciiBar,   Math.min(1.0, progress * 0.8));
            renderSubBar(grid, 9, brailleBar, Math.min(1.0, progress * 1.5));

            writeString(grid, 11, 2,
                "Press Ctrl+C to quit",
                0x555566, Cell.DEFAULT_COLOR, Cell.ATTR_NONE);

            return grid;
        }

        private void renderSubBar(Cell[][] grid, int row,
                                   ProgressBar bar, double value) {
            if (row >= grid.length) return;
            int barWidth = Math.min(getWidth() - 4, 50);
            bar.setValue(value);
            LayoutAccess.setBounds(bar, new Bounds(0, 0, barWidth, 1));
            Cell[][] barGrid = bar.render();
            if (barGrid.length > 0) {
                int copyWidth = Math.min(barWidth, grid[row].length - 2);
                System.arraycopy(barGrid[0], 0, grid[row], 2, copyWidth);
            }
        }

        @Override
        public boolean onEvent(Event event) {
            // StateChangeEvent with key "tick" drives the animation
            if (event instanceof StateChangeEvent s
                    && "tick".equals(s.key())) {
                progress += 0.005;
                if (progress > 1.0) progress = 0.0;
                markDirty(); // NOW it's correct — mutation in onEvent, not render
                return true;
            }
            return false;
        }

        @Override
        public Bounds measure(Constraint c) {
            return Bounds.of(c.maxWidth(), c.maxHeight());
        }
    }

    public static void main(String[] args) {
        if (!Terminal.isRealTerminal()) {
            System.err.println("[Terminus] Run via: " +
                "java --enable-preview -jar terminus-demo/build/libs/terminus-demo.jar");
            System.exit(1);
        }

        int[] size       = Terminal.getSize();
        RenderPipeline  pipeline   = new RenderPipeline(size[0], size[1]);
        EventDispatcher dispatcher = new EventDispatcher();
        EventLoop       loop       = new EventLoop(pipeline, dispatcher);

        ProgressDemo root = new ProgressDemo();

        // Start a background timer that posts tick events at ~60fps
        // This is the correct way to drive animation — from outside render()
        Thread timer = Thread.ofVirtual()
            .name("terminus-animation-timer")
            .start(() -> {
                while (!Thread.currentThread().isInterrupted()) {
                    try {
                        Thread.sleep(16); // ~60fps
                        loop.post(new StateChangeEvent(
                            System.nanoTime(), "tick", null));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            });

        try {
            loop.start(root);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            timer.interrupt();
        }
    }
}