package io.terminus.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Bounds")
class BoundsTest {

    @Nested
    @DisplayName("spatial operations")
    class SpatialOps {

        @Test
        @DisplayName("inset(padding) shrinks uniformly on all sides")
        void inset_shrinksUniformly() {
            Bounds b = new Bounds(0, 0, 100, 50);
            Bounds inset = b.inset(5);

            assertThat(inset.x()).isEqualTo(5);
            assertThat(inset.y()).isEqualTo(5);
            assertThat(inset.width()).isEqualTo(90);
            assertThat(inset.height()).isEqualTo(40);
        }

        @Test
        @DisplayName("inset larger than size clamps to zero, never negative")
        void inset_clampsToZero() {
            Bounds b = new Bounds(0, 0, 10, 10);
            Bounds inset = b.inset(20); // padding larger than the bounds

            assertThat(inset.width()).isEqualTo(0);
            assertThat(inset.height()).isEqualTo(0);
        }

        @Test
        @DisplayName("contains() returns true for points inside, false outside")
        void contains_hitTest() {
            Bounds b = new Bounds(10, 5, 20, 10); // x:10-30, y:5-15

            assertThat(b.contains(10, 5)).isTrue();   // top-left corner
            assertThat(b.contains(29, 14)).isTrue();  // bottom-right (exclusive right=30,bottom=15)
            assertThat(b.contains(30, 5)).isFalse();  // right edge (exclusive)
            assertThat(b.contains(9, 5)).isFalse();   // just left of bounds
        }

        @Test
        @DisplayName("intersect() returns the overlap region")
        void intersect_returnsOverlap() {
            Bounds a = new Bounds(0, 0, 10, 10);
            Bounds b = new Bounds(5, 5, 10, 10);

            Bounds overlap = a.intersect(b);

            assertThat(overlap.x()).isEqualTo(5);
            assertThat(overlap.y()).isEqualTo(5);
            assertThat(overlap.width()).isEqualTo(5);
            assertThat(overlap.height()).isEqualTo(5);
        }

        @Test
        @DisplayName("intersect() returns ZERO when no overlap")
        void intersect_returnsZeroWhenNoOverlap() {
            Bounds a = new Bounds(0, 0, 5, 5);
            Bounds b = new Bounds(10, 10, 5, 5);

            assertThat(a.intersect(b)).isEqualTo(Bounds.ZERO);
        }

        @Test
        @DisplayName("translate() moves bounds without changing size")
        void translate_movesBounds() {
            Bounds b = new Bounds(0, 0, 20, 10);
            Bounds moved = b.translate(5, 3);

            assertThat(moved.x()).isEqualTo(5);
            assertThat(moved.y()).isEqualTo(3);
            assertThat(moved.width()).isEqualTo(20);
            assertThat(moved.height()).isEqualTo(10);
        }
    }

    @Nested
    @DisplayName("validation")
    class Validation {

        @Test
        @DisplayName("negative width throws")
        void negativeWidth_throws() {
            assertThatThrownBy(() -> new Bounds(0, 0, -1, 10))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }
}