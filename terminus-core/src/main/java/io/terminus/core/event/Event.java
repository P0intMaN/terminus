package io.terminus.core.event;

/**
 * Sealed hierarchy of all input events in Terminus.
 *
 * WHY SEALED?
 * Java 21 sealed interfaces restrict which classes can implement this
 * interface to the ones we explicitly permit. This means:
 *   1. The compiler knows the complete set of event types
 *   2. switch expressions on Event are exhaustive — no default needed
 *   3. Adding a new event type forces you to handle it everywhere
 *
 * This is far safer than an open interface where any class could
 * claim to be an Event.
 *
 * We declare the permits list now and fill in the implementations
 * in Week 3. The stub compiles and lets Component.java reference it.
 */
public sealed interface Event permits KeyEvent, MouseEvent, ResizeEvent, StateChangeEvent {

    /** When this event was created, in nanoseconds (System.nanoTime()). */
    long timestamp();
}