package io.terminus.core;

/**
 * A controlled bridge that gives the layout engine access to
 * package-private Component internals.
 *
 * WHY THIS EXISTS:
 * Component.setBounds() is package-private — external code should
 * never set a component's position directly. But LayoutEngine lives
 * in a different package (io.terminus.core.layout) and legitimately
 * needs to assign bounds.
 *
 * This class lives in io.terminus.core (same package as Component),
 * so it CAN call setBounds(). It exposes that capability as a public
 * static method, creating a single, explicit, auditable entry point
 * for bounds assignment.
 *
 * PATTERN: Friend Accessor (also called Package-Private Bridge)
 * Common in JavaFX internals, Android's View system, and other
 * large Java frameworks that need controlled cross-package access
 * without making internals fully public.
 *
 * RULE: Only LayoutEngine should call these methods.
 * Nothing else in the codebase has a legitimate reason to set bounds.
 */
public final class LayoutAccess {

    // Prevent instantiation — this is a pure static utility class.
    private LayoutAccess() {}

    /**
     * Assign layout bounds to a component.
     * Called exclusively by LayoutEngine during the layout pass.
     */
    public static void setBounds(Component component, Bounds bounds) {
        component.setBounds(bounds); // package-private call — legal here
    }

    /**
     * Set a component's parent pointer.
     * Called exclusively by Container.addChild() / removeChild().
     *
     * NOTE: Container.addChild() already lives in io.terminus.core,
     * so it can set parent directly. This method is here for any
     * future cross-package code that needs parent access.
     */
    public static void setParent(Component component, Component parent) {
        component.parent = parent; // package-private field — legal here
    }
}