package io.terminus.core.components;

import io.terminus.core.Bounds;
import io.terminus.core.Cell;
import io.terminus.core.Constraint;
import io.terminus.core.LayoutAccess;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Text")
class TextTest {

    @Nested
    @DisplayName("word wrap algorithm")
    class WordWrap {

        @Test
        @DisplayName("short text fits on one line")
        void shortText_oneLine() {
            List<String> lines = Text.wrap("hello", 20);
            assertThat(lines).hasSize(1);
            assertThat(lines.get(0)).isEqualTo("hello");
        }

        @Test
        @DisplayName("long text wraps to multiple lines")
        void longText_wrapsToMultipleLines() {
            List<String> lines = Text.wrap(
                "the quick brown fox jumps over the lazy dog", 15);
            assertThat(lines.size()).isGreaterThan(1);
            for (String line : lines) {
                assertThat(line.length()).isLessThanOrEqualTo(15);
            }
        }

        @Test
        @DisplayName("explicit newlines create new lines")
        void explicitNewlines_createNewLines() {
            List<String> lines = Text.wrap("line one\nline two\nline three", 40);
            assertThat(lines).containsExactly("line one", "line two", "line three");
        }

        @Test
        @DisplayName("words longer than width are hard-clipped")
        void longWords_hardClipped() {
            List<String> lines = Text.wrap("superlongword", 5);
            for (String line : lines) {
                assertThat(line.length()).isLessThanOrEqualTo(5);
            }
            // All characters are preserved across lines
            String all = String.join("", lines);
            assertThat(all).isEqualTo("superlongword");
        }

        @Test
        @DisplayName("empty string produces one empty line")
        void emptyString_oneEmptyLine() {
            List<String> lines = Text.wrap("", 20);
            assertThat(lines).hasSize(1);
            assertThat(lines.get(0)).isEqualTo("");
        }

        @Test
        @DisplayName("blank lines are preserved")
        void blankLines_preserved() {
            List<String> lines = Text.wrap("first\n\nthird", 20);
            assertThat(lines).containsExactly("first", "", "third");
        }
    }

    @Nested
    @DisplayName("render()")
    class Render {

        private String renderLine(Text t, int width, int row) {
            LayoutAccess.setBounds(t, new Bounds(0, 0, width, 5));
            Cell[][] grid = t.render();
            if (row >= grid.length) return "";
            StringBuilder sb = new StringBuilder();
            for (Cell c : grid[row]) sb.appendCodePoint(c.glyph());
            return sb.toString();
        }

        @Test
        @DisplayName("left-aligned text pads with spaces on the right")
        void leftAlign_padsRight() {
            Text t = Text.of("hi").align(Text.Alignment.LEFT).build();
            String line = renderLine(t, 10, 0);
            assertThat(line).startsWith("hi");
            assertThat(line).endsWith("        "); // 8 trailing spaces
        }

        @Test
        @DisplayName("right-aligned text pads with spaces on the left")
        void rightAlign_padsLeft() {
            Text t = Text.of("hi").align(Text.Alignment.RIGHT).build();
            String line = renderLine(t, 10, 0);
            assertThat(line).startsWith("        "); // 8 leading spaces
            assertThat(line).endsWith("hi");
        }

        @Test
        @DisplayName("center-aligned text pads both sides")
        void centerAlign_padsBothSides() {
            Text t = Text.of("hi").align(Text.Alignment.CENTER).build();
            String line = renderLine(t, 10, 0);
            assertThat(line.stripLeading().stripTrailing()).isEqualTo("hi");
            assertThat(line.indexOf("hi")).isGreaterThan(0);
        }

        @Test
        @DisplayName("TRUNCATE overflow adds ellipsis")
        void truncate_addsEllipsis() {
            Text t = Text.of("hello world")
                .overflow(Text.Overflow.TRUNCATE).build();
            String line = renderLine(t, 8, 0);
            assertThat(line).endsWith("…");
            assertThat(line.length()).isEqualTo(8);
        }

        @Test
        @DisplayName("maxLines limits output to N lines")
        void maxLines_limitsOutput() {
            Text t = Text.of("one two three four five six seven eight")
                .maxLines(2).build();
            LayoutAccess.setBounds(t, new Bounds(0, 0, 10, 5));
            Cell[][] grid = t.render();
            // Find last non-blank row
            int lastContentRow = 0;
            for (int r = 0; r < grid.length; r++) {
                boolean hasContent = false;
                for (Cell c : grid[r]) {
                    if (c.glyph() != ' ') { hasContent = true; break; }
                }
                if (hasContent) lastContentRow = r;
            }
            assertThat(lastContentRow).isLessThan(2);
        }

        @Test
        @DisplayName("bold attribute is applied to all cells")
        void boldAttr_appliedToAllCells() {
            Text t = Text.of("bold").bold().build();
            LayoutAccess.setBounds(t, new Bounds(0, 0, 10, 1));
            Cell[][] grid = t.render();
            // First 4 cells should have BOLD attribute
            for (int i = 0; i < 4; i++) {
                assertThat(grid[0][i].isBold())
                    .as("cell %d should be bold", i)
                    .isTrue();
            }
        }

        @Test
        @DisplayName("setText() marks component dirty")
        void setText_marksDirty() {
            Text t = Text.plain("hello");
            LayoutAccess.setBounds(t, new Bounds(0, 0, 20, 1));
            t.render();
            t.clearDirty();
            t.setText("world");
            assertThat(t.isDirty()).isTrue();
        }

        @Test
        @DisplayName("setText() with same content does NOT mark dirty")
        void setText_sameContent_notDirty() {
            Text t = Text.plain("hello");
            t.clearDirty();
            t.setText("hello"); // same content
            assertThat(t.isDirty()).isFalse();
        }
    }

    @Nested
    @DisplayName("measure()")
    class Measure {

        @Test
        @DisplayName("single line text measures as height 1")
        void singleLine_heightOne() {
            Text t = Text.plain("hello");
            Bounds b = t.measure(Constraint.of(80, 24));
            assertThat(b.height()).isEqualTo(1);
        }

        @Test
        @DisplayName("wrapped text measures correct height")
        void wrappedText_correctHeight() {
            // "hello world" at width 5 wraps to "hello" and "world" = 2 lines
            Text t = Text.plain("hello world");
            Bounds b = t.measure(Constraint.of(5, 24));
            assertThat(b.height()).isEqualTo(2);
        }
    }
}