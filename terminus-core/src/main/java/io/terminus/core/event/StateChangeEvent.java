package io.terminus.core.event;

/**
 * Posted by background threads to signal a data change.
 * The payload is intentionally Object — callers cast to the expected type.
 *
 * WHY NOT GENERICS?
 * A generic Event<T> can't be sealed across mixed payload types cleanly.
 * The EventDispatcher just routes StateChangeEvents to registered
 * listeners by type token — the cast is safe at the dispatch site.
 */
public record StateChangeEvent(long timestamp, String key, Object payload) implements Event {}