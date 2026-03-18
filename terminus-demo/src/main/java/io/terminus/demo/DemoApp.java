package io.terminus.demo;

import io.terminus.core.*;
import io.terminus.core.event.Event;
import io.terminus.core.event.KeyEvent;

/**
 * The first live Terminus application.
 *
 * What it does:
 *   - Displays a message in the center of the terminal
 *   - Shows the last key you pressed
 *   - Press Ctrl+C or Ctrl+Q to quit
 *
 * This proves the entire pipeline works end-to-end:
 * keystroke → KeyParser → EventLoop → component → render → screen
 */
public class DemoApp {

    /**
     * A simple leaf that displays a message and the last key pressed.
     * This is the simplest possible Terminus component.
     */
    static class HelloComponent extends Leaf {

        private String lastKey = "(press any key)";

        @Override
        public Cell[][] render() {
            Cell[][] grid = blankGrid();

            String line1 = "  Terminus is alive!  ";
            String line2 = "  Last key: " + lastKey;
            String line3 = "  Press Ctrl+C to quit  ";

            // Write each line at a fixed row
            // Purple color: 0x7F77DD
            writeString(grid, 1, 2, line1, 0x7F77DD, Cell.DEFAULT_COLOR, Cell.ATTR_BOLD);
            writeString(grid, 3, 2, line2, 0xFFFFFF, Cell.DEFAULT_COLOR, Cell.ATTR_NONE);
            writeString(grid, 5, 2, line3, 0x888780, Cell.DEFAULT_COLOR, Cell.ATTR_NONE);

            return grid;
        }

        @Override
        public Bounds measure(Constraint c) {
            return Bounds.of(c.maxWidth(), c.maxHeight());
        }

        @Override
        public boolean onEvent(Event event) {
            if (event instanceof KeyEvent k) {
                lastKey = buildKeyLabel(k);
                markDirty(); // trigger re-render
                return true; // consumed
            }
            return false;
        }

        private String buildKeyLabel(KeyEvent k) {
            StringBuilder sb = new StringBuilder();
            if (k.ctrl())  sb.append("Ctrl+");
            if (k.alt())   sb.append("Alt+");
            if (k.shift()) sb.append("Shift+");
            sb.append(k.key());
            return sb.toString();
        }
    }

    public static void main(String[] args) {
        TerminusApp.run(new HelloComponent());
    }
}