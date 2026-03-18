package io.terminus.demo;

import io.terminus.core.*;
import io.terminus.core.components.*;
import io.terminus.core.event.Event;
import io.terminus.core.event.KeyEvent;
import io.terminus.core.event.StateChangeEvent;
import io.terminus.core.layout.FlexConfig;
import io.terminus.core.render.RenderPipeline;
import io.terminus.core.terminal.EventDispatcher;
import io.terminus.core.terminal.EventLoop;
import io.terminus.core.terminal.Terminal;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class DemoApp {

    // ── Fake process data ─────────────────────────────────────────────────

    record Process(String name, String status, double cpu,
                   long memoryMb, int pid) {}

    static List<Process> generateProcesses() {
        return List.of(
            new Process("nginx",      "running",  2.1,   128,  892),
            new Process("postgres",   "running",  8.4,   512, 1204),
            new Process("redis",      "running",  0.3,    64, 3301),
            new Process("node",       "stopped",  0.0,     0, 4892),
            new Process("java",       "running", 24.7,  1024, 5521),
            new Process("python3",    "running",  3.2,   256, 6103),
            new Process("docker",     "running",  1.8,   384, 7200),
            new Process("prometheus", "running",  0.9,    96, 8814),
            new Process("grafana",    "running",  1.2,   128, 9001),
            new Process("kafka",      "running", 12.1,  768,  9420),
            new Process("zookeeper",  "running",  0.4,   192, 9421),
            new Process("elasticsearch","running",18.3, 2048, 9800)
        );
    }

    // ── Text label helper ─────────────────────────────────────────────────

    static class TextLabel extends Leaf {
        private String text;
        private final int fg;
        private final byte attrs;

        TextLabel(String text, int fg, byte attrs) {
            this.text = text; this.fg = fg; this.attrs = attrs;
        }
        TextLabel(String text, int fg) { this(text, fg, Cell.ATTR_NONE); }

        public void setText(String text) {
            if (!this.text.equals(text)) { this.text = text; markDirty(); }
        }

        @Override public Cell[][] render() {
            Cell[][] grid = blankGrid();
            writeString(grid, 0, 0, text, fg, Cell.DEFAULT_COLOR, attrs);
            return grid;
        }
        @Override public Bounds measure(Constraint c) {
            return Bounds.of(text.length(), 1);
        }
    }

    // ── Demo root ─────────────────────────────────────────────────────────

    static class TableDemo extends Leaf {

        private final ListTableModel<Process> model;
        private final Table table;
        private final TextLabel statusBar;
        private final TextLabel titleLabel;
        private final Layout root;
        private final Random  rng = new Random();
        private List<Process> processes;

        TableDemo() {
            processes = new ArrayList<>(generateProcesses());

            // ── Build the model ───────────────────────────────────────
            model = ListTableModel.<Process>builder()
                .column(p -> p.name())
                .column(p -> p.status())
                .column(p -> String.format("%5.1f%%", p.cpu()),
                        p -> p.cpu())
                .column(p -> String.format("%6d MB", p.memoryMb()),
                        p -> p.memoryMb())
                .column(p -> String.valueOf(p.pid()),
                        p -> (long) p.pid())
                .build();
            model.setRows(processes);

            // ── Column definitions ────────────────────────────────────
            ColumnDef[] cols = {
                ColumnDef.flex("Name",    14),
                ColumnDef.fixed("Status",  8),
                ColumnDef.fixed("CPU%",    7, ColumnDef.Alignment.RIGHT),
                ColumnDef.fixed("Memory", 10, ColumnDef.Alignment.RIGHT),
                ColumnDef.fixed("PID",     6, ColumnDef.Alignment.RIGHT)
            };

            // ── Initialize labels FIRST — before anything that captures them ──
            titleLabel = new TextLabel(
                " Terminus Process Monitor", 0x7F77DD, Cell.ATTR_BOLD);
            statusBar  = new TextLabel(
                " ↑↓ navigate  s sort  S reverse  r reset  Ctrl+C quit",
                0x888780);

            // ── Build the table (now statusBar is definitely assigned) ────────
            table = Table.builder(model, cols)
                .onSelect(dataRow -> {
                    Process p = processes.get(dataRow);
                    statusBar.setText(String.format(
                        " Selected: %s  (pid %d)  cpu=%.1f%%  mem=%d MB",
                        p.name(), p.pid(), p.cpu(), p.memoryMb()));
                })
                .build();
            table.setFocused(true);

            // ── Layout tree ───────────────────────────────────────────
            root = Layout.column().build();
            root.add(titleLabel);
            root.addFlex(table);
            root.add(statusBar);
        }

        @Override
        public Cell[][] render() {
            Cell[][] grid = blankGrid();
            LayoutAccess.setBounds(root, getBounds());
            root.performLayout();
            renderTree(root, grid);
            return grid;
        }

        private void renderTree(Component comp, Cell[][] grid) {
            if (comp.getBounds().isEmpty()) return;
            if (!(comp instanceof Layout)) {
                Cell[][] sub = comp.render();
                Bounds b = comp.getBounds();
                for (int r = 0; r < sub.length && b.y()+r < grid.length; r++) {
                    for (int c = 0; c < sub[r].length
                            && b.x()+c < grid[b.y()+r].length; c++) {
                        grid[b.y()+r][b.x()+c] = sub[r][c];
                    }
                }
            }
            if (comp instanceof Container ct) {
                for (Component child : ct.getChildren())
                    renderTree(child, grid);
            }
        }

        @Override
        public boolean onEvent(Event event) {
            if (event instanceof StateChangeEvent s
                    && "tick".equals(s.key())) {
                // Simulate fluctuating CPU values
                processes = processes.stream().map(p ->
                    p.status().equals("running")
                        ? new Process(p.name(), p.status(),
                            Math.max(0, p.cpu() + (rng.nextDouble()-0.5)*2),
                            p.memoryMb(), p.pid())
                        : p
                ).toList();
                model.setRows(processes);
                table.refresh();
                markDirty();
                return false;
            }
            return table.onEvent(event);
        }

        @Override
        public Bounds measure(Constraint c) {
            return Bounds.of(c.maxWidth(), c.maxHeight());
        }
    }

    // ── Main ──────────────────────────────────────────────────────────────

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
        TableDemo       root       = new TableDemo();

        Thread timer = Thread.ofVirtual().name("terminus-timer").start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(500); // update every 500ms
                    loop.post(new StateChangeEvent(
                        System.nanoTime(), "tick", null));
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