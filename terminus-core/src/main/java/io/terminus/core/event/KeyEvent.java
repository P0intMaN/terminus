package io.terminus.core.event;

/**
 * A keyboard input event.
 * Full implementation — key codes, modifiers — in Week 3.
 */
public record KeyEvent(long timestamp, String key, boolean ctrl, boolean alt, boolean shift) implements Event {}