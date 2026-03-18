package io.terminus.core.render;

import io.terminus.core.Cell;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ScreenBuffer")
class ScreenBufferTest {

    private ScreenBuffer buffer;

    @BeforeEach
    void setUp() {
        buffer = new ScreenBuffer(10, 5); // 10 cols, 5 rows
    }

    @Nested
    @DisplayName("setCell()")
    class SetCell {

        @Test
        @DisplayName("writes a cell at the correct position in the back buffer")
        void writesCell_atCorrectPosition() {
            Cell red = Cell.of('X', 0xFF0000);
            buffer.setCell(3, 2, red);

            assertThat(buffer.getBackBuffer()[2][3]).isEqualTo(red);
        }

        @Test
        @DisplayName("silently ignores out-of-bounds writes")
        void outOfBounds_isSilentlyIgnored() {
            // Should not throw
            assertThatCode(() -> {
                buffer.setCell(-1, 0, Cell.of('A'));
                buffer.setCell(0, -1, Cell.of('A'));
                buffer.setCell(10, 0, Cell.of('A')); // col == width (exclusive)
                buffer.setCell(0, 5, Cell.of('A'));  // row == height (exclusive)
            }).doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("composite()")
    class Composite {

        @Test
        @DisplayName("writes a local grid at the correct global offset")
        void writesGrid_atGlobalOffset() {
            // A 2×2 grid of 'Z' cells
            Cell[][] grid = {
                { Cell.of('Z'), Cell.of('Z') },
                { Cell.of('Z'), Cell.of('Z') }
            };

            // Composite at origin (3, 1)
            buffer.composite(grid, 3, 1);

            Cell[][] back = buffer.getBackBuffer();
            // Check cells are at global positions (col+3, row+1)
            assertThat(back[1][3].glyph()).isEqualTo((int) 'Z');
            assertThat(back[1][4].glyph()).isEqualTo((int) 'Z');
            assertThat(back[2][3].glyph()).isEqualTo((int) 'Z');
            assertThat(back[2][4].glyph()).isEqualTo((int) 'Z');

            // Cells outside the grid are still BLANK
            assertThat(back[0][3]).isEqualTo(Cell.BLANK);
            assertThat(back[1][2]).isEqualTo(Cell.BLANK);
        }

        @Test
        @DisplayName("clips correctly when grid extends beyond screen edge")
        void clipsWhenGridExceedsScreenBounds() {
            // Place a 4×2 grid at col=8, which would extend to col=11 (out of bounds)
            Cell[][] grid = {
                { Cell.of('A'), Cell.of('B'), Cell.of('C'), Cell.of('D') },
                { Cell.of('E'), Cell.of('F'), Cell.of('G'), Cell.of('H') }
            };

            // Should not throw — clips to screen edge
            assertThatCode(() -> buffer.composite(grid, 8, 0))
                .doesNotThrowAnyException();

            Cell[][] back = buffer.getBackBuffer();
            // Only A and B fit (cols 8 and 9); C and D are clipped
            assertThat(back[0][8].glyph()).isEqualTo((int) 'A');
            assertThat(back[0][9].glyph()).isEqualTo((int) 'B');
        }
    }

    @Nested
    @DisplayName("clearBack()")
    class ClearBack {

        @Test
        @DisplayName("fills the entire back buffer with BLANK cells")
        void fillsWithBlanks() {
            // Write something first
            buffer.setCell(5, 2, Cell.of('X', 0xFF0000));

            buffer.clearBack();

            Cell[][] back = buffer.getBackBuffer();
            for (int row = 0; row < 5; row++) {
                for (int col = 0; col < 10; col++) {
                    assertThat(back[row][col]).isEqualTo(Cell.BLANK);
                }
            }
        }
    }

    @Nested
    @DisplayName("swap()")
    class Swap {

        @Test
        @DisplayName("back buffer becomes front buffer after swap")
        void backBecomesfront() {
            Cell marker = Cell.of('M', 0x00FF00);
            buffer.setCell(0, 0, marker); // write to back
            assertThat(buffer.getBackBuffer()[0][0]).isEqualTo(marker);
            assertThat(buffer.getFrontBuffer()[0][0]).isEqualTo(Cell.BLANK);

            buffer.swap();

            // After swap: what was back is now front
            assertThat(buffer.getFrontBuffer()[0][0]).isEqualTo(marker);
            // New back is the old front (which was blank)
            assertThat(buffer.getBackBuffer()[0][0]).isEqualTo(Cell.BLANK);
        }

        @Test
        @DisplayName("swap is O(1) — references exchanged, no copy")
        void swap_isReferenceExchange() {
            Cell[][] originalBack  = buffer.getBackBuffer();
            Cell[][] originalFront = buffer.getFrontBuffer();

            buffer.swap();

            // The exact same array objects just changed roles
            assertThat(buffer.getFrontBuffer()).isSameAs(originalBack);
            assertThat(buffer.getBackBuffer()).isSameAs(originalFront);
        }
    }

    @Nested
    @DisplayName("resize()")
    class Resize {

        @Test
        @DisplayName("resize allocates new buffers at the new dimensions")
        void resize_createsNewBuffers() {
            buffer.resize(20, 10);

            assertThat(buffer.getCols()).isEqualTo(20);
            assertThat(buffer.getRows()).isEqualTo(10);
            assertThat(buffer.getBackBuffer().length).isEqualTo(10);
            assertThat(buffer.getBackBuffer()[0].length).isEqualTo(20);
        }

        @Test
        @DisplayName("resize fills new buffers with BLANK")
        void resize_fillsWithBlanks() {
            buffer.setCell(0, 0, Cell.of('X')); // write to back
            buffer.resize(5, 3);

            assertThat(buffer.getBackBuffer()[0][0]).isEqualTo(Cell.BLANK);
        }
    }
}