package io.terminus.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A Component that owns and manages child components.
 *
 * All layout and grouping components extend Container:
 *   Layout (row/col), Modal, ScrollView, Table (which has row children), etc.
 *
 * PATTERN: Composite — Container IS-A Component, and HAS-A list of Components.
 * This is the recursive structure that makes the Composite pattern work.
 *
 * KEY INVARIANT: A component can only have ONE parent at a time.
 * Adding a component to a Container that already has a parent is an error.
 * This mirrors how real UI frameworks (Flutter, Android) work.
 */
public abstract class Container extends Component {

    /**
     * The ordered list of children.
     *
     * WHY ArrayList AND NOT LinkedList?
     * We iterate this list every frame during rendering. ArrayList has
     * O(1) random access and excellent CPU cache locality — iteration
     * is very fast. LinkedList has O(n) random access and poor cache
     * locality. For a list that's read far more often than it's written,
     * ArrayList wins decisively.
     *
     * WHY NOT expose the list directly?
     * Exposing the raw list would let callers add children without
     * going through addChild(), bypassing parent-assignment logic.
     * We provide a controlled API instead.
     */
    private final List<Component> children = new ArrayList<>();

    // ── Child management ──────────────────────────────────────────────────

    /**
     * Add a child to this container.
     *
     * Sets the child's parent pointer and marks this container dirty,
     * because adding a child changes the visual output.
     *
     * @throws IllegalStateException if the child already has a parent.
     *         A component cannot be in two places in the tree at once.
     */
    public final void addChild(Component child) {
        if (child.parent != null) {
            throw new IllegalStateException(
                "Component already has a parent. Remove it first before re-adding. " +
                "Component: " + child.getClass().getSimpleName()
            );
        }
        children.add(child);
        child.parent = this;
        markDirty(); // visual output changed
    }

    /**
     * Remove a child from this container.
     * Clears the child's parent pointer and marks this container dirty.
     *
     * @return true if the child was present and removed, false otherwise.
     */
    public final boolean removeChild(Component child) {
        boolean removed = children.remove(child);
        if (removed) {
            child.parent = null;
            markDirty();
        }
        return removed;
    }

    /**
     * Remove all children from this container.
     * Clears every child's parent pointer.
     */
    public final void clearChildren() {
        for (Component child : children) {
            child.parent = null;
        }
        children.clear();
        markDirty();
    }

    /**
     * Returns an unmodifiable view of the children list.
     *
     * WHY UNMODIFIABLE?
     * We want callers to be able to read children (e.g. the Renderer
     * iterating to render them) but not mutate the list directly.
     * All mutation goes through addChild / removeChild / clearChildren.
     * This is the principle of "controlling your invariants" —
     * the Container is the sole authority on its children list.
     */
    public final List<Component> getChildren() {
        return Collections.unmodifiableList(children);
    }

    /** Returns the number of children. */
    public final int childCount() {
        return children.size();
    }

    /** True if this container has no children. */
    public final boolean isEmpty() {
        return children.isEmpty();
    }

    // ── Dirty-aware rendering helper ──────────────────────────────────────

    /**
     * Returns true if ANY child in this subtree is dirty.
     *
     * The Renderer uses this to decide whether to re-render a subtree
     * or skip it entirely. If neither the container nor any of its
     * children are dirty, skip the whole thing.
     *
     * WHY RECURSIVE?
     * A Container may contain other Containers. The dirty signal can
     * come from any depth. We need to walk the full subtree.
     *
     * NOTE: isDirty() on the Container itself is also checked by
     * the Renderer directly. This method is an additional optimization
     * to short-circuit subtree traversal.
     */
    public final boolean isSubtreeDirty() {
        if (isDirty()) return true;
        for (Component child : children) {
            if (child instanceof Container c) {
                if (c.isSubtreeDirty()) return true;
            } else {
                if (child.isDirty()) return true;
            }
        }
        return false;
    }
}