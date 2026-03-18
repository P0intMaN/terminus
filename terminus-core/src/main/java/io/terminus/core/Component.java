package io.terminus.core;

import io.terminus.core.event.Event;


/**
 * The abstract root of the Terminus component tree.
 *
 * PATTERN: Composite (Gang of Four)
 * Every node in the UI tree — whether it has children or not —
 * is a Component. This gives the Renderer a single, uniform
 * interface to walk the tree without ever checking types.
 *
 * WHAT THIS CLASS IS RESPONSIBLE FOR:
 *   1. Holding the component's position and size (bounds)
 *   2. Tracking dirty state and bubbling it to the parent
 *   3. Declaring the contract all components must fulfill
 *      (render, measure, onEvent)
 *
 * WHAT THIS CLASS IS NOT RESPONSIBLE FOR:
 *   - Managing children (that's Container's job)
 *   - Knowing what to draw (that's each subclass's job)
 *   - Knowing who is watching for dirty signals (zero coupling)
 */


public abstract class Component {

    // ── State ─────────────────────────────────────────────────────────────

    /**
     * The parent of this component, or null if this is the root.
     *
     * WHY PACKAGE-PRIVATE?
     * Only Container.addChild() and Container.removeChild() should
     * set this field. External code has no business touching it.
     * Package-private (no modifier) gives Container access while
     * keeping it hidden from the rest of the world.
     */

    Component parent;

    /**
     * Where this component lives on the terminal screen, in cell coords.
     * Set by the LayoutEngine before render() is called.
     * Starts as ZERO — "not yet laid out."
     */
    private Bounds bounds = Bounds.ZERO;

    /**
     * Whether this component's visual output has changed since the
     * last render pass. Starts true so the first frame always renders.
     *
     * WHY volatile?
     * Background threads (data loaders, animators) may call markDirty()
     * from a non-UI thread. The EventLoop reads dirty on the UI thread.
     * volatile guarantees the write is immediately visible across threads
     * without the overhead of full synchronization.
     *
     * This is a deliberate micro-optimization — we don't synchronize the
     * whole dirty-check because it happens every frame at 60fps.
     */
    private volatile boolean dirty = true;


    // ── Abstract contract ─────────────────────────────────────────────────

    /**
     * Produce this component's visual output as a 2D cell grid.
     *
     * CONTRACT:
     * - The returned grid must be exactly bounds.width() x bounds.height().
     * - This method must be pure — same bounds + same state = same output.
     * - This method must NOT call markDirty(). render() is observation, not mutation.
     * - After render() returns, the component should call clearDirty().
     *
     * CALLED BY: Renderer, on the UI thread only.
     */
    public abstract Cell[][] render();

    /**
     * Report the preferred size of this component given a constraint.
     *
     * The LayoutEngine calls this during the measurement pass to
     * determine how much space each component wants before assigning
     * final bounds. A component can ask for any size — the layout
     * engine decides what it actually gets.
     *
     * @param constraint the space available (max width/height)
     * @return the component's preferred Bounds (x and y are typically 0;
     *         only width and height matter during measurement)
     */
    public abstract Bounds measure(Constraint constraint);

    /**
     * Handle an input event. Return true if this component consumed it,
     * false to let it propagate further up the tree.
     *
     * PATTERN: Chain of Responsibility
     * The EventDispatcher calls onEvent() starting at the focused
     * component and walking up to the root. The first component that
     * returns true stops the chain.
     *
     * DEFAULT: does nothing, returns false (pass it up).
     * Leaf components that care about input (TextInput, Button) override this.
     * Most layout/display components never override it.
     */
    public boolean onEvent(Event event) {
        return false; // not consumed -- propogate
    }


    // ── Dirty tracking ────────────────────────────────────────────────────

    /**
     * Mark this component — and all its ancestors — as needing re-render.
     *
     * PATTERN: Observer (inverted — component notifies upward)
     * The component doesn't know who is watching. It just tells its
     * parent, which tells its parent, until the root is marked dirty.
     * The Renderer (the actual observer) polls root.isDirty() each frame.
     *
     * THREAD SAFETY: Safe to call from any thread.
     * Writes to volatile dirty, then recurses on parent.
     * The recursion stops at the root (where parent == null).
     *
     * CALL THIS whenever your component's visual state changes:
     *   - A data value changed
     *   - A style property changed
     *   - Focus was gained or lost
     */
    public final void markDirty() {
        dirty = true;
        if (parent != null) {
            parent.markDirty(); // bubble upward
        }
    }

    /**
     * Reset the dirty flag after a successful render.
     * Called by the Renderer, not by components themselves.
     *
     * WHY final?
     * No subclass should change this behavior. Dirty tracking is a
     * framework concern, not a component concern. Making it final
     * prevents subtle bugs where a subclass overrides clearDirty()
     * and breaks the render loop.
     */
    public final void clearDirty() {
        dirty = false;
    }

    /** True if this component needs to be re-rendered this frame. */
    public final boolean isDirty() {
        return dirty;
    }


    // ── Bounds management ─────────────────────────────────────────────────

    /**
     * Returns the current layout bounds of this component.
     * Will be Bounds.ZERO until the LayoutEngine runs.
     */
    public final Bounds getBounds() {
        return bounds;
    }

    /**
     * Set the layout bounds. Called exclusively by LayoutEngine.
     *
     * WHY NOT PUBLIC?
     * If external code could set bounds directly, it would bypass the
     * LayoutEngine and produce misaligned UIs. Package-private restricts
     * this to the layout package only.
     *
     * Note: does NOT call markDirty() automatically. The LayoutEngine
     * marks everything dirty after a layout pass.
     */
    final void setBounds(Bounds bounds) {
        this.bounds = bounds;
    }


    // ── Convenience accessors ─────────────────────────────────────────────
    // Shortcuts so components don't have to write getBounds().width() everywhere.

    protected final int getWidth()  { return bounds.width(); }
    protected final int getHeight() { return bounds.height(); }
    protected final int getX()      { return bounds.x(); }
    protected final int getY()      { return bounds.y(); }
}
