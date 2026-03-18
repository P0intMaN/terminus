package io.terminus.core.layout;

import io.terminus.core.Bounds;
import io.terminus.core.Component;
import io.terminus.core.Constraint;
import io.terminus.core.Container;
import io.terminus.core.LayoutAccess;
import io.terminus.core.components.Layout;

/**
 * Assigns Bounds to every component in the tree before rendering.
 *
 * PATTERN: Template Method
 * The algorithm is: measure → constrain → assign → recurse.
 * For Layout containers, we delegate to Layout.performLayout()
 * which implements the three-pass flex algorithm.
 * For other containers (custom ones), we fall back to the stub
 * behaviour: give each child the full parent bounds.
 *
 * This means third-party Container subclasses work out of the
 * box (with naive layout), while Layout containers get the full
 * flex treatment.
 */
public class LayoutEngine {

    public void layout(Component root, Constraint constraint, Bounds rootBounds) {
        LayoutAccess.setBounds(root, rootBounds);

        if (root instanceof Layout layout) {
            layout.performLayout();
        } else if (root instanceof Container container) {
            layoutChildren(container, constraint, rootBounds);
        }
    }

    /**
     * Fallback for non-Layout containers: give every child the
     * full parent bounds. Used by custom Container subclasses
     * and the test doubles in our test suite.
     */
    protected void layoutChildren(Container parent,
                                   Constraint constraint,
                                   Bounds parentBounds) {
        for (Component child : parent.getChildren()) {
            Bounds preferred = child.measure(constraint);
            int w = Math.min(preferred.width(),  parentBounds.width());
            int h = Math.min(preferred.height(), parentBounds.height());
            Bounds childBounds = new Bounds(
                parentBounds.x(), parentBounds.y(), w, h);
            LayoutAccess.setBounds(child, childBounds);

            if (child instanceof Layout nested) {
                nested.performLayout();
            } else if (child instanceof Container c) {
                Constraint childConstraint = Constraint.of(w, h);
                layoutChildren(c, childConstraint, childBounds);
            }
        }
    }
}