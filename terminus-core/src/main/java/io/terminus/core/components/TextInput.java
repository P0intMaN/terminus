package io.terminus.core.components;

import io.terminus.core.Bounds;
import io.terminus.core.Cell;
import io.terminus.core.Constraint;
import io.terminus.core.Leaf;
import io.terminus.core.event.Event;
import io.terminus.core.event.KeyEvent;
import io.terminus.core.event.StateChangeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A single-line editable text input field.
 *
 * Handles:
 *   - Character insertion and deletion
 *   - Cursor movement (arrows, Home, End, Ctrl+arrows)
 *   - Text selection (Shift+arrows, Shift+Home/End, Ctrl+A)
 *   - Clipboard-style operations (Ctrl+X cut, Ctrl+C copy, Ctrl+V paste)
 *   - Input history (Up/Down arrows cycle through previous submissions)
 *   - Horizontal scrolling when text exceeds component width
 *   - Cursor blinking (driven by StateChangeEvent("blink"))
 *   - Submit callback on Enter
 *
 * PATTERN: Command (each edit operation is a discrete action)
 * Each key handler (insertChar, deleteBackward, moveRight, etc.)
 * is a self-contained method that mutates state consistently.
 * This makes the operations easy to test, easy to extend with
 * undo/redo later, and easy to reason about individually.
 *
 * FOCUSED vs UNFOCUSED:
 * When unfocused, the component renders as a plain text field.
 * When focused, it shows the cursor and responds to key events.
 * Focus is managed externally by EventDispatcher.setFocus().
 */
public class TextInput extends Leaf {

    // ── Configuration ─────────────────────────────────────────────────────

    private final int     maxLength;
    private final String  placeholder;
    private final int     fg;
    private final int     bg;
    private final int     cursorFg;
    private final int     cursorBg;
    private final int     selectionFg;
    private final int     selectionBg;
    private final int     placeholderFg;

    /** Called when the user presses Enter. Receives the submitted text. */
    private Consumer<String> onSubmit;

    /** Called on every keystroke that changes the value. */
    private Consumer<String> onChange;

    // ── Core state ────────────────────────────────────────────────────────

    /**
     * The text content.
     *
     * WHY StringBuilder AND NOT String?
     * String is immutable — every edit creates a new String object.
     * For a text field that may receive hundreds of keystrokes,
     * that's hundreds of allocations. StringBuilder is mutable and
     * designed for exactly this use case: incremental character
     * insertion and deletion at arbitrary positions.
     */
    private final StringBuilder buffer = new StringBuilder();

    /**
     * Cursor position in [0, buffer.length()].
     * 0 = before the first character.
     * buffer.length() = after the last character.
     */
    private int cursorPos = 0;

    /**
     * The anchor point of a text selection, or -1 if no selection.
     *
     * A selection spans from selectionStart to cursorPos.
     * The anchor is FIXED while the cursor moves with Shift+Arrow.
     * selectionStart can be > cursorPos (selecting backwards is valid).
     */
    private int selectionStart = -1;

    /**
     * How many characters are scrolled off the left edge.
     * Ensures the cursor is always within the visible window.
     */
    private int scrollOffset = 0;

    /** Whether this component currently has keyboard focus. */
    private boolean focused = false;

    /**
     * Cursor blink state — true = cursor visible, false = cursor hidden.
     * Toggled by StateChangeEvent("blink") from an external timer.
     */
    private boolean cursorVisible = true;

    /**
     * The "clipboard" — text cut with Ctrl+X, pasted with Ctrl+V.
     * This is internal to the application, not the system clipboard.
     */
    private String clipboard = "";

    // ── History ───────────────────────────────────────────────────────────

    /**
     * Previous submitted values, newest at the end.
     * Navigated with Up/Down arrows when the buffer is empty
     * or when already browsing history.
     */
    private final List<String> history = new ArrayList<>();

    /**
     * Current position in history. -1 = not browsing history.
     * history.size() - 1 = oldest entry.
     */
    private int historyIndex = -1;

