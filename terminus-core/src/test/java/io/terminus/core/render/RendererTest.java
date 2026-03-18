package io.terminus.core.render;

import io.terminus.core.*;
import io.terminus.core.event.Event;
import io.terminus.core.layout.LayoutEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Renderer")
class RendererTest {

    // ── Test doubles ──────────────────────────────────────────────────────

    /**
     * A leaf that fills its entire bounds with a single character.
     * Makes it easy to assert "did this component render here?"
     */
    static class FillerLeaf extends Leaf {
        private final char fillChar;
        private final int fg;
        private int renderCallCount = 0;

        FillerLeaf(char fillChar, int fg) {
            this.fillChar = fillChar;
            this.fg = fg;
        }

        @Override
        public Cell[][] render() {
            renderCallCount++;
            Cell[][] grid = blankGrid();
            for (Cell[] row : grid) {
                for (int c = 0; c < row.length; c++) {
                    row[c] = Cell.of(fillChar, fg);
                }
            }
            return grid;
        }

        @Override
        public Bounds measure(Constraint c) {
            return Bounds.of(c.maxWidth(), c.maxHeight());
        }

        int getRenderCallCount() { return renderCallCount; }
    }

    static class TestContainer extends Container {
        @Override
        public Cell[][] render() { return new Cell[getHeight()][getWidth()]; }

        @Override
        public Bounds measure(Constraint c) {
            return Bounds.of(c.maxWidth(), c.maxHeight());
        }
    }

    private ScreenBuffer buffer;
    private Renderer renderer;
    private LayoutEngine layoutEngine;

    @BeforeEach
    void setUp() {
        buffer       = new ScreenBuffer(20, 5);
        layoutEngine = new LayoutEngine();
        renderer     = new Renderer(buffer, layoutEngine);
    }

    @Nested
    @DisplayName("renderFrame()")
    class RenderFrame {

        @Test
        @DisplayName("renders a single leaf into the back buffer")
        void renders_singleLeaf() {
            FillerLeaf leaf = new FillerLeaf('A', 0xFF0000);
            leaf.setBounds(new Bounds(0, 0, 20, 5));

            renderer.renderFrame(leaf);

            Cell[][] back = buffer.getBackBuffer();
            // Every cell should be 'A' with red fg
            assertThat(back[0][0].glyph()).isEqualTo((int) 'A');
            assertThat(back[0][0].fg()).isEqualTo(0xFF0000);
            assertThat(back[4][19].glyph()).isEqualTo((int) 'A');
        }

        @Test
        @DisplayName("clears back buffer before rendering — stale cells are gone")
        void clearsBackBuffer_beforeRender() {
            // Manually write a stale cell into the back buffer
            buffer.setCell(5, 2, Cell.of('Z', 0x0000FF));

            // Render a leaf that only covers (0,0,3,3)
            FillerLeaf smallLeaf = new FillerLeaf('A', 0xFF0000);
            smallLeaf.setBounds(new Bounds(0, 0, 3, 3));

            renderer.renderFrame(smallLeaf);

            // The stale 'Z' at (5,2) should be gone — clearBack() wiped it
            assertThat(buffer.getBackBuffer()[2][5]).isEqualTo(Cell.BLANK);
        }

        @Test
        @DisplayName("renders children on top of the container")
        void renders_childrenOnTopOfContainer() {
            TestContainer container = new TestContainer();
            FillerLeaf child = new FillerLeaf('C', 0x00FF00);
            container.addChild(child);

            // Layout: container gets full screen, child gets full screen
            container.setBounds(new Bounds(0, 0, 20, 5));
            child.setBounds(new Bounds(0, 0, 20, 5));

            renderer.renderFrame(container);

            // Child's 'C' cells should be in the buffer
            assertThat(buffer.getBackBuffer()[0][0].glyph()).isEqualTo((int) 'C');
        }

        @Test
        @DisplayName("child at offset renders at the correct global position")
        void child_atOffset_rendersAtGlobalPosition() {
            TestContainer container = new TestContainer();
            // A 5-wide, 1-tall leaf positioned at column 10
            FillerLeaf child = new FillerLeaf('X', 0xFFFFFF);
            container.addChild(child);

            container.setBounds(new Bounds(0, 0, 20, 5));
            child.setBounds(new Bounds(10, 2, 5, 1)); // offset x=10, y=2

            renderer.renderFrame(container);

            Cell[][] back = buffer.getBackBuffer();
            // cols 10-14, row 2 should be 'X'
            for (int col = 10; col < 15; col++) {
                assertThat(back[2][col].glyph())
                    .as("col %d should be 'X'", col)
                    .isEqualTo((int) 'X');
            }
            // col 9 (just before) should be blank
            assertThat(back[2][9]).isEqualTo(Cell.BLANK);
            // col 15 (just after) should be blank
            assertThat(back[2][15]).isEqualTo(Cell.BLANK);
        }

        @Test
        @DisplayName("render() clears the dirty flag on each component")
        void render_clearsDirtyFlag() {
            FillerLeaf leaf = new FillerLeaf('A', 0xFF0000);
            leaf.setBounds(new Bounds(0, 0, 20, 5));
            assertThat(leaf.isDirty()).isTrue(); // starts dirty

            renderer.renderFrame(leaf);

            assertThat(leaf.isDirty()).isFalse(); // cleared after render
        }

        @Test
        @DisplayName("skips components with empty bounds")
        void skips_emptyBounds() {
            FillerLeaf leaf = new FillerLeaf('A', 0xFF0000);
            // Bounds.ZERO — not yet laid out
            // leaf.setBounds NOT called — stays at ZERO

            assertThatCode(() -> renderer.renderFrame(leaf))
                .doesNotThrowAnyException();

            assertThat(leaf.getRenderCallCount()).isEqualTo(0);
        }
    }
}