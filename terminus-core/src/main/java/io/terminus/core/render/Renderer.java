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
 * The Renderer visits every node in the component tree without the
 * nodes knowing they're being visited. Components expose render()
 * but have no reference to the Renderer. This decoupling means:
 *   - We can add alternative renderers (e.g., an HTML snapshot renderer,
 *     a test renderer that captures output as a string) without touching
 *     any component.
 *   - Components stay focused on WHAT to draw. Renderer handles WHERE.
 *
 * RENDERING ALGORITHM:
 * 1. Clear the back buffer
 * 2. Run the layout engine to assign bounds to every component
 * 3. Walk the tree depth-first (leaves before parents)
 * 4. For each dirty component: call render(), composite into back buffer,
 *    clear the component's dirty flag
 * 5. The ScreenDiffer (Step 4) will compare back vs front
 *
 * THREAD SAFETY:
 * Must be called exclusively from the EventLoop's UI thread.
 * Never call render() from a background thread.
 */
public class Renderer {

    private final ScreenBuffer screenBuffer;
    private final LayoutEngine layoutEngine;

    public Renderer(ScreenBuffer screenBuffer, LayoutEngine layoutEngine) {
        this.screenBuffer = screenBuffer;
        this.layoutEngine = layoutEngine;
    }

    /**
     * Render a full frame into the back buffer.
     *
     * Steps:
     *   1. Clear the back buffer to blank
     *   2. Run layout: assign Bounds to every component
     *   3. Walk the tree: render each component into the back buffer
     *
     * After this method returns, the back buffer holds the complete
     * new frame. The caller (EventLoop) then passes it to ScreenDiffer.
     *
     * @param root the root component of the UI tree
     */
    public void renderFrame(Component root) {
        // Step 1: Clear — no stale cells from the previous frame
        screenBuffer.clearBack();

        // Step 2: Layout — give every component its Bounds
        // The root gets the full terminal size as its constraint
        Constraint terminalConstraint = Constraint.of(
            screenBuffer.getCols(),
            screenBuffer.getRows()
        );
        layoutEngine.layout(root, terminalConstraint,
            new Bounds(0, 0, screenBuffer.getCols(), screenBuffer.getRows()));

        // Step 3: Walk and composite
        renderComponent(root);
    }

    /**
     * Recursively render a component and all its descendants.
     *
     * WHY DEPTH-FIRST?
     * In a TUI, children render ON TOP of parents. If a Modal sits on
     * top of a Layout, the Layout renders first, then the Modal renders
     * over it. Depth-first ensures parents are drawn before children,
     * so children naturally overlay parents in the buffer.
     *
     * Wait — we said "leaves before parents" earlier. Let me be precise:
     * We visit PARENTS first to set context, then recurse to CHILDREN.
     * But for the actual buffer write, children write AFTER parents,
     * so children visually appear on top. This is standard painter's
     * algorithm ordering.
     *
     * SKIPPING CLEAN SUBTREES:
     * If a Container and its entire subtree are clean (!isSubtreeDirty),
     * we skip it entirely. This is the key render optimization —
     * unchanged parts of the UI cost zero render time.
     *
     * However: we ALWAYS re-composite into the back buffer regardless,
     * because clearBack() wiped it. Even clean components need their
     * cells written back. The "skip" optimization applies only to
     * calling render() — the expensive part — not the composite() call.
     *
     * This is a subtle but important distinction:
     *   render()    = compute new Cell[][] from component state (skip if clean)
     *   composite() = write existing Cell[][] into the back buffer (always do)
     *
     * For now we implement the simple correct version: always render.
     * Caching the last rendered Cell[][] per component is Step 5 (optimization pass).
     */
    private void renderComponent(Component component) {
        // Skip components with no allocated space
        if (component.getBounds().isEmpty()) return;

        if (component instanceof Container container) {
            // Render the container itself first (it may have a background/border)
            writeComponentToBuffer(container);
            // Then render all children on top, in order
            for (Component child : container.getChildren()) {
                renderComponent(child); // recurse
            }
        } else {
            // Leaf — just render it
            writeComponentToBuffer(component);
        }
    }

    /**
     * Call render() on a component and write its output into the back buffer.
     *
     * This is where the local → global coordinate transform happens.
     * The component's Cell[][] is in local space (0,0 is top-left of component).
     * We composite it into global screen space using the component's bounds.
     *
     * After writing, we clear the component's dirty flag — it has been rendered.
     */
    private void writeComponentToBuffer(Component component) {
        Bounds bounds = component.getBounds();

        // Guard: if the layout engine hasn't assigned bounds yet, skip
        if (bounds.isEmpty()) return;

        // Call the component's render() — this is the only place render() is called
        Cell[][] localGrid = component.render();

        // Validate the grid dimensions match the assigned bounds
        // Mismatch = component bug. We log and clip rather than crash.
        if (localGrid == null) {
            // Component returned null — treat as blank, log the bug
            System.err.println("[Terminus] WARN: " +
                component.getClass().getSimpleName() + ".render() returned null. " +
                "Return blankGrid() instead.");
            return;
        }

        // Composite: local (0,0) → global (bounds.x(), bounds.y())
        screenBuffer.composite(localGrid, bounds.x(), bounds.y());

        // Mark this component clean — it's been rendered this frame
        component.clearDirty();
    }
}