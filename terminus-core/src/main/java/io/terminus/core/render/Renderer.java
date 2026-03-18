package io.terminus.core.render;

import io.terminus.core.Bounds;
import io.terminus.core.Cell;
import io.terminus.core.Component;
import io.terminus.core.Constraint;
import io.terminus.core.Container;
import io.terminus.core.layout.LayoutEngine;

/**
 * Walks the component tree and composites all components into
 * the ScreenBuffer's back buffer.
 *
 * PATTERN: Visitor (structural)
 * The Renderer visits every node in the component tree without
 * the nodes knowing they're being visited.
 *
 * TWO ENTRY POINTS:
 *   renderFrame() — full pipeline: layout then render (production)
 *   renderOnly()  — render only, skip layout (tests + cached layouts)
 *
 * WHY SEPARATE THEM?
 * Mixing layout and rendering in one method makes the Renderer
 * impossible to unit test in isolation. Layout changes bounds;
 * tests need stable bounds to assert on positions. Separating
 * the two passes gives us testability without sacrificing the
 * convenience of renderFrame() for production callers.
 */
public class Renderer {

    private final ScreenBuffer screenBuffer;
    private final LayoutEngine layoutEngine;

    public Renderer(ScreenBuffer screenBuffer, LayoutEngine layoutEngine) {
        this.screenBuffer = screenBuffer;
        this.layoutEngine = layoutEngine;
    }

    /**
     * Full pipeline: run layout, then render into the back buffer.
     * This is what the EventLoop calls every frame in production.
     *
     * @param root the root component of the UI tree
     */
    public void renderFrame(Component root) {
        Constraint terminalConstraint = Constraint.of(
            screenBuffer.getCols(),
            screenBuffer.getRows()
        );
        Bounds rootBounds = new Bounds(
            0, 0,
            screenBuffer.getCols(),
            screenBuffer.getRows()
        );
        layoutEngine.layout(root, terminalConstraint, rootBounds);
        renderOnly(root);
    }

    /**
     * Render-only pipeline: skip layout, use existing bounds.
     *
     * USE CASES:
     *   1. Unit tests — bounds are set manually via LayoutAccess
     *   2. Future optimization — only re-layout when size changes,
     *      otherwise just re-render dirty components
     *
     * @param root the root component of the UI tree
     */
    public void renderOnly(Component root) {
        screenBuffer.clearBack();
        renderComponent(root);
    }

    // ── Private rendering walk ────────────────────────────────────────────

    private void renderComponent(Component component) {
        if (component.getBounds().isEmpty()) return;

        if (component instanceof Container container) {
            writeComponentToBuffer(container);
            for (Component child : container.getChildren()) {
                renderComponent(child);
            }
        } else {
            writeComponentToBuffer(component);
        }
    }

    private void writeComponentToBuffer(Component component) {
        Bounds bounds = component.getBounds();
        if (bounds.isEmpty()) return;

        Cell[][] localGrid = component.render();

        if (localGrid == null) {
            System.err.println("[Terminus] WARN: " +
                component.getClass().getSimpleName() +
                ".render() returned null. Return blankGrid() instead.");
            return;
        }

        screenBuffer.composite(localGrid, bounds.x(), bounds.y());
        component.clearDirty();
    }
}