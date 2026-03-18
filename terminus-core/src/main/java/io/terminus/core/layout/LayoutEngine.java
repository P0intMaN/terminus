package io.terminus.core.layout;

import io.terminus.core.Bounds;
import io.terminus.core.Component;
import io.terminus.core.Constraint;
import io.terminus.core.Container;

/**
 * Assigns Bounds to every component in the tree before rendering.
 *
 * FULL IMPLEMENTATION: Step 5 (flex row/col layout, constraint solving).
 *
 * THIS STUB: Assigns the full terminal bounds to the root, then asks
 * each component for its preferred size via measure() and assigns that.
 * This lets the Renderer and ScreenBuffer work correctly in tests
 * without a complete layout algorithm.
 *
 * PATTERN: Template Method
 * The overall layout algorithm is fixed (measure → constrain → assign).
 * Subclasses (FlexLayout, FixedLayout) will override the constraint
 * solving step. The stub here is the simplest possible implementation.
 */
public class LayoutEngine {

    /**
     * Run a layout pass: assign Bounds to root and all descendants.
     *
     * @param root       the root component
     * @param constraint the terminal's available space
     * @param rootBounds the exact bounds to assign to the root
     */
    public void layout(Component root, Constraint constraint, Bounds rootBounds) {
        root.setBounds(rootBounds);

        if (root instanceof Container container) {
            layoutChildren(container, constraint, rootBounds);
        }
    }

    /**
     * Stub: give every child the full parent bounds.
     * Real implementation (Step 5) will do flex sizing, gap, padding, etc.
     */
    protected void layoutChildren(Container parent,
                                   Constraint constraint,
                                   Bounds parentBounds) {
        for (Component child : parent.getChildren()) {
            Bounds preferred = child.measure(constraint);
            // For now: clip preferred size to parent bounds
            int w = Math.min(preferred.width(),  parentBounds.width());
            int h = Math.min(preferred.height(), parentBounds.height());
            child.setBounds(new Bounds(parentBounds.x(), parentBounds.y(), w, h));

            if (child instanceof Container c) {
                Constraint childConstraint = Constraint.of(w, h);
                layoutChildren(c, childConstraint,
                    new Bounds(parentBounds.x(), parentBounds.y(), w, h));
            }
        }
    }
}