package io.terminus.core.terminal;

import io.terminus.core.event.KeyEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

@DisplayName("KeyParser")
class KeyParserTest {

    private KeyParser parser;

    @BeforeEach
    void setUp() { parser = new KeyParser(); }

    /** Feed a sequence of bytes to the parser, return the last non-null event. */
    private KeyEvent feed(byte... bytes) {
        KeyEvent last = null;
        for (byte b : bytes) {
            KeyEvent evt = parser.process(b);
            if (evt != null) last = evt;
        }
        return last;
    }

    @Nested
    @DisplayName("printable characters")
    class Printable {

        @Test @DisplayName("lowercase letter produces key event")
        void lowercase_producesKeyEvent() {
            KeyEvent evt = feed((byte) 'a');
            assertThat(evt).isNotNull();
            assertThat(evt.key()).isEqualTo("a");
            assertThat(evt.ctrl()).isFalse();
            assertThat(evt.alt()).isFalse();
        }

        @Test @DisplayName("space character produces key event")
        void space_producesKeyEvent() {
            KeyEvent evt = feed((byte) ' ');
            assertThat(evt.key()).isEqualTo(" ");
        }
    }

    @Nested
    @DisplayName("special keys")
    class SpecialKeys {

        @Test @DisplayName("Enter key (0x0D) produces ENTER")
        void enter_produces() {
            assertThat(feed((byte) 0x0D).key()).isEqualTo("ENTER");
        }

        @Test @DisplayName("Backspace (0x7F) produces BACKSPACE")
        void backspace_produces() {
            assertThat(feed((byte) 0x7F).key()).isEqualTo("BACKSPACE");
        }

        @Test @DisplayName("Tab (0x09) produces TAB")
        void tab_produces() {
            assertThat(feed((byte) 0x09).key()).isEqualTo("TAB");
        }
    }

    @Nested
    @DisplayName("control characters")
    class ControlChars {

        @Test @DisplayName("Ctrl+A (0x01) produces key=A with ctrl=true")
        void ctrlA_produces() {
            KeyEvent evt = feed((byte) 0x01);
            assertThat(evt.key()).isEqualTo("A");
            assertThat(evt.ctrl()).isTrue();
        }

        @Test @DisplayName("Ctrl+C (0x03) produces key=C with ctrl=true")
        void ctrlC_produces() {
            KeyEvent evt = feed((byte) 0x03);
            assertThat(evt.key()).isEqualTo("C");
            assertThat(evt.ctrl()).isTrue();
        }
    }

    @Nested
    @DisplayName("arrow keys (escape sequences)")
    class ArrowKeys {

        @Test @DisplayName("ESC [ A produces UP")
        void arrowUp_produces() {
            KeyEvent evt = feed((byte)0x1B, (byte)'[', (byte)'A');
            assertThat(evt).isNotNull();
            assertThat(evt.key()).isEqualTo("UP");
            assertThat(evt.ctrl()).isFalse();
        }

        @Test @DisplayName("ESC [ B produces DOWN")
        void arrowDown_produces() {
            KeyEvent evt = feed((byte)0x1B, (byte)'[', (byte)'B');
            assertThat(evt.key()).isEqualTo("DOWN");
        }

        @Test @DisplayName("ESC [ C produces RIGHT")
        void arrowRight_produces() {
            KeyEvent evt = feed((byte)0x1B, (byte)'[', (byte)'C');
            assertThat(evt.key()).isEqualTo("RIGHT");
        }

        @Test @DisplayName("ESC [ D produces LEFT")
        void arrowLeft_produces() {
            KeyEvent evt = feed((byte)0x1B, (byte)'[', (byte)'D');
            assertThat(evt.key()).isEqualTo("LEFT");
        }

        @Test @DisplayName("Ctrl+Up (ESC[1;5A) produces UP with ctrl=true")
        void ctrlArrowUp_produces() {
            // ESC [ 1 ; 5 A
            KeyEvent evt = feed(
                (byte)0x1B, (byte)'[',
                (byte)'1', (byte)';', (byte)'5',
                (byte)'A'
            );
            assertThat(evt.key()).isEqualTo("UP");
            assertThat(evt.ctrl()).isTrue();
        }
    }

    @Nested
    @DisplayName("function keys")
    class FunctionKeys {

        @Test @DisplayName("ESC O P produces F1")
        void f1_produces() {
            KeyEvent evt = feed((byte)0x1B, (byte)'O', (byte)'P');
            assertThat(evt.key()).isEqualTo("F1");
        }

        @Test @DisplayName("ESC [ 1 5 ~ produces F5")
        void f5_produces() {
            KeyEvent evt = feed(
                (byte)0x1B, (byte)'[',
                (byte)'1', (byte)'5',
                (byte)'~'
            );
            assertThat(evt.key()).isEqualTo("F5");
        }
    }

    @Nested
    @DisplayName("parser state reset")
    class StateReset {

        @Test @DisplayName("after a complete sequence, parser returns to GROUND")
        void afterSequence_returnsToGround() {
            // Process an arrow key sequence
            feed((byte)0x1B, (byte)'[', (byte)'A');
            // Then process a plain character — should work fine
            KeyEvent evt = feed((byte) 'x');
            assertThat(evt).isNotNull();
            assertThat(evt.key()).isEqualTo("x");
        }

        @Test @DisplayName("reset() clears state mid-sequence")
        void reset_clearsMidSequence() {
            // Start an escape sequence but don't finish it
            parser.process((byte) 0x1B);
            parser.process((byte) '[');
            // Reset
            parser.reset();
            // Plain char should now work
            KeyEvent evt = parser.process((byte) 'z');
            assertThat(evt).isNotNull();
            assertThat(evt.key()).isEqualTo("z");
        }
    }
}