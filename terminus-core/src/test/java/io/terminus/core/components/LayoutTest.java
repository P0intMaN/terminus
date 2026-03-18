package io.terminus.core.components;

import io.terminus.core.Bounds;
import io.terminus.core.Cell;
import io.terminus.core.Component;
import io.terminus.core.Constraint;
import io.terminus.core.LayoutAccess;
import io.terminus.core.Leaf;
import io.terminus.core.layout.FlexConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Layout")
class LayoutTest {

    // ── Test double ───────────────────────────────────────────────────────

    /**
     * A leaf with a declared preferred size.
     * Used to verify that layout assigns the correct bounds.
     */
    static class SizedLeaf extends Leaf {
        private final int prefW, prefH;

        SizedLeaf(int prefW, int prefH) {
            this.prefW = prefW; this.prefH = prefH;
        }

        @Override
        public Cell[][] render() { return blankGrid(); }

        @Override
        public Bounds measure(Constraint c) {
            int w = c.isWidthUnbounded()  ? prefW : Math.min(prefW, c.maxWidth());
            int h = c.isHeightUnbounded() ? prefH : Math.min(prefH, c.maxHeight());
            return Bounds.of(w, h);
        }
    }

    /** Give a Layout its bounds and run layout. */
    private void layout(Layout l, int w, int h) {
        LayoutAccess.setBounds(l, new Bounds(0, 0, w, h));
        l.performLayout();
    }

    @Nested
    @DisplayName("ROW layout")
    class Row {

        @Test
        @DisplayName("two fixed children placed side by side")
        void twoFixed_sideBySide() {
            Layout row = Layout.row().build();
            SizedLeaf a = new SizedLeaf(10, 3);
            SizedLeaf b = new SizedLeaf(15, 3);
            row.add(a);
            row.add(b);

            layout(row, 40, 5);

            assertThat(a.getBounds().x()).isEqualTo(0);
            assertThat(a.getBounds().width()).isEqualTo(10);
            assertThat(b.getBounds().x()).isEqualTo(10);
            assertThat(b.getBounds().width()).isEqualTo(15);
        }

        @Test
        @DisplayName("gap is applied between children")
        void gap_appliedBetweenChildren() {
            Layout row = Layout.row().gap(2).build();
            SizedLeaf a = new SizedLeaf(10, 3);
            SizedLeaf b = new SizedLeaf(10, 3);
            row.add(a);
            row.add(b);

            layout(row, 40, 5);

            assertThat(a.getBounds().x()).isEqualTo(0);
            assertThat(b.getBounds().x()).isEqualTo(12); // 10 + gap(2)
        }

        @Test
        @DisplayName("flex=1 child takes all remaining space")
        void flex1_takesRemainder() {
            Layout row = Layout.row().build();
            SizedLeaf fixed = new SizedLeaf(10, 3);
            SizedLeaf flex  = new SizedLeaf(1, 3);  // preferred=1, but flex=1
            row.add(fixed);
            row.addFlex(flex);

            layout(row, 40, 5);

            assertThat(fixed.getBounds().width()).isEqualTo(10);
            assertThat(flex.getBounds().width()).isEqualTo(30); // 40 - 10
            assertThat(flex.getBounds().x()).isEqualTo(10);
        }

        @Test
        @DisplayName("two flex=1 children split space equally")
        void twoFlex1_splitEqually() {
            Layout row = Layout.row().build();
            SizedLeaf a = new SizedLeaf(1, 3);
            SizedLeaf b = new SizedLeaf(1, 3);
            row.addFlex(a);
            row.addFlex(b);

            layout(row, 40, 5);

            assertThat(a.getBounds().width()).isEqualTo(20);
            assertThat(b.getBounds().width()).isEqualTo(20);
        }

        @Test
        @DisplayName("flex=2 and flex=1 split space 2:1")
        void flex2_and_flex1_splitProportionally() {
            Layout row = Layout.row().build();
            SizedLeaf big   = new SizedLeaf(1, 3);
            SizedLeaf small = new SizedLeaf(1, 3);
            row.addFlex(big, 2);
            row.addFlex(small, 1);

            layout(row, 60, 5);

            assertThat(big.getBounds().width()).isEqualTo(40);   // 2/3 of 60
            assertThat(small.getBounds().width()).isEqualTo(20); // 1/3 of 60
        }

        @Test
        @DisplayName("children stretch to full height by default")
        void children_stretchToFullHeight() {
            Layout row = Layout.row().build();
            SizedLeaf a = new SizedLeaf(10, 1); // prefers height=1
            row.add(a);

            layout(row, 40, 5); // container is 5 tall

            // STRETCH alignment: child fills full cross-axis (height=5)
            assertThat(a.getBounds().height()).isEqualTo(5);
        }

