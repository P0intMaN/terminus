package io.terminus.core.event;

public record MouseEvent(long timestamp, int col, int row, MouseButton button, MouseAction action) implements Event {

    public enum MouseButton { LEFT, MIDDLE, RIGHT, NONE }
    public enum MouseAction { PRESS, RELEASE, MOVE, SCROLL_UP, SCROLL_DOWN }
}