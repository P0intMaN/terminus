package io.terminus.core.components;

import io.terminus.core.Bounds;
import io.terminus.core.Cell;
import io.terminus.core.Constraint;
import io.terminus.core.LayoutAccess;
import io.terminus.core.event.KeyEvent;
import io.terminus.core.event.StateChangeEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("TextInput")
class TextInputTest {

    private TextInput input;

    // Helper: send a plain key event
    private void key(String k) {
        input.onEvent(new KeyEvent(0, k, false, false, false));
    }

    private void ctrlKey(String k) {
        input.onEvent(new KeyEvent(0, k, true, false, false));
    }

    private void shiftKey(String k) {
        input.onEvent(new KeyEvent(0, k, false, false, true));
    }

    // Helper: type a full string
    private void type(String text) {
        for (char c : text.toCharArray()) {
            input.onEvent(new KeyEvent(0, String.valueOf(c), false, false, false));
        }
    }

    // Helper: render to a string for easy assertion
    private String renderToString() {
        LayoutAccess.setBounds(input, new Bounds(0, 0, 20, 1));
        Cell[][] grid = input.render();
        StringBuilder sb = new StringBuilder();
        for (Cell cell : grid[0]) {
            sb.appendCodePoint(cell.glyph());
        }
        return sb.toString().stripTrailing();
    }

    @BeforeEach
    void setUp() {
        input = TextInput.builder().build();
        input.setFocused(true);
        LayoutAccess.setBounds(input, new Bounds(0, 0, 20, 1));
    }

    @Nested
    @DisplayName("typing")
    class Typing {

        @Test
        @DisplayName("typing characters inserts them into the buffer")
        void typing_insertsChars() {
            type("hello");
            assertThat(input.getText()).isEqualTo("hello");
        }

        @Test
        @DisplayName("typing advances the cursor")
        void typing_advancesCursor() {
            type("abc");
            assertThat(input.getCursorPos()).isEqualTo(3);
        }

        @Test
        @DisplayName("typing in the middle inserts at cursor position")
        void typing_inMiddle_insertsAtCursor() {
            type("helo");
            // Move left once — cursor is between 'l' and 'o'
            key("LEFT");
            type("l");
            assertThat(input.getText()).isEqualTo("hello");
        }

        @Test
        @DisplayName("typing marks component dirty")
        void typing_marksDirty() {
            input.clearDirty();
            type("a");
            assertThat(input.isDirty()).isTrue();
        }

        @Test
        @DisplayName("maxLength prevents typing beyond the limit")
        void maxLength_preventsOverflow() {
            input = TextInput.builder().maxLength(3).build();
            input.setFocused(true);
            type("abcdef");
            assertThat(input.getText()).isEqualTo("abc");
            assertThat(input.getText().length()).isEqualTo(3);
        }
    }

    @Nested
    @DisplayName("backspace and delete")
    class Deletion {

        @Test
        @DisplayName("Backspace removes char before cursor")
        void backspace_removesCharBefore() {
            type("hello");
            key("BACKSPACE");
            assertThat(input.getText()).isEqualTo("hell");
            assertThat(input.getCursorPos()).isEqualTo(4);
        }

        @Test
        @DisplayName("Delete removes char after cursor")
        void delete_removesCharAfter() {
            type("hello");
            key("HOME");
            key("DELETE");
            assertThat(input.getText()).isEqualTo("ello");
            assertThat(input.getCursorPos()).isEqualTo(0);
        }

        @Test
        @DisplayName("Backspace at position 0 does nothing")
        void backspace_atStart_doesNothing() {
            type("hi");
            key("HOME");
            key("BACKSPACE");
            assertThat(input.getText()).isEqualTo("hi");
        }

        @Test
        @DisplayName("Ctrl+K deletes from cursor to end")
        void ctrlK_deletesToEnd() {
            type("hello world");
            key("HOME");
            key("RIGHT"); key("RIGHT"); key("RIGHT"); key("RIGHT"); key("RIGHT");
            ctrlKey("K");
            assertThat(input.getText()).isEqualTo("hello");
        }

        @Test
        @DisplayName("Ctrl+U deletes from start to cursor")
        void ctrlU_deletesToStart() {
            type("hello world");
            key("END");
            key("LEFT"); key("LEFT"); key("LEFT"); key("LEFT"); key("LEFT");
            ctrlKey("U");
            assertThat(input.getText()).isEqualTo("world");
        }
    }

    @Nested
    @DisplayName("cursor movement")
    class CursorMovement {

        @Test
        @DisplayName("Left/Right arrow moves cursor")
        void arrows_moveCursor() {
            type("abc");
            assertThat(input.getCursorPos()).isEqualTo(3);
            key("LEFT");
            assertThat(input.getCursorPos()).isEqualTo(2);
            key("RIGHT");
            assertThat(input.getCursorPos()).isEqualTo(3);
        }

        @Test
        @DisplayName("Home moves cursor to position 0")
        void home_movesToStart() {
            type("hello");
            key("HOME");
            assertThat(input.getCursorPos()).isEqualTo(0);
        }

        @Test
        @DisplayName("End moves cursor to end of text")
        void end_movesToEnd() {
            type("hello");
            key("HOME");
            key("END");
            assertThat(input.getCursorPos()).isEqualTo(5);
        }

