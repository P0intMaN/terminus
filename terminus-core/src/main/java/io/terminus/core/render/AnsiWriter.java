package io.terminus.core.render;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Writes ANSI escape sequences to the terminal in a single atomic flush.
 *
 * WHY A DEDICATED CLASS FOR THIS?
 * Three reasons:
 *
 *   1. ATOMICITY: Multiple small writes to stdout cause flicker because
 *      the OS may flush partial output between writes. Batching everything
 *      into one byte array and writing it in one system call is atomic
 *      from the terminal's perspective.
 *
 *   2. ENCODING: Terminals expect UTF-8. Java's System.out uses the
 *      platform default encoding (which may not be UTF-8 on Windows).
 *      We always encode explicitly as UTF-8.
 *
 *   3. TESTABILITY: By accepting an OutputStream in the constructor,
 *      tests can inject a ByteArrayOutputStream and inspect exactly
 *      what bytes were written — without touching a real terminal.
 *      This is the Dependency Injection pattern applied to I/O.
 *
 * THREAD SAFETY: Not thread-safe. Call only from the UI thread.
 */
public class AnsiWriter {

    private final OutputStream out;

    /**
     * Create an AnsiWriter that writes to the given output stream.
     *
     * For production: pass System.out directly (raw stream, not PrintStream).
     * For tests: pass new ByteArrayOutputStream().
     */
    public AnsiWriter(OutputStream out) {
        this.out = out;
    }

    /** Convenience constructor for production use — writes to stdout. */
    public static AnsiWriter toStdout() {
        // Use the raw underlying stream from System.out, not System.out itself.
        // System.out is a PrintStream that adds its own buffering and
        // platform-default encoding. We want raw bytes with explicit UTF-8.
        return new AnsiWriter(System.out);
    }

    /**
     * Write an ANSI string to the terminal.
     *
     * Encodes as UTF-8 and flushes in a single write.
     * If the string is empty, does nothing — no syscall overhead.
     *
     * @param ansiString the ANSI escape sequence string from ScreenDiffer
     * @throws RuntimeException wrapping IOException if the write fails
     */
    public void write(String ansiString) {
        if (ansiString == null || ansiString.isEmpty()) return;

        try {
            byte[] bytes = ansiString.getBytes(StandardCharsets.UTF_8);
            out.write(bytes);    // single write — atomic from terminal's view
            out.flush();         // ensure bytes leave the Java buffer immediately
        } catch (IOException e) {
            // In a TUI framework, an IOException writing to stdout is fatal —
            // the terminal connection is broken (e.g. SSH session closed).
            // We wrap and rethrow as unchecked so callers don't need
            // try/catch on every write() call.
            throw new TerminalWriteException("Failed to write to terminal", e);
        }
    }

    /**
     * Perform a full screen clear — used on startup and after resize.
     */
    public void clearScreen() {
        write(new ScreenDiffer().fullClear());
    }

    /**
     * Reset all SGR attributes and show the cursor.
     * Called on shutdown to leave the terminal in a clean state.
     */
    public void reset() {
        write(Ansi.RESET + Ansi.SHOW_CURSOR);
    }

    /**
     * Unchecked exception for terminal write failures.
     *
     * WHY UNCHECKED?
     * A checked IOException would force every caller to handle it,
     * cluttering the EventLoop's render path with try/catch blocks
     * that can't meaningfully recover anyway. If stdout is broken,
     * the application should crash cleanly, not try to recover.
     */
    public static class TerminalWriteException extends RuntimeException {
        public TerminalWriteException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}