    /**
     * The text that was in the buffer when the user started
     * browsing history — restored when they press Down past
     * the newest history entry.
     */
    private String savedBuffer = "";

    // ── Constructor ───────────────────────────────────────────────────────

    private TextInput(Builder b) {
        this.maxLength     = b.maxLength;
        this.placeholder   = b.placeholder;
        this.fg            = b.fg;
        this.bg            = b.bg;
        this.cursorFg      = b.cursorFg;
        this.cursorBg      = b.cursorBg;
        this.selectionFg   = b.selectionFg;
        this.selectionBg   = b.selectionBg;
        this.placeholderFg = b.placeholderFg;
        this.onSubmit      = b.onSubmit;
        this.onChange      = b.onChange;
    }

    // ── Builder ───────────────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private int      maxLength     = Integer.MAX_VALUE;
        private String   placeholder   = "";
        private int      fg            = 0xF0EFF8;
        private int      bg            = Cell.DEFAULT_COLOR;
        private int      cursorFg      = 0x0a0a0f;
        private int      cursorBg      = 0x7F77DD;
        private int      selectionFg   = 0x0a0a0f;
        private int      selectionBg   = 0x378ADD;
        private int      placeholderFg = 0x555566;
        private Consumer<String> onSubmit = null;
        private Consumer<String> onChange = null;

        public Builder maxLength(int n)              { this.maxLength     = n;  return this; }
        public Builder placeholder(String p)         { this.placeholder   = p;  return this; }
        public Builder fg(int color)                 { this.fg            = color; return this; }
        public Builder bg(int color)                 { this.bg            = color; return this; }
        public Builder cursorColor(int fg, int bg)   {
            this.cursorFg = fg; this.cursorBg = bg;  return this; }
        public Builder selectionColor(int fg, int bg){
            this.selectionFg = fg; this.selectionBg = bg; return this; }
        public Builder placeholderFg(int color)      { this.placeholderFg = color; return this; }
        public Builder onSubmit(Consumer<String> cb) { this.onSubmit      = cb;  return this; }
        public Builder onChange(Consumer<String> cb) { this.onChange      = cb;  return this; }

