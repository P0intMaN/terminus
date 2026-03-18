package io.terminus.core.components;

import io.terminus.core.Bounds;
import io.terminus.core.Cell;
import io.terminus.core.Constraint;
import io.terminus.core.LayoutAccess;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ProgressBar")
class ProgressBarTest {

    // ── Helpers ───────────────────────────────────────────────────────────

    private ProgressBar barAt(double value, int width) {
        ProgressBar bar = ProgressBar.builder()
            .initialValue(value)
            .showPercentage(false)
            .build();
        LayoutAccess.setBounds(bar, new Bounds(0, 0, width, 1));
        return bar;
    }

    private Cell[] renderRow(ProgressBar bar) {
        return bar.render()[0];
    }

    // ── Tests ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("setValue()")
    class SetValue {

        @Test
        @DisplayName("value is clamped to [0.0, 1.0]")
        void value_isClamped() {
            ProgressBar bar = ProgressBar.builder().build();
            bar.setValue(1.5);
            assertThat(bar.getValue()).isEqualTo(1.0);
            bar.setValue(-0.5);
            assertThat(bar.getValue()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("setValue marks dirty when value changes")
        void setValue_marksDirty() {
            ProgressBar bar = ProgressBar.builder().initialValue(0.5).build();
            bar.clearDirty();

            bar.setValue(0.7);

            assertThat(bar.isDirty()).isTrue();
        }

        @Test
        @DisplayName("setValue does NOT mark dirty when value is unchanged")
        void setValue_notDirty_whenSameValue() {
            ProgressBar bar = ProgressBar.builder().initialValue(0.5).build();
            bar.clearDirty();

            bar.setValue(0.5); // same value

            assertThat(bar.isDirty()).isFalse();
        }
    }

    @Nested
    @DisplayName("render() — EIGHTHS style")
    class RenderEighths {

        @Test
        @DisplayName("value=0.0 renders all empty cells")
        void zeroValue_allEmpty() {
            ProgressBar bar = barAt(0.0, 10);
            Cell[] row = renderRow(bar);

            // All cells should be the empty char (space for EIGHTHS)
            for (Cell cell : row) {
                assertThat(cell.glyph()).isEqualTo((int) ' ');
            }
        }

        @Test
        @DisplayName("value=1.0 renders all full blocks")
        void fullValue_allFull() {
            ProgressBar bar = barAt(1.0, 10);
            Cell[] row = renderRow(bar);

            for (Cell cell : row) {
                assertThat(cell.glyph())
                    .isEqualTo((int) ProgressBar.Style.EIGHTHS.fullChar);
            }
        }

        @Test
        @DisplayName("value=0.5 fills exactly half the bar")
        void halfValue_fillsHalf() {
            ProgressBar bar = barAt(0.5, 10); // 5 full blocks
            Cell[] row = renderRow(bar);

            // First 5 cells: full block █
            for (int i = 0; i < 5; i++) {
                assertThat(row[i].glyph())
                    .as("col %d should be full", i)
                    .isEqualTo((int) '█');
            }
            // Last 5 cells: empty space
            for (int i = 5; i < 10; i++) {
                assertThat(row[i].glyph())
                    .as("col %d should be empty", i)
                    .isEqualTo((int) ' ');
            }
        }

        @Test
        @DisplayName("fractional value produces correct partial block")
        void fractionalValue_producesPartialBlock() {
            // value=0.1 on a 10-wide bar = 1.0 cells fill
            // fullBlocks=1, remainder=0.0, so just 1 full block
            ProgressBar bar = barAt(0.1, 10);
            Cell[] row = renderRow(bar);
            assertThat(row[0].glyph()).isEqualTo((int) '█');
            assertThat(row[1].glyph()).isEqualTo((int) ' ');
        }

        @Test
        @DisplayName("partial fill uses correct eighth character")
        void partialFill_correctEighthChar() {
            // value=0.15 on 10-wide bar = 1.5 cells fill
            // fullBlocks=1, remainder=0.5, partialIdx = floor(0.5*8) = 4 → '▌'
            ProgressBar bar = barAt(0.15, 10);
            Cell[] row = renderRow(bar);
            assertThat(row[0].glyph()).isEqualTo((int) '█'); // full block
            assertThat(row[1].glyph()).isEqualTo((int) '▌'); // 4/8 = half block
        }
    }

    @Nested
    @DisplayName("render() — ASCII style")
    class RenderAscii {

        @Test
        @DisplayName("ASCII style uses = for fill and - for empty")
        void asciiStyle_correctChars() {
            ProgressBar bar = ProgressBar.builder()
                .style(ProgressBar.Style.ASCII)
                .initialValue(0.5)
                .showPercentage(false)
                .build();
            LayoutAccess.setBounds(bar, new Bounds(0, 0, 10, 1));
            Cell[] row = bar.render()[0];

            // First 5: fill char '='
            assertThat(row[0].glyph()).isEqualTo((int) '=');
            // Last 5: empty char '-'
            assertThat(row[9].glyph()).isEqualTo((int) '-');
        }
    }

    @Nested
    @DisplayName("render() — colors")
    class Colors {

        @Test
        @DisplayName("filled cells use the fg color")
        void filledCells_useFgColor() {
            ProgressBar bar = ProgressBar.builder()
                .fg(0xFF0000)
                .initialValue(1.0)
                .showPercentage(false)
                .build();
            LayoutAccess.setBounds(bar, new Bounds(0, 0, 5, 1));
            Cell[] row = bar.render()[0];

            for (Cell cell : row) {
                assertThat(cell.fg()).isEqualTo(0xFF0000);
            }
        }

        @Test
        @DisplayName("empty cells use the emptyFg color")
        void emptyCells_useEmptyFgColor() {
            ProgressBar bar = ProgressBar.builder()
                .emptyFg(0x333333)
                .initialValue(0.0)
                .showPercentage(false)
                .build();
            LayoutAccess.setBounds(bar, new Bounds(0, 0, 5, 1));
            Cell[] row = bar.render()[0];

            for (Cell cell : row) {
                assertThat(cell.fg()).isEqualTo(0x333333);
            }
        }
    }

    @Nested
    @DisplayName("measure()")
    class Measure {

        @Test
        @DisplayName("measure returns height of 1")
        void measure_heightIsOne() {
            ProgressBar bar = ProgressBar.builder().build();
            Bounds size = bar.measure(Constraint.of(80, 24));
            assertThat(size.height()).isEqualTo(1);
        }

        @Test
        @DisplayName("measure respects available width")
        void measure_respectsWidth() {
            ProgressBar bar = ProgressBar.builder().build();
            Bounds size = bar.measure(Constraint.of(30, 24));
            assertThat(size.width()).isEqualTo(30);
        }

        @Test
        @DisplayName("measure enforces minimum width of 10")
        void measure_minimumWidth() {
            ProgressBar bar = ProgressBar.builder().build();
            Bounds size = bar.measure(Constraint.of(3, 24));
            assertThat(size.width()).isEqualTo(10);
        }
    }
}