package io.terminus.core.render;

import io.terminus.core.Bounds;
import io.terminus.core.Cell;
import io.terminus.core.Component;
import io.terminus.core.Constraint;
import io.terminus.core.Container;
import io.terminus.core.LayoutAccess;
import io.terminus.core.Leaf;
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
        public Cell[][] render() {
            // Containers render a blank grid — children paint on top
            int h = Math.max(1, getHeight());
            int w = Math.max(1, getWidth());
            Cell[][] grid = new Cell[h][w];
            for (Cell[] row : grid) java.util.Arrays.fill(row, Cell.BLANK);
            return grid;
        }

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
            LayoutAccess.setBounds(leaf, new Bounds(0, 0, 20, 5)); // ← fixed

            renderer.renderFrame(leaf);

            Cell[][] back = buffer.getBackBuffer();
            assertThat(back[0][0].glyph()).isEqualTo((int) 'A');
            assertThat(back[0][0].fg()).isEqualTo(0xFF0000);
            assertThat(back[4][19].glyph()).isEqualTo((int) 'A');
        }

        @Test
        @DisplayName("clears back buffer before rendering — stale cells are gone")
        void clearsBackBuffer_beforeRender() {
            buffer.setCell(5, 2, Cell.of('Z', 0x0000FF));

            FillerLeaf smallLeaf = new FillerLeaf('A', 0xFF0000);
            LayoutAccess.setBounds(smallLeaf, new Bounds(0, 0, 3, 3)); // ← fixed

            renderer.renderFrame(smallLeaf);

            assertThat(buffer.getBackBuffer()[2][5]).isEqualTo(Cell.BLANK);
        }

        @Test
        @DisplayName("renders children on top of the container")
        void renders_childrenOnTopOfContainer() {
            TestContainer container = new TestContainer();
            FillerLeaf child = new FillerLeaf('C', 0x00FF00);
            container.addChild(child);

            LayoutAccess.setBounds(container, new Bounds(0, 0, 20, 5)); // ← fixed
            LayoutAccess.setBounds(child, new Bounds(0, 0, 20, 5));     // ← fixed

            renderer.renderFrame(container);

            assertThat(buffer.getBackBuffer()[0][0].glyph()).isEqualTo((int) 'C');
        }

        @Test
        @DisplayName("child at offset renders at the correct global position")
        void child_atOffset_rendersAtGlobalPosition() {
            TestContainer container = new TestContainer();
            FillerLeaf child = new FillerLeaf('X', 0xFFFFFF);
            container.addChild(child);

            LayoutAccess.setBounds(container, new Bounds(0, 0, 20, 5));  // ← fixed
            LayoutAccess.setBounds(child, new Bounds(10, 2, 5, 1));      // ← fixed

            renderer.renderFrame(container);

            Cell[][] back = buffer.getBackBuffer();
            for (int col = 10; col < 15; col++) {
                assertThat(back[2][col].glyph())
                    .as("col %d should be 'X'", col)
                    .isEqualTo((int) 'X');
            }
            assertThat(back[2][9]).isEqualTo(Cell.BLANK);
            assertThat(back[2][15]).isEqualTo(Cell.BLANK);
        }

        @Test
        @DisplayName("render() clears the dirty flag on each component")
        void render_clearsDirtyFlag() {
            FillerLeaf leaf = new FillerLeaf('A', 0xFF0000);
            LayoutAccess.setBounds(leaf, new Bounds(0, 0, 20, 5)); // ← fixed
            assertThat(leaf.isDirty()).isTrue();

            renderer.renderFrame(leaf);

            assertThat(leaf.isDirty()).isFalse();
        }

        @Test
        @DisplayName("skips components with empty bounds")
        void skips_emptyBounds() {
            FillerLeaf leaf = new FillerLeaf('A', 0xFF0000);
            // No setBounds call — stays at Bounds.ZERO

            assertThatCode(() -> renderer.renderFrame(leaf))
                .doesNotThrowAnyException();

            assertThat(leaf.getRenderCallCount()).isEqualTo(0);
        }
    }
}