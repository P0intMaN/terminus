package io.terminus.core.event;

/** Fired when the terminal window is resized. */
public record ResizeEvent(long timestamp, int cols, int rows) implements Event {}