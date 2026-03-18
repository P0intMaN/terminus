package io.terminus.demo;

import io.terminus.core.*;
import io.terminus.core.components.Layout;
import io.terminus.core.components.ProgressBar;
import io.terminus.core.components.TextInput;
import io.terminus.core.event.Event;
import io.terminus.core.event.KeyEvent;
import io.terminus.core.event.StateChangeEvent;
import io.terminus.core.layout.FlexConfig;
import io.terminus.core.render.RenderPipeline;
import io.terminus.core.terminal.EventDispatcher;
import io.terminus.core.terminal.EventLoop;
import io.terminus.core.terminal.Terminal;

public class DemoApp {

    /**
     * A simple Text leaf — renders a single line of styled text.
     * We'll build a proper Text component in Step 9,
     * but this stub is enough for the demo.
     */
    static class TextLabel extends Leaf {
        private final String text;
        private final int    fg;
        private final byte   attrs;

        TextLabel(String text, int fg, byte attrs) {
            this.text = text; this.fg = fg; this.attrs = attrs;
        }

        TextLabel(String text, int fg) { this(text, fg, Cell.ATTR_NONE); }

        @Override
        public Cell[][] render() {
            Cell[][] grid = blankGrid();
            writeString(grid, 0, 0, text, fg, Cell.DEFAULT_COLOR, attrs);
            return grid;
        }

        @Override
        public Bounds measure(Constraint c) {
            return Bounds.of(text.length(), 1);
        }
    }

    static class DemoRoot extends Leaf {

        // Inputs
        private final TextInput searchInput = TextInput.builder()
            .placeholder("Search... (Enter to submit)")
            .build();

        private final TextInput nameInput = TextInput.builder()
            .placeholder("Your name...")
            .fg(0x1D9E75)
            .maxLength(20)
            .build();

        // Progress bars
        private final ProgressBar bar1 = ProgressBar.builder()
            .style(ProgressBar.Style.EIGHTHS).fg(0x7F77DD)
            .label("Tasks").build();

        private final ProgressBar bar2 = ProgressBar.builder()
            .style(ProgressBar.Style.BLOCK).fg(0x1D9E75)
            .label("Memory").build();

        private final ProgressBar bar3 = ProgressBar.builder()
            .style(ProgressBar.Style.ASCII).fg(0xEF9F27)
            .label("CPU").build();

        // Build the layout tree once
        private final Layout root;
        private int focusedField = 0;
        private double tick = 0;
        private final java.util.List<String> log = new java.util.ArrayList<>();

        DemoRoot() {
            searchInput.setFocused(true);
            searchInput.setOnSubmit(text -> {
                log.add(0, "> " + text);
                if (log.size() > 3) log.remove(log.size() - 1);
                markDirty();
            });

            // ── Build the layout tree ──────────────────────────────────
            // column
            //   header row
            //   divider
            //   search row [label | input (flex)]
            //   name row   [label | input (flex)]
            //   hint
            //   divider
            //   progress bars (column)
            //   divider
            //   log entries

            Layout searchRow = Layout.row().gap(1).build();
            searchRow.add(new TextLabel("Search:", 0x888780));
            searchRow.addFlex(searchInput);

            Layout nameRow = Layout.row().gap(1).build();
            nameRow.add(new TextLabel("Name:  ", 0x888780));
            nameRow.addFlex(nameInput);

            Layout bars = Layout.column().gap(1).build();
            bars.add(bar1);
            bars.add(bar2);
            bars.add(bar3);

            root = Layout.column().gap(0).padding(1, 2).build();
            root.add(new TextLabel(
                "Terminus TUI Demo", 0x7F77DD, Cell.ATTR_BOLD));
            root.add(new TextLabel(
                "─────────────────────────────────────────", 0x333344));
            root.add(searchRow);
            root.add(nameRow);
            root.add(new TextLabel(
                "Tab to switch fields  •  Enter to submit", 0x444455));
            root.add(new TextLabel(
                "─────────────────────────────────────────", 0x333344));
            root.add(bars);
            root.add(new TextLabel(
                "─────────────────────────────────────────", 0x333344));
        }

        @Override
        public Bounds measure(Constraint c) {
            return Bounds.of(c.maxWidth(), c.maxHeight());
        }

        @Override
        public Cell[][] render() {
            Cell[][] grid = blankGrid();

            // Run layout at our current bounds
            LayoutAccess.setBounds(root, getBounds());
            root.performLayout();

            // Render each leaf in the tree into our grid
            renderTree(root, grid);

            // Draw log entries below the layout
            int logY = root.getBounds().height() + 1;
            for (int i = 0; i < log.size(); i++) {
                writeString(grid, logY + i, 2, log.get(i),
                    0xF0EFF8, Cell.DEFAULT_COLOR, Cell.ATTR_NONE);
            }

            return grid;
        }

        /**
         * Recursively render a component tree into our cell grid.
         * This is a local mini-renderer — it works because we OWN
         * the grid and all components are positioned within our bounds.
         */
        private void renderTree(Component comp, Cell[][] grid) {
            if (comp.getBounds().isEmpty()) return;
            if (!(comp instanceof Layout)) {
                Cell[][] sub = comp.render();
                Bounds b = comp.getBounds();
                for (int r = 0; r < sub.length; r++) {
                    int targetRow = b.y() + r;
                    if (targetRow >= grid.length) break;
                    for (int c = 0; c < sub[r].length; c++) {
                        int targetCol = b.x() + c;
                        if (targetCol >= grid[targetRow].length) break;
                        grid[targetRow][targetCol] = sub[r][c];
                    }
                }
            }
            if (comp instanceof Container container) {
                for (Component child : container.getChildren()) {
                    renderTree(child, grid);
                }
            }
        }

        @Override
        public boolean onEvent(Event event) {
            if (event instanceof StateChangeEvent s) {
                if ("tick".equals(s.key())) {
                    tick += 0.004;
                    bar1.setValue(tick % 1.0);
                    bar2.setValue((tick * 0.7) % 1.0);
                    bar3.setValue((tick * 1.3) % 1.0);
                    markDirty();
                    return false;
                }
                if ("blink".equals(s.key())) {
                    searchInput.onEvent(event);
                    nameInput.onEvent(event);
                    return false;
                }
            }

            if (event instanceof KeyEvent k && "TAB".equals(k.key())) {
                focusedField = 1 - focusedField;
                searchInput.setFocused(focusedField == 0);
                nameInput.setFocused(focusedField == 1);
                markDirty();
                return true;
            }

            return focusedField == 0
                ? searchInput.onEvent(event)
                : nameInput.onEvent(event);
        }
    }

    public static void main(String[] args) {
        if (!Terminal.isRealTerminal()) {
            System.err.println("[Terminus] Run via: java --enable-preview " +
                "-jar terminus-demo/build/libs/terminus-demo.jar");
            System.exit(1);
        }

        int[] size          = Terminal.getSize();
        RenderPipeline  pipeline   = new RenderPipeline(size[0], size[1]);
        EventDispatcher dispatcher = new EventDispatcher();
        EventLoop       loop       = new EventLoop(pipeline, dispatcher);
        DemoRoot        root       = new DemoRoot();

        long[] lastBlink = { System.currentTimeMillis() };

        Thread timer = Thread.ofVirtual().name("terminus-timer").start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(16);
                    long now = System.currentTimeMillis();
                    loop.post(new StateChangeEvent(now, "tick", null));
                    if (now - lastBlink[0] >= 530) {
                        loop.post(new StateChangeEvent(now, "blink", null));
                        lastBlink[0] = now;
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
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