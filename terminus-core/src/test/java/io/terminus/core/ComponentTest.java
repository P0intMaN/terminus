package io.terminus.core;

import io.terminus.core.event.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("Component tree")
class ComponentTest {

    // ── Test doubles ──────────────────────────────────────────────────────
    // We need concrete implementations to test the abstract classes.
    // These are minimal stubs — just enough to instantiate and test.

    static class TestLeaf extends Leaf {
        @Override public Cell[][] render() { return blankGrid(); }
        @Override public Bounds measure(Constraint c) { return Bounds.of(10, 1); }
    }

    static class TestContainer extends Container {
        @Override public Cell[][] render() { return new Cell[1][1]; }
        @Override public Bounds measure(Constraint c) { return Bounds.of(80, 24); }
    }

    private TestContainer root;
    private TestContainer middle;
    private TestLeaf leaf;

    @BeforeEach
    void setUp() {
        root   = new TestContainer();
        middle = new TestContainer();
        leaf   = new TestLeaf();

        root.addChild(middle);
        middle.addChild(leaf);

        // Clear dirty flags so tests start from a clean state
        root.clearDirty();
        middle.clearDirty();
        leaf.clearDirty();
    }

    @Nested
    @DisplayName("markDirty() bubbling")
    class DirtyBubbling {

        @Test
        @DisplayName("marking a leaf dirty propagates to all ancestors")
        void leafDirty_propagatesToRoot() {
            leaf.markDirty();

            assertThat(leaf.isDirty()).isTrue();
            assertThat(middle.isDirty()).isTrue();
            assertThat(root.isDirty()).isTrue();
        }

        @Test
        @DisplayName("marking middle dirty does NOT affect the leaf")
        void middleDirty_doesNotAffectLeaf() {
            middle.markDirty();

            assertThat(middle.isDirty()).isTrue();
            assertThat(root.isDirty()).isTrue();
            // leaf was not marked dirty — it's clean
            assertThat(leaf.isDirty()).isFalse();
        }

        @Test
        @DisplayName("clearDirty() affects only the component, not ancestors")
        void clearDirty_localOnly() {
            leaf.markDirty();  // root, middle, leaf all dirty
            leaf.clearDirty(); // only leaf cleared

            assertThat(leaf.isDirty()).isFalse();
            assertThat(middle.isDirty()).isTrue();  // still dirty
            assertThat(root.isDirty()).isTrue();    // still dirty
        }

        @Test
        @DisplayName("freshly created component starts dirty")
        void newComponent_startsDirty() {
            TestLeaf fresh = new TestLeaf();
            assertThat(fresh.isDirty()).isTrue();
        }
    }

    @Nested
    @DisplayName("Container child management")
    class ChildManagement {

        @Test
        @DisplayName("addChild() sets parent pointer correctly")
        void addChild_setsParent() {
            TestLeaf newLeaf = new TestLeaf();
            TestContainer container = new TestContainer();
            container.clearDirty();

            container.addChild(newLeaf);

            // We test the effect of parent being set via markDirty bubbling
            newLeaf.markDirty();
            assertThat(container.isDirty()).isTrue();
        }

        @Test
        @DisplayName("addChild() with already-parented component throws")
        void addChild_alreadyParented_throws() {
            TestContainer other = new TestContainer();
            // leaf already belongs to middle
            assertThatThrownBy(() -> other.addChild(leaf))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already has a parent");
        }

        @Test
        @DisplayName("removeChild() clears parent pointer")
        void removeChild_clearsParent() {
            TestContainer container = new TestContainer();
            TestLeaf target = new TestLeaf();
            container.addChild(target);
            container.clearDirty();
            target.clearDirty();

            container.removeChild(target);

            // After removal, marking target dirty should NOT reach container
            container.clearDirty();
            target.markDirty();
            assertThat(container.isDirty()).isFalse();
        }

        @Test
        @DisplayName("getChildren() returns unmodifiable view")
        void getChildren_isUnmodifiable() {
            assertThatThrownBy(() -> root.getChildren().add(new TestLeaf()))
                .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("isSubtreeDirty() returns true when any descendant is dirty")
        void isSubtreeDirty_anyDescendantDirty() {
            // Everything clean
            assertThat(root.isSubtreeDirty()).isFalse();

            // Make only the leaf dirty (without bubbling, to test subtree check independently)
            // We directly mark just the leaf's own flag via markDirty (which bubbles)
            // To test isSubtreeDirty in isolation, we clear root/middle after marking leaf
            leaf.markDirty();
            root.clearDirty();
            middle.clearDirty();
            // leaf.isDirty() == true, but root and middle are cleared

            assertThat(root.isSubtreeDirty()).isTrue(); // finds dirty leaf via recursion
        }
    }

    @Nested
    @DisplayName("Leaf utilities")
    class LeafUtilities {

        @Test
        @DisplayName("blankGrid() fills with BLANK cells at current bounds size")
        void blankGrid_fillsWithBlanks() {
            leaf.setBounds(new Bounds(0, 0, 5, 3));
            Cell[][] grid = leaf.render();

            assertThat(grid.length).isEqualTo(3);       // height rows
            assertThat(grid[0].length).isEqualTo(5);    // width cols
            for (Cell[] row : grid) {
                for (Cell cell : row) {
                    assertThat(cell).isEqualTo(Cell.BLANK);
                }
            }
        }
    }
}