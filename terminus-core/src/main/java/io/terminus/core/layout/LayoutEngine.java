package io.terminus.core.layout;

import io.terminus.core.Bounds;
import io.terminus.core.Component;
import io.terminus.core.Constraint;
import io.terminus.core.Container;
import io.terminus.core.LayoutAccess;

/**
 * Assigns Bounds to every component in the tree before rendering.
 *
 * FULL IMPLEMENTATION: Step 5 (flex row/col layout, constraint solving).
 *
 * THIS STUB: Assigns the full terminal bounds to the root, then asks
 * each component for its preferred size via measure() and assigns that.
 *
 * PATTERN: Template Method
 */
public class LayoutEngine {

    public void layout(Component root, Constraint constraint, Bounds rootBounds) {
        LayoutAccess.setBounds(root, rootBounds); // ← was: root.setBounds(rootBounds)

        if (root instanceof Container container) {
            layoutChildren(container, constraint, rootBounds);
        }
    }

    protected void layoutChildren(Container parent,
                                   Constraint constraint,
                                   Bounds parentBounds) {
        for (Component child : parent.getChildren()) {
            Bounds preferred = child.measure(constraint);
            int w = Math.min(preferred.width(),  parentBounds.width());
            int h = Math.min(preferred.height(), parentBounds.height());
            Bounds childBounds = new Bounds(parentBounds.x(), parentBounds.y(), w, h);
            LayoutAccess.setBounds(child, childBounds); // ← was: child.setBounds(...)

            if (child instanceof Container c) {
                Constraint childConstraint = Constraint.of(w, h);
                layoutChildren(c, childConstraint, childBounds);
            }
        }
    }
}