        @Test
        @DisplayName("Left arrow does not go below 0")
        void left_clampsAtZero() {
            key("LEFT"); key("LEFT");
            assertThat(input.getCursorPos()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("selection")
    class Selection {

        @Test
        @DisplayName("Shift+Right extends selection rightward")
        void shiftRight_extendsSelection() {
            type("hello");
            key("HOME");
            shiftKey("RIGHT");
            shiftKey("RIGHT");
            shiftKey("RIGHT");
            assertThat(input.getCursorPos()).isEqualTo(3);
        }

        @Test
        @DisplayName("Ctrl+A selects all text")
        void ctrlA_selectsAll() {
            type("hello world");
            ctrlKey("A");
            // After Ctrl+A, cursor should be at end, selection at 0
            assertThat(input.getCursorPos()).isEqualTo(11);
        }

        @Test
        @DisplayName("typing with selection replaces selected text")
        void typing_replacesSelection() {
            type("hello world");
            ctrlKey("A");
            type("bye");
            assertThat(input.getText()).isEqualTo("bye");
        }

        @Test
        @DisplayName("Backspace with selection deletes selected text")
        void backspace_deletesSelection() {
            type("hello world");
            ctrlKey("A");
            key("BACKSPACE");
            assertThat(input.getText()).isEqualTo("");
        }

        @Test
        @DisplayName("Right arrow without Shift collapses selection to right edge")
        void right_collapses_toRightEdge() {
            type("hello");
            key("HOME");
            shiftKey("END"); // select all
            key("RIGHT");    // collapse to right edge
            assertThat(input.getCursorPos()).isEqualTo(5);
        }

        @Test
        @DisplayName("Left arrow without Shift collapses selection to left edge")
        void left_collapses_toLeftEdge() {
            type("hello");
            key("HOME");
            shiftKey("END"); // select all
            key("LEFT");     // collapse to left edge
            assertThat(input.getCursorPos()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("clipboard")
    class Clipboard {

        @Test
        @DisplayName("Ctrl+C copies selected text")
        void ctrlC_copies() {
            type("hello");
            ctrlKey("A");
            ctrlKey("C");
            // Clear buffer and paste
            ctrlKey("A");
            key("DELETE");
            ctrlKey("V");
            assertThat(input.getText()).isEqualTo("hello");
        }

        @Test
        @DisplayName("Ctrl+X cuts selected text")
        void ctrlX_cuts() {
            type("hello world");
            ctrlKey("A");
            ctrlKey("X");
            assertThat(input.getText()).isEqualTo("");
            ctrlKey("V");
            assertThat(input.getText()).isEqualTo("hello world");
        }
    }

    @Nested
    @DisplayName("history")
    class History {

        @Test
        @DisplayName("submitted text is stored in history")
        void submit_storesInHistory() {
            type("first");
            key("ENTER");
            type("second");
            key("ENTER");
            // Now press Up — should get "second"
            key("UP");
            assertThat(input.getText()).isEqualTo("second");
            // Press Up again — should get "first"
            key("UP");
            assertThat(input.getText()).isEqualTo("first");
        }

        @Test
        @DisplayName("Down arrow after history navigation restores original buffer")
        void down_restoresSavedBuffer() {
            type("first");
            key("ENTER");
            type("current");
            key("UP");                   // go to "first"
            key("DOWN");                 // back to saved buffer
            assertThat(input.getText()).isEqualTo("current");
        }
    }

    @Nested
    @DisplayName("submit callback")
    class SubmitCallback {

        @Test
        @DisplayName("onSubmit callback is called with the text on Enter")
        void onSubmit_calledWithText() {
            List<String> submitted = new ArrayList<>();
            input.setOnSubmit(submitted::add);
            type("hello");
            key("ENTER");
            assertThat(submitted).containsExactly("hello");
        }

        @Test
        @DisplayName("buffer is cleared after submit")
        void bufferCleared_afterSubmit() {
            type("hello");
            key("ENTER");
            assertThat(input.getText()).isEqualTo("");
            assertThat(input.getCursorPos()).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("onChange callback")
    class OnChangeCallback {

        @Test
        @DisplayName("onChange fires on every character typed")
        void onChange_firesOnTyping() {
            List<String> changes = new ArrayList<>();
            input.setOnChange(changes::add);
            type("abc");
            assertThat(changes).containsExactly("a", "ab", "abc");
        }
    }

    @Nested
    @DisplayName("unfocused behaviour")
    class Unfocused {

        @Test
        @DisplayName("key events are ignored when unfocused")
        void unfocused_ignoresKeys() {
            input.setFocused(false);
            type("hello");
            assertThat(input.getText()).isEqualTo("");
        }

        @Test
        @DisplayName("placeholder shown when empty and unfocused")
        void placeholder_shownWhenEmptyAndUnfocused() {
            input = TextInput.builder()
                .placeholder("Type here...")
                .build();
            input.setFocused(false);
            LayoutAccess.setBounds(input, new Bounds(0, 0, 20, 1));
            Cell[][] grid = input.render();
            StringBuilder sb = new StringBuilder();
            for (Cell cell : grid[0]) sb.appendCodePoint(cell.glyph());
            assertThat(sb.toString()).startsWith("Type here...");
        }
    }

    @Nested
    @DisplayName("blink")
    class Blink {

        @Test
        @DisplayName("blink StateChangeEvent toggles cursor visibility and marks dirty")
        void blink_togglesVisibility() {
            input.clearDirty();
            input.onEvent(new StateChangeEvent(0, "blink", null));
            assertThat(input.isDirty()).isTrue();
        }

        @Test
        @DisplayName("blink event is ignored when unfocused")
        void blink_ignoredWhenUnfocused() {
            input.setFocused(false);
            input.clearDirty();
            input.onEvent(new StateChangeEvent(0, "blink", null));
            assertThat(input.isDirty()).isFalse();
        }
    }
}