        @Test
        @DisplayName("padding shrinks the inner area")
        void padding_shrinks_innerArea() {
            Layout row = Layout.row().padding(2).build(); // 2 on all sides
            SizedLeaf a = new SizedLeaf(100, 1); // wants max width
            row.addFlex(a);

            layout(row, 40, 10);

            // Inner width = 40 - 2 - 2 = 36
            assertThat(a.getBounds().width()).isEqualTo(36);
            // Inner x = 0 + paddingLeft(2) = 2
            assertThat(a.getBounds().x()).isEqualTo(2);
            // Inner y = 0 + paddingTop(2) = 2
            assertThat(a.getBounds().y()).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("COLUMN layout")
    class Column {

        @Test
        @DisplayName("two fixed children stacked vertically")
        void twoFixed_stackedVertically() {
            Layout col = Layout.column().build();
            SizedLeaf a = new SizedLeaf(10, 3);
            SizedLeaf b = new SizedLeaf(10, 5);
            col.add(a);
            col.add(b);

            layout(col, 20, 30);

            assertThat(a.getBounds().y()).isEqualTo(0);
            assertThat(a.getBounds().height()).isEqualTo(3);
            assertThat(b.getBounds().y()).isEqualTo(3);
            assertThat(b.getBounds().height()).isEqualTo(5);
        }

        @Test
        @DisplayName("flex=1 child takes remaining vertical space")
        void flex1_takesRemainingHeight() {
            Layout col = Layout.column().build();
            SizedLeaf header  = new SizedLeaf(20, 2);
            SizedLeaf content = new SizedLeaf(20, 1);
            SizedLeaf footer  = new SizedLeaf(20, 1);
            col.add(header);
            col.addFlex(content);
            col.add(footer);

            layout(col, 20, 24);

            assertThat(header.getBounds().height()).isEqualTo(2);
            assertThat(footer.getBounds().height()).isEqualTo(1);
            // Content takes: 24 - 2 - 1 = 21 rows
            assertThat(content.getBounds().height()).isEqualTo(21);
        }

        @Test
        @DisplayName("gap applied between rows")
        void gap_appliedBetweenRows() {
            Layout col = Layout.column().gap(1).build();
            SizedLeaf a = new SizedLeaf(10, 2);
            SizedLeaf b = new SizedLeaf(10, 2);
            col.add(a);
            col.add(b);

            layout(col, 20, 20);

            assertThat(a.getBounds().y()).isEqualTo(0);
            assertThat(b.getBounds().y()).isEqualTo(3); // 2 + gap(1)
        }
    }

    @Nested
    @DisplayName("nesting")
    class Nesting {

        @Test
        @DisplayName("column containing rows — nested layout works correctly")
        void column_containingRows() {
            // Build:
            //   column
            //     row1: [A(10)] [B(flex=1)]
            //     row2: [C(flex=1)] [D(10)]

            Layout col  = Layout.column().gap(1).build();
            Layout row1 = Layout.row().build();
            Layout row2 = Layout.row().build();

            SizedLeaf a = new SizedLeaf(10, 3);
            SizedLeaf b = new SizedLeaf(1,  3);
            SizedLeaf c = new SizedLeaf(1,  3);
            SizedLeaf d = new SizedLeaf(10, 3);

            row1.add(a);
            row1.addFlex(b);
            row2.addFlex(c);
            row2.add(d);

            col.addFlex(row1);
            col.addFlex(row2);

            layout(col, 40, 7); // 7 rows: 3 + gap(1) + 3

            // row1 occupies y=0..2 (3 rows), row2 y=4..6 (3 rows)
            assertThat(row1.getBounds().y()).isEqualTo(0);
            assertThat(row2.getBounds().y()).isEqualTo(4);

            // In row1: a=x(0,w=10), b=x(10,w=30)
            assertThat(a.getBounds().x()).isEqualTo(0);
            assertThat(a.getBounds().width()).isEqualTo(10);
            assertThat(b.getBounds().x()).isEqualTo(10);
            assertThat(b.getBounds().width()).isEqualTo(30);
        }
    }

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test
        @DisplayName("empty layout does not crash")
        void empty_doesNotCrash() {
            Layout row = Layout.row().build();
            assertThatCode(() -> layout(row, 40, 5))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("children too wide for container are clipped to available space")
        void childrenTooWide_areClipped() {
            Layout row = Layout.row().build();
            SizedLeaf a = new SizedLeaf(50, 3); // wants 50, only 40 available
            row.add(a);

            layout(row, 40, 5);

            assertThat(a.getBounds().width()).isLessThanOrEqualTo(40);
        }

        @Test
        @DisplayName("single flex child in empty space gets all of it")
        void singleFlex_getsAll() {
            Layout row = Layout.row().build();
            SizedLeaf a = new SizedLeaf(1, 1);
            row.addFlex(a);

            layout(row, 80, 24);

            assertThat(a.getBounds().width()).isEqualTo(80);
            assertThat(a.getBounds().height()).isEqualTo(24);
        }
    }
}