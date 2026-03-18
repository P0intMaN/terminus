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
    @DisplayName("renderOnly() — compositing with pre-set bounds")
    class RenderOnly {

        @Test
        @DisplayName("renders a single leaf into the back buffer")
        void renders_singleLeaf() {
            FillerLeaf leaf = new FillerLeaf('A', 0xFF0000);
            LayoutAccess.setBounds(leaf, new Bounds(0, 0, 20, 5));

            renderer.renderOnly(leaf);           // ← renderOnly, not renderFrame

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
            LayoutAccess.setBounds(smallLeaf, new Bounds(0, 0, 3, 3));

            renderer.renderOnly(smallLeaf);      // ← renderOnly

            // clearBack() wiped (5,2) before compositing the small leaf
            assertThat(buffer.getBackBuffer()[2][5]).isEqualTo(Cell.BLANK);
        }

        @Test
        @DisplayName("renders children on top of the container")
        void renders_childrenOnTopOfContainer() {
            TestContainer container = new TestContainer();
            FillerLeaf child = new FillerLeaf('C', 0x00FF00);
            container.addChild(child);

            LayoutAccess.setBounds(container, new Bounds(0, 0, 20, 5));
            LayoutAccess.setBounds(child, new Bounds(0, 0, 20, 5));

            renderer.renderOnly(container);      // ← renderOnly

            assertThat(buffer.getBackBuffer()[0][0].glyph()).isEqualTo((int) 'C');
        }

        @Test
        @DisplayName("child at offset renders at the correct global position")
        void child_atOffset_rendersAtGlobalPosition() {
            TestContainer container = new TestContainer();
            FillerLeaf child = new FillerLeaf('X', 0xFFFFFF);
            container.addChild(child);

            LayoutAccess.setBounds(container, new Bounds(0, 0, 20, 5));
            LayoutAccess.setBounds(child, new Bounds(10, 2, 5, 1));

            renderer.renderOnly(container);      // ← renderOnly

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
            LayoutAccess.setBounds(leaf, new Bounds(0, 0, 20, 5));
            assertThat(leaf.isDirty()).isTrue();

            renderer.renderOnly(leaf);           // ← renderOnly

            assertThat(leaf.isDirty()).isFalse();
        }

        @Test
        @DisplayName("skips components with empty bounds — render() never called")
        void skips_emptyBounds() {
            FillerLeaf leaf = new FillerLeaf('A', 0xFF0000);
            // No setBounds — stays at Bounds.ZERO

            assertThatCode(() -> renderer.renderOnly(leaf))
                .doesNotThrowAnyException();

            assertThat(leaf.getRenderCallCount()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("renderFrame() — full layout + render pipeline")
    class RenderFrame {

        @Test
        @DisplayName("full pipeline assigns bounds and renders the root")
        void fullPipeline_assignsBoundsAndRenders() {
            FillerLeaf leaf = new FillerLeaf('F', 0xFFFFFF);
            // Note: no setBounds here — renderFrame() runs layout which assigns them

            renderer.renderFrame(leaf);

            // Layout stub gives root the full terminal size (20×5)
            // so every cell should be 'F'
            Cell[][] back = buffer.getBackBuffer();
            assertThat(back[0][0].glyph()).isEqualTo((int) 'F');
            assertThat(back[4][19].glyph()).isEqualTo((int) 'F');
        }
    }
}