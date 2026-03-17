package io.terminus.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;


@DisplayName("Cell")
class CellTest {
    
    // WHY @Nested?
    // Nested test classes let you group tests by behavior, not by method.
    // The output reads like a spec: "Cell > factory methods > of(char) creates..."
    // This is the style used at Google (their testing guidelines encourage this).

    @Nested
    @DisplayName("factory method")
    class FactoryMethods {

        @Test
        @DisplayName("of(char) creates a plain cell with default colors")
        void ofChar_createsPlainCell() {
            Cell cell = Cell.of('A');

            assertThat(cell.glyph()).isEqualTo((int) 'A');
            assertThat(cell.fg()).isEqualTo(Cell.DEFAULT_COLOR);
            assertThat(cell.bg()).isEqualTo(Cell.DEFAULT_COLOR);
            assertThat(cell.attrs()).isEqualTo(Cell.ATTR_NONE);
            assertThat(cell.width()).isEqualTo((byte) 1);
        }

        @Test
        @DisplayName("of(char, fg) sets foreground color")
        void ofCharFg_setsForeground() {
            Cell cell = Cell.of('X', 0xFF0000); // red foreground

            assertThat(cell.fg()).isEqualTo(0xFF0000);
            assertThat(cell.bg()).isEqualTo(Cell.DEFAULT_COLOR);
        }

        @Test
        @DisplayName("BLANK is a space with default colors")
        void blank_isSpaceWithDefaults() {
            assertThat(Cell.BLANK.glyph()).isEqualTo((int) ' ');
            assertThat(Cell.BLANK.fg()).isEqualTo(Cell.DEFAULT_COLOR);
            assertThat(Cell.BLANK.bg()).isEqualTo(Cell.DEFAULT_COLOR);
        }

        @Test
        @DisplayName("wide() creates a cell with width 2")
        void wide_createsCellWithWidth2() {
            Cell cell = Cell.wide(0x4E2D, 0xFFFFFF, Cell.DEFAULT_COLOR, Cell.ATTR_NONE);
            assertThat(cell.width()).isEqualTo((byte) 2);
        }
    }

    @Nested
    @DisplayName("attribute helpers")
    class AttributeHelpers {

        @Test
        @DisplayName("isBold() returns true only when BOLD flag is set")
        void isBold_onlyWhenFlagSet() {
            Cell plain = Cell.of('A');
            Cell bold  = Cell.of('A', Cell.DEFAULT_COLOR, Cell.DEFAULT_COLOR, Cell.ATTR_BOLD);

            assertThat(plain.isBold()).isFalse();
            assertThat(bold.isBold()).isTrue();
        }

        @Test
        @DisplayName("multiple attributes can be combined with bitwise OR")
        void multipleAttrs_canBeCombined() {
            byte boldItalic = (byte)(Cell.ATTR_BOLD | Cell.ATTR_ITALIC);
            Cell cell = Cell.of('A', Cell.DEFAULT_COLOR, Cell.DEFAULT_COLOR, boldItalic);

            assertThat(cell.isBold()).isTrue();
            assertThat(cell.isItalic()).isTrue();
            assertThat(cell.isUnderline()).isFalse();
        }

        @Test
        @DisplayName("withAttr() returns a new Cell without mutating the original")
        void withAttr_returnsNewCell_doesNotMutate() {
            Cell original = Cell.of('A');
            Cell bold     = original.withAttr(Cell.ATTR_BOLD);

            // original is unchanged — immutability guarantee
            assertThat(original.isBold()).isFalse();
            assertThat(bold.isBold()).isTrue();

            // they are different objects
            assertThat(bold).isNotSameAs(original);
        }

    }

    @Nested
    @DisplayName("value equality")
    class ValueEquality {

        @Test
        @DisplayName("two Cells with same data are equal (record semantics)")
        void sameData_isEqual() {
            Cell a = Cell.of('Z', 0x00FF00);
            Cell b = Cell.of('Z', 0x00FF00);

            // Records use structural equality — this is the whole point.
            // The ScreenDiffer uses cell.equals() to skip unchanged cells.
            assertThat(a).isEqualTo(b);
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
        }

        @Test
        @DisplayName("Cells with different glyph are not equal")
        void differentGlyph_notEqual() {
            assertThat(Cell.of('A')).isNotEqualTo(Cell.of('B'));
        }

    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("invalid width throws IllegalArgumentException")
        void invalidWidth_throws() {
            assertThatThrownBy(() ->
                new Cell('A', Cell.DEFAULT_COLOR, Cell.DEFAULT_COLOR, Cell.ATTR_NONE, (byte) 3)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("width must be 1 or 2");
        }

        @Test
        @DisplayName("invalid fg color throws IllegalArgumentException")
        void invalidFgColor_throws() {
            assertThatThrownBy(() ->
                new Cell('A', 0x1FFFFFF, Cell.DEFAULT_COLOR, Cell.ATTR_NONE, (byte) 1)
            ).isInstanceOf(IllegalArgumentException.class)
             .hasMessageContaining("fg color");
        }

        @Test
        @DisplayName("DEFAULT_COLOR (-1) is valid for both fg and bg")
        void defaultColor_isValid() {
            // Should not throw
            assertThatCode(() -> Cell.of('A')).doesNotThrowAnyException();
        }
    }
}
