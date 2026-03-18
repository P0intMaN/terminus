package io.terminus.core.render;

import io.terminus.core.Cell;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Ansi")
class AnsiTest {

    @Nested
    @DisplayName("moveTo()")
    class MoveTo {

        @Test
        @DisplayName("adds 1 to row and col for ANSI 1-indexing")
        void addsOne_toRowAndCol() {
            // Our grid is 0-indexed. ANSI is 1-indexed.
            // moveTo(0,0) must produce ESC[1;1H
            assertThat(Ansi.moveTo(0, 0)).isEqualTo("\033[1;1H");
            assertThat(Ansi.moveTo(2, 5)).isEqualTo("\033[3;6H");
        }

        @Test
        @DisplayName("moveTo(0,0) is the top-left home position")
        void moveToOrigin_isHome() {
            assertThat(Ansi.moveTo(0, 0)).isEqualTo(Ansi.CSI + "1;1H");
        }
    }

    @Nested
    @DisplayName("fgRgb() / bgRgb()")
    class ColorSequences {

        @Test
        @DisplayName("fgRgb extracts R,G,B components correctly")
        void fgRgb_extractsComponents() {
            // 0xFF0000 = R:255, G:0, B:0
            assertThat(Ansi.fgRgb(0xFF0000)).isEqualTo("\033[38;2;255;0;0m");
            // 0x00FF00 = R:0, G:255, B:0
            assertThat(Ansi.fgRgb(0x00FF00)).isEqualTo("\033[38;2;0;255;0m");
            // 0x0000FF = R:0, G:0, B:255
            assertThat(Ansi.fgRgb(0x0000FF)).isEqualTo("\033[38;2;0;0;255m");
        }

        @Test
        @DisplayName("bgRgb uses code 48 instead of 38")
        void bgRgb_uses48() {
            assertThat(Ansi.bgRgb(0xFFFFFF)).startsWith("\033[48;2;");
        }
    }

    @Nested
    @DisplayName("sgrFor()")
    class SgrFor {

        @Test
        @DisplayName("always starts with RESET")
        void alwaysStartsWithReset() {
            String sgr = Ansi.sgrFor(Cell.DEFAULT_COLOR, Cell.DEFAULT_COLOR, Cell.ATTR_NONE);
            assertThat(sgr).startsWith(Ansi.RESET);
        }

        @Test
        @DisplayName("plain cell — only RESET, no color or attribute codes")
        void plainCell_onlyReset() {
            String sgr = Ansi.sgrFor(Cell.DEFAULT_COLOR, Cell.DEFAULT_COLOR, Cell.ATTR_NONE);
            assertThat(sgr).isEqualTo(Ansi.RESET);
        }

        @Test
        @DisplayName("bold attribute emits bold sequence after reset")
        void boldAttr_emitsBold() {
            String sgr = Ansi.sgrFor(Cell.DEFAULT_COLOR, Cell.DEFAULT_COLOR, Cell.ATTR_BOLD);
            assertThat(sgr).isEqualTo(Ansi.RESET + Ansi.BOLD);
        }

        @Test
        @DisplayName("fg color emits RGB foreground sequence")
        void fgColor_emitsRgbSequence() {
            String sgr = Ansi.sgrFor(0xFF0000, Cell.DEFAULT_COLOR, Cell.ATTR_NONE);
            assertThat(sgr).contains("\033[38;2;255;0;0m");
        }

        @Test
        @DisplayName("combined bold + fg color emits both sequences")
        void boldPlusFg_emitsBoth() {
            String sgr = Ansi.sgrFor(0x00FF00, Cell.DEFAULT_COLOR, Cell.ATTR_BOLD);
            assertThat(sgr).contains(Ansi.BOLD);
            assertThat(sgr).contains("\033[38;2;0;255;0m");
        }
    }
}