        public TextInput build() { return new TextInput(this); }
    }

    // ── Public API ────────────────────────────────────────────────────────

    public String getText()  { return buffer.toString(); }
    public int    getCursorPos() { return cursorPos; }
    public boolean isFocused()   { return focused; }

    /** Set focus programmatically. */
    public void setFocused(boolean focused) {
        if (this.focused != focused) {
            this.focused     = focused;
            this.cursorVisible = true; // reset blink on focus change
            markDirty();
        }
    }

    /** Replace entire content programmatically. */
    public void setText(String text) {
        buffer.setLength(0);
        buffer.append(text == null ? "" : text);
        cursorPos      = buffer.length();
        selectionStart = -1;
        scrollOffset   = 0;
        clampScrollOffset();
        markDirty();
    }

    /** Callback setters (can be set after construction). */
    public void setOnSubmit(Consumer<String> cb) { this.onSubmit = cb; }
    public void setOnChange(Consumer<String> cb) { this.onChange = cb; }

    // ── Event handling ────────────────────────────────────────────────────

    @Override
    public boolean onEvent(Event event) {

        // Blink timer — toggle cursor regardless of focus
        if (event instanceof StateChangeEvent s && "blink".equals(s.key())) {
            if (focused) {
                cursorVisible = !cursorVisible;
                markDirty();
            }
            return false; // don't consume — other components may need it
        }

        // Only handle key events when focused
        if (!focused) return false;
        if (!(event instanceof KeyEvent k)) return false;

        // Reset blink on any keypress — cursor should be visible immediately
        cursorVisible = true;

        return switch (k.key()) {
            // ── Navigation ────────────────────────────────────────────
            case "LEFT"  -> { moveLeft(k.shift(), k.ctrl());  yield true; }
            case "RIGHT" -> { moveRight(k.shift(), k.ctrl()); yield true; }
            case "HOME"  -> { moveHome(k.shift());             yield true; }
            case "END"   -> { moveEnd(k.shift());              yield true; }
            case "UP"    -> { historyBack();                   yield true; }
            case "DOWN"  -> { historyForward();                yield true; }

            // ── Editing ───────────────────────────────────────────────
            case "BACKSPACE" -> { deleteBackward(); yield true; }
            case "DELETE"    -> { deleteForward();  yield true; }
            case "ENTER"     -> { submit();          yield true; }

            // ── Ctrl combos ───────────────────────────────────────────
            default -> {
                if (k.ctrl()) {
                    yield switch (k.key()) {
                        case "A" -> { selectAll();    yield true; }
                        case "C" -> { copySelection(); yield true; }
                        case "X" -> { cutSelection();  yield true; }
                        case "V" -> { paste();         yield true; }
                        case "K" -> { deleteToEnd();   yield true; }
                        case "U" -> { deleteToStart(); yield true; }
                        default  -> false;
                    };
                }
                // Printable character — insert it
                if (k.key().length() == 1 && !k.ctrl() && !k.alt()) {
                    insertChar(k.key().charAt(0));
                    yield true;
                }
                yield false;
            }
        };
    }

    // ── Navigation commands ───────────────────────────────────────────────

    private void moveLeft(boolean shift, boolean ctrl) {
        if (!shift && hasSelection()) {
            // Without shift: collapse selection to the left edge
            cursorPos      = Math.min(cursorPos, selectionStart);
            selectionStart = -1;
        } else {
            if (shift && !hasSelection()) selectionStart = cursorPos;
            if (ctrl) {
                cursorPos = prevWordBoundary(cursorPos);
            } else {
                if (cursorPos > 0) cursorPos--;
            }
            if (shift && cursorPos == selectionStart) selectionStart = -1;
        }
        clampScrollOffset();
        markDirty();
    }

    private void moveRight(boolean shift, boolean ctrl) {
        if (!shift && hasSelection()) {
            // Without shift: collapse selection to the right edge
            cursorPos      = Math.max(cursorPos, selectionStart);
            selectionStart = -1;
        } else {
            if (shift && !hasSelection()) selectionStart = cursorPos;
            if (ctrl) {
                cursorPos = nextWordBoundary(cursorPos);
            } else {
                if (cursorPos < buffer.length()) cursorPos++;
            }
            if (shift && cursorPos == selectionStart) selectionStart = -1;
        }
        clampScrollOffset();
        markDirty();
    }

    private void moveHome(boolean shift) {
        if (shift && !hasSelection()) selectionStart = cursorPos;
        cursorPos = 0;
        if (shift && cursorPos == selectionStart) selectionStart = -1;
        if (!shift) selectionStart = -1;
        clampScrollOffset();
        markDirty();
    }

    private void moveEnd(boolean shift) {
        if (shift && !hasSelection()) selectionStart = cursorPos;
        cursorPos = buffer.length();
        if (shift && cursorPos == selectionStart) selectionStart = -1;
        if (!shift) selectionStart = -1;
        clampScrollOffset();
        markDirty();
    }

    // ── Edit commands ─────────────────────────────────────────────────────

    /**
     * Insert a single character at the cursor position.
     * If text is selected, the selection is replaced first.
     */
    private void insertChar(char c) {
        if (buffer.length() >= maxLength && !hasSelection()) return;
        if (hasSelection()) deleteSelectedText();
        buffer.insert(cursorPos, c);
        cursorPos++;
        clampScrollOffset();
        fireOnChange();
        markDirty();
    }

    /**
     * Delete the character before the cursor (Backspace).
     * If text is selected, delete the selection instead.
     */
    private void deleteBackward() {
        if (hasSelection()) {
            deleteSelectedText();
        } else if (cursorPos > 0) {
            buffer.deleteCharAt(cursorPos - 1);
            cursorPos--;
        }
        clampScrollOffset();
        fireOnChange();
        markDirty();
    }

    /**
     * Delete the character after the cursor (Delete key).
     * If text is selected, delete the selection instead.
     */
    private void deleteForward() {
        if (hasSelection()) {
            deleteSelectedText();
        } else if (cursorPos < buffer.length()) {
            buffer.deleteCharAt(cursorPos);
        }
        clampScrollOffset();
        fireOnChange();
        markDirty();
    }

    private void deleteToEnd() {
        if (cursorPos < buffer.length()) {
            buffer.delete(cursorPos, buffer.length());
            selectionStart = -1;
            fireOnChange();
            markDirty();
        }
    }

    private void deleteToStart() {
        if (cursorPos > 0) {
            buffer.delete(0, cursorPos);
            cursorPos      = 0;
            selectionStart = -1;
            scrollOffset   = 0;
            fireOnChange();
            markDirty();
        }
    }

    /** Submit the current text and add to history. */
    private void submit() {
        String text = buffer.toString();
        if (!text.isEmpty()) {
            history.add(text);
        }
        historyIndex = -1;
        savedBuffer  = "";
        buffer.setLength(0);
        cursorPos      = 0;
        selectionStart = -1;
        scrollOffset   = 0;
        markDirty();
        if (onSubmit != null) onSubmit.accept(text);
    }

    // ── Selection commands ────────────────────────────────────────────────

    private void selectAll() {
        selectionStart = 0;
        cursorPos      = buffer.length();
        clampScrollOffset();
        markDirty();
    }

    private void copySelection() {
        if (hasSelection()) {
            int lo = Math.min(cursorPos, selectionStart);
            int hi = Math.max(cursorPos, selectionStart);
            clipboard = buffer.substring(lo, hi);
        }
        // Copy doesn't mark dirty — nothing visible changed
    }

    private void cutSelection() {
        if (hasSelection()) {
            copySelection();
            deleteSelectedText();
            fireOnChange();
            markDirty();
        }
    }

    private void paste() {
        if (!clipboard.isEmpty()) {
            if (hasSelection()) deleteSelectedText();
            // Respect maxLength
            int available = maxLength - buffer.length();
            String insert  = clipboard.length() <= available
                ? clipboard
                : clipboard.substring(0, available);
            buffer.insert(cursorPos, insert);
            cursorPos += insert.length();
            clampScrollOffset();
            fireOnChange();
            markDirty();
        }
    }

    // ── History navigation ────────────────────────────────────────────────

    private void historyBack() {
        if (history.isEmpty()) return;
        if (historyIndex == -1) {
            // Starting to browse — save current buffer
            savedBuffer  = buffer.toString();
            historyIndex = history.size() - 1;
        } else if (historyIndex > 0) {
            historyIndex--;
        } else {
            return; // already at oldest entry
        }
        setText(history.get(historyIndex));
        moveEnd(false);
    }

    private void historyForward() {
        if (historyIndex == -1) return;
        if (historyIndex < history.size() - 1) {
            historyIndex++;
            setText(history.get(historyIndex));
        } else {
            // Past the newest — restore the saved buffer
            historyIndex = -1;
            setText(savedBuffer);
        }
        moveEnd(false);
    }

    // ── Scroll management ─────────────────────────────────────────────────

    /**
     * Adjust scrollOffset so the cursor is always within the visible window.
     *
     * Called after every operation that moves the cursor.
     * The visible window shows characters [scrollOffset, scrollOffset + visibleWidth).
     */
    private void clampScrollOffset() {
        int visibleWidth = Math.max(1, getWidth());

        // Cursor scrolled off the left — scroll left
        if (cursorPos < scrollOffset) {
            scrollOffset = cursorPos;
        }

        // Cursor scrolled off the right — scroll right
        if (cursorPos >= scrollOffset + visibleWidth) {
            scrollOffset = cursorPos - visibleWidth + 1;
        }

        // Never scroll further right than necessary
        int maxScroll = Math.max(0, buffer.length() - visibleWidth + 1);
        scrollOffset  = Math.min(scrollOffset, maxScroll);
        scrollOffset  = Math.max(0, scrollOffset);
    }

    // ── Word boundary helpers ─────────────────────────────────────────────

    /** Find the start of the previous word (Ctrl+Left). */
    private int prevWordBoundary(int pos) {
        if (pos == 0) return 0;
        int i = pos - 1;
        // Skip whitespace
        while (i > 0 && Character.isWhitespace(buffer.charAt(i))) i--;
        // Skip word characters
        while (i > 0 && !Character.isWhitespace(buffer.charAt(i - 1))) i--;
        return i;
    }

    /** Find the end of the next word (Ctrl+Right). */
    private int nextWordBoundary(int pos) {
        int len = buffer.length();
        if (pos >= len) return len;
        int i = pos;
        // Skip word characters
        while (i < len && !Character.isWhitespace(buffer.charAt(i))) i++;
        // Skip whitespace
        while (i < len && Character.isWhitespace(buffer.charAt(i))) i++;
        return i;
    }

    // ── Internal helpers ──────────────────────────────────────────────────

    private boolean hasSelection() {
        return selectionStart >= 0 && selectionStart != cursorPos;
    }

    /**
     * Delete the currently selected text and collapse cursor
     * to the start of the (now deleted) selection.
     */
    private void deleteSelectedText() {
        if (!hasSelection()) return;
        int lo = Math.min(cursorPos, selectionStart);
        int hi = Math.max(cursorPos, selectionStart);
        buffer.delete(lo, hi);
        cursorPos      = lo;
        selectionStart = -1;
    }

    private void fireOnChange() {
        if (onChange != null) onChange.accept(buffer.toString());
    }

    // ── Leaf contract — rendering ─────────────────────────────────────────

    @Override
    public Bounds measure(Constraint c) {
        // TextInput is always 1 row tall.
        // Width: take all available space, minimum 5.
        int width = c.isWidthUnbounded() ? 20 : Math.max(5, c.maxWidth());
        return Bounds.of(width, 1);
    }

    @Override
    public Cell[][] render() {
        Cell[][] grid = blankGrid();
        if (grid[0].length == 0) return grid;

        int visibleWidth = grid[0].length;

        // Show placeholder when empty and unfocused
        if (buffer.isEmpty() && !focused && !placeholder.isEmpty()) {
            writeString(grid, 0, 0, placeholder,
                placeholderFg, bg, Cell.ATTR_NONE);
            return grid;
        }

        // Determine selection range in visible coords
        int selLo = -1, selHi = -1;
        if (hasSelection()) {
            selLo = Math.min(cursorPos, selectionStart) - scrollOffset;
            selHi = Math.max(cursorPos, selectionStart) - scrollOffset;
        }

        // Render visible characters
        for (int col = 0; col < visibleWidth; col++) {
            int bufIdx = col + scrollOffset;

            // Determine which colors apply at this column
            boolean inSelection = selLo >= 0 && col >= selLo && col < selHi;
            boolean isCursor    = focused && cursorVisible
                                  && bufIdx == cursorPos;

            int cellFg, cellBg;
            byte attrs = Cell.ATTR_NONE;

            if (isCursor) {
                cellFg = cursorFg;
                cellBg = cursorBg;
            } else if (inSelection) {
                cellFg = selectionFg;
                cellBg = selectionBg;
            } else {
                cellFg = fg;
                cellBg = bg;
            }

            char c;
            if (bufIdx < buffer.length()) {
                c = buffer.charAt(bufIdx);
            } else if (isCursor) {
                c = ' '; // cursor past end of text — draw on blank cell
            } else {
                c = ' '; // empty space after text
            }

            grid[0][col] = new Cell((int) c, cellFg, cellBg, attrs, (byte) 1);
        }

        return grid;
    }
}