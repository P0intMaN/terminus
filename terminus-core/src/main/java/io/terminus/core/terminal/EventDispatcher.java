package io.terminus.core.terminal;

import io.terminus.core.Component;
import io.terminus.core.Container;
import io.terminus.core.event.Event;

/**
 * Routes events to the focused component and walks the tree
 * upward until a component consumes the event.
 *
 * PATTERN: Chain of Responsibility
 *
 * The dispatch chain:
 *   focused component → its parent → its parent → ... → root
 *
 * Each component's onEvent() can:
 *   return true  → event consumed, chain stops
 *   return false → event not consumed, continues up the tree
 *
 * WHY BOTTOM-UP (LEAF TO ROOT)?
 * The most specific component gets first refusal. A TextInput
 * inside a Modal inside a Layout should handle a keypress before
 * the Layout gets a chance to. This mirrors how browser DOM
 * event bubbling works — and it's the right mental model for
 * nested UIs.
 *
 * FOCUS MANAGEMENT:
 * The focused component is the one that receives keyboard events.
 * Mouse events go to whichever component was clicked (hit-testing).
 * Focus changes when Tab is pressed or when a click changes it.
 */
public class EventDispatcher {

    /** The currently focused component. null = no focus (root handles all events). */
    private Component focused = null;

    /**
     * Dispatch an event through the component tree.
     *
     * @param event the event to dispatch
     * @param root  the root of the component tree (fallback handler)
     */
    public void dispatch(Event event, Component root) {
        Component target = (focused != null) ? focused : root;
        dispatchUpward(event, target, root);
    }

    /**
     * Walk from target up to root, stopping when consumed.
     */
    private void dispatchUpward(Event event, Component target, Component root) {
        Component current = target;
        while (current != null) {
            boolean consumed = current.onEvent(event);
            if (consumed) return;
            // Not consumed — move up to parent
            current = current.getParent();
        }
        // Reached the root without consumption — event is unhandled.
        // This is normal for many events (e.g. resize events when no
        // component specifically handles them).
    }

    // ── Focus management ──────────────────────────────────────────────────

    /** Set keyboard focus to a specific component. */
    public void setFocus(Component component) {
        this.focused = component;
    }

    /** Returns the currently focused component, or null. */
    public Component getFocused() {
        return focused;
    }

    /** Clear focus — root handles all events directly. */
    public void clearFocus() {
        this.focused = null;
    }
}