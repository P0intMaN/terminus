package io.terminus.core.terminal;

import io.terminus.core.event.KeyEvent;

/**
 * Parses raw bytes from stdin into KeyEvent objects.
 *
 * PATTERN: State Machine
 * The parser moves between states as bytes arrive:
 *   GROUND   → waiting for first byte
 *   ESC_SEQ  → received ESC (0x1B), waiting for next byte
 *   CSI      → received ESC[, reading parameter bytes
 *   SS3      → received ESC O, reading F-key byte
 *
 * WHY A STATE MACHINE AND NOT JUST READING KNOWN BYTE SEQUENCES?
 * Because escape sequences are variable length. ESC[A is 3 bytes
 * (arrow up). ESC[1;5A is 6 bytes (Ctrl+arrow up). ESC[200~ is 7
 * bytes (bracketed paste start). You can't know the length until
 * you read the terminating byte. A state machine handles this
 * naturally — it accumulates bytes and decides when a sequence
 * is complete.
 *
 * THREAD SAFETY: Not thread-safe. Called only from the stdin
 * reader thread inside EventLoop.
 */
public class KeyParser {

    /** Parser states. */
    private enum State { GROUND, ESC_SEQ, CSI, SS3 }

    private State state = State.GROUND;

    /**
     * Buffer for accumulating escape sequence parameter bytes.
     * e.g. for ESC[1;5A, we accumulate "1;5" before seeing 'A'.
     * Max 16 bytes — no real escape sequence is longer than this.
     */
    private final byte[] paramBuf = new byte[16];
    private int paramLen = 0;

    /**
     * Process one byte from stdin and return a KeyEvent if a complete
     * key has been recognized, or null if more bytes are needed.
     *
     * The EventLoop calls this in a tight loop, passing each byte
     * from the stdin read buffer one at a time.
     *
     * @param b the next byte from stdin
     * @return a KeyEvent, or null if the sequence is incomplete
     */
    public KeyEvent process(byte b) {
        return switch (state) {
            case GROUND   -> processGround(b);
            case ESC_SEQ  -> processEscSeq(b);
            case CSI      -> processCsi(b);
            case SS3      -> processSs3(b);
        };
    }

    // ── State handlers ────────────────────────────────────────────────────

    private KeyEvent processGround(byte b) {
        int c = b & 0xFF; // treat as unsigned

        if (c == 0x1B) {
            // ESC byte — start of an escape sequence
            state = State.ESC_SEQ;
            return null; // need more bytes
        }

        if (c == 0x7F) {
            // DEL byte — sent by Backspace on most terminals
            return key("BACKSPACE");
        }

        if (c == 0x0D) {
            // Carriage return — sent by Enter key in raw mode
            return key("ENTER");
        }

        if (c == 0x09) {
            return key("TAB");
        }

        if (c < 0x20) {
            // Control characters: 0x01='A', 0x02='B', ... 0x1A='Z'
            // Ctrl+A sends 0x01, Ctrl+B sends 0x02, etc.
            char ctrlChar = (char)('A' + c - 1);
            return new KeyEvent(
                System.nanoTime(),
                String.valueOf(ctrlChar),
                true, false, false // ctrl=true
            );
        }

        // Printable ASCII or UTF-8 continuation byte
        // For full Unicode support, we'd accumulate multi-byte sequences here.
        // For now, handle ASCII + common Latin characters (sufficient for most TUIs).
        if (c >= 0x20 && c < 0x7F) {
            return new KeyEvent(
                System.nanoTime(),
                String.valueOf((char) c),
                false, false, false
            );
        }

        // Ignore anything else (UTF-8 continuation bytes for now)
        return null;
    }

    private KeyEvent processEscSeq(byte b) {
        int c = b & 0xFF;

        if (c == '[') {
            // ESC[ — Control Sequence Introducer
            state = State.CSI;
            paramLen = 0;
            return null;
        }

        if (c == 'O') {
            // ESC O — SS3 prefix for F1-F4 on many terminals
            state = State.SS3;
            return null;
        }

        if (c == 0x1B) {
            // Two ESCs in a row — treat first as standalone ESC key
            // and start a new escape sequence with the second
            state = State.ESC_SEQ;
            return key("ESC");
        }

        // ESC followed by a letter — Alt+key (Meta key)
        state = State.GROUND;
        if (c >= 0x20 && c < 0x7F) {
            return new KeyEvent(
                System.nanoTime(),
                String.valueOf((char) c),
                false, true, false // alt=true
            );
        }

        // Unknown ESC sequence — emit standalone ESC and reprocess byte
        return key("ESC");
    }

    private KeyEvent processCsi(byte b) {
        int c = b & 0xFF;

        // Parameter bytes: 0x30-0x3F (digits, semicolons, etc.)
        if (c >= 0x30 && c <= 0x3F) {
            if (paramLen < paramBuf.length) {
                paramBuf[paramLen++] = b;
            }
            return null; // still accumulating
        }

        // Intermediate bytes: 0x20-0x2F (rare, skip for now)
        if (c >= 0x20 && c <= 0x2F) {
            return null;
        }

        // Final byte: 0x40-0x7E — this completes the sequence
        state = State.GROUND;
        String params = new String(paramBuf, 0, paramLen);

        return switch ((char) c) {
            case 'A' -> keyWithMods("UP",    params);
            case 'B' -> keyWithMods("DOWN",  params);
            case 'C' -> keyWithMods("RIGHT", params);
            case 'D' -> keyWithMods("LEFT",  params);
            case 'H' -> keyWithMods("HOME",  params);
            case 'F' -> keyWithMods("END",   params);
            case 'Z' -> new KeyEvent(System.nanoTime(), "TAB", false, false, true); // Shift+Tab
            case '~' -> parseTildeSequence(params);
            case 'M', 'm' -> parseMouse(params, (char) c); // SGR mouse
            default  -> null; // unknown CSI sequence
        };
    }

    private KeyEvent processSs3(byte b) {
        // SS3 sequences: ESC O followed by one letter
        state = State.GROUND;
        return switch ((char)(b & 0xFF)) {
            case 'P' -> key("F1");
            case 'Q' -> key("F2");
            case 'R' -> key("F3");
            case 'S' -> key("F4");
            case 'H' -> key("HOME");
            case 'F' -> key("END");
            default  -> null;
        };
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Create a simple KeyEvent with no modifiers. */
    private KeyEvent key(String name) {
        return new KeyEvent(System.nanoTime(), name, false, false, false);
    }

    /**
     * Parse modifier keys from CSI parameter string.
     *
     * Many terminals encode modifiers in the parameter byte:
     *   ESC[A      = Up (no modifier)
     *   ESC[1;2A   = Shift+Up  (modifier code 2)
     *   ESC[1;3A   = Alt+Up    (modifier code 3)
     *   ESC[1;5A   = Ctrl+Up   (modifier code 5)
     *
     * Modifier code = sum of: Shift=1, Alt=2, Ctrl=4  (then +1)
     */
    private KeyEvent keyWithMods(String name, String params) {
        int modCode = 0;
        if (params.contains(";")) {
            try {
                modCode = Integer.parseInt(params.split(";")[1]) - 1;
            } catch (NumberFormatException ignored) {}
        }
        boolean shift = (modCode & 1) != 0;
        boolean alt   = (modCode & 2) != 0;
        boolean ctrl  = (modCode & 4) != 0;
        return new KeyEvent(System.nanoTime(), name, ctrl, alt, shift);
    }

    /**
     * Parse tilde-terminated sequences: ESC[{code}~
     *
     * Common codes:
     *   2~ = Insert    3~ = Delete
     *   5~ = PageUp    6~ = PageDown
     *   15~ = F5    17~ = F6    18~ = F7    19~ = F8
     *   20~ = F9    21~ = F10   23~ = F11   24~ = F12
     */
    private KeyEvent parseTildeSequence(String params) {
        // params may be "5" or "5;2" (with modifier)
        String[] parts = params.split(";");
        int code = 0;
        int modCode = 0;
        try {
            code = Integer.parseInt(parts[0]);
            if (parts.length > 1) modCode = Integer.parseInt(parts[1]) - 1;
        } catch (NumberFormatException ignored) {}

        boolean shift = (modCode & 1) != 0;
        boolean alt   = (modCode & 2) != 0;
        boolean ctrl  = (modCode & 4) != 0;

        String name = switch (code) {
            case 2  -> "INSERT";
            case 3  -> "DELETE";
            case 5  -> "PAGE_UP";
            case 6  -> "PAGE_DOWN";
            case 15 -> "F5";
            case 17 -> "F6";
            case 18 -> "F7";
            case 19 -> "F8";
            case 20 -> "F9";
            case 21 -> "F10";
            case 23 -> "F11";
            case 24 -> "F12";
            default -> "UNKNOWN_" + code;
        };
        return new KeyEvent(System.nanoTime(), name, ctrl, alt, shift);
    }

    /**
     * Parse SGR mouse sequences: ESC[<{params}M (press) or m (release)
     * Format: ESC[<{button};{col};{row}M
     *
     * We return null for now — full mouse event parsing
     * is wired into MouseEvent in a future step.
     */
    private KeyEvent parseMouse(String params, char finalByte) {
        // Mouse events are handled separately by the EventLoop.
        // The EventLoop detects the ESC[< prefix and routes to
        // MouseParser instead. Return null here.
        return null;
    }

    /** Reset the parser to GROUND state. */
    public void reset() {
        state = State.GROUND;
        paramLen = 0;
    }
}