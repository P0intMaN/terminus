package io.terminus.core.terminal;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Structure;

/**
 * Manages raw terminal mode and terminal size queries.
 *
 * All native (JNA) code is contained here. The rest of Terminus
 * never touches JNA directly.
 *
 * NOTE ON CONSTANTS:
 * Termios flag constants (ECHO, ICANON, etc.) are NOT available
 * via JNA's LibC interface — that interface only exposes function
 * signatures. We define the constants ourselves using their
 * standard POSIX/Linux values. These values are stable across
 * all Linux distributions on x86-64.
 */
public final class Terminal {

    private Terminal() {}

    private static final int STDIN_FD = 0;

    private static Termios savedTermios = null;
    private static boolean inRawMode    = false;

    // ── Termios action constants (for tcsetattr) ──────────────────────────
    // TCSAFLUSH: apply after flushing output and discarding pending input
    private static final int TCSAFLUSH = 2;

    // ── c_iflag bits — input mode flags ──────────────────────────────────
    private static final int BRKINT = 0x000002; // signal interrupt on break
    private static final int ICRNL  = 0x000100; // map CR to NL on input
    private static final int INPCK  = 0x000010; // enable input parity check
    private static final int ISTRIP = 0x000020; // strip 8th bit of input chars
    private static final int IXON   = 0x000400; // enable XON/XOFF flow control

    // ── c_oflag bits — output mode flags ─────────────────────────────────
    private static final int OPOST  = 0x000001; // post-process output

    // ── c_cflag bits — control mode flags ────────────────────────────────
    private static final int CS8    = 0x000030; // 8-bit characters

    // ── c_lflag bits — local mode flags ──────────────────────────────────
    private static final int ECHO   = 0x000008; // echo input characters
    private static final int ICANON = 0x000002; // canonical (line) mode
    private static final int IEXTEN = 0x008000; // extended input processing
    private static final int ISIG   = 0x000001; // signal-generating characters

    // ── c_cc array indices — special characters ───────────────────────────
    private static final int VMIN   = 6;  // minimum bytes for read()
    private static final int VTIME  = 5;  // timeout for read() in 0.1s units

    // ── ioctl request code ────────────────────────────────────────────────
    // TIOCGWINSZ: get terminal window size. Linux x86-64 value.
    private static final int TIOCGWINSZ = 0x5413;

    // ── Raw mode ──────────────────────────────────────────────────────────

    public static void enterRawMode() {
        if (inRawMode) return;

        if (!isRealTerminal()) {
            throw new TerminalException(
                "stdin is not a TTY. Terminus requires an interactive terminal.");
        }

        savedTermios = new Termios();
        int result = CLib.INSTANCE.tcgetattr(STDIN_FD, savedTermios);
        if (result != 0) {
            throw new TerminalException("tcgetattr() failed: " + result);
        }

        Termios raw = Termios.copyOf(savedTermios);

        // Input flags: disable flow control, CR translation, parity
        raw.c_iflag &= ~(BRKINT | ICRNL | INPCK | ISTRIP | IXON);

        // Output flags: disable output post-processing
        raw.c_oflag &= ~OPOST;

        // Control flags: set 8-bit characters
        raw.c_cflag |= CS8;

        // Local flags: disable echo, canonical mode, signals, extended input
        raw.c_lflag &= ~(ECHO | ICANON | IEXTEN | ISIG);

        // Read: return after 1 byte, no timeout
        raw.c_cc[VMIN]  = 1;
        raw.c_cc[VTIME] = 0;

        result = CLib.INSTANCE.tcsetattr(STDIN_FD, TCSAFLUSH, raw);
        if (result != 0) {
            throw new TerminalException("tcsetattr() failed: " + result);
        }

        inRawMode = true;

        // Always restore on JVM exit — even on crash
        Runtime.getRuntime().addShutdownHook(
            new Thread(Terminal::exitRawMode, "terminus-terminal-restore")
        );
    }

    public static void exitRawMode() {
        if (!inRawMode || savedTermios == null) return;
        CLib.INSTANCE.tcsetattr(STDIN_FD, TCSAFLUSH, savedTermios);
        inRawMode = false;
    }

    // ── Terminal size ─────────────────────────────────────────────────────

    public static int[] getSize() {
        Winsize ws = new Winsize();
        int result = CLib.INSTANCE.ioctl(STDIN_FD, TIOCGWINSZ, ws);
        if (result != 0) {
            return new int[]{ 80, 24 }; // safe fallback
        }
        int cols = ws.ws_col > 0 ? ws.ws_col : 80;
        int rows = ws.ws_row > 0 ? ws.ws_row : 24;
        return new int[]{ cols, rows };
    }

    public static boolean isRealTerminal() {
        return CLib.INSTANCE.isatty(STDIN_FD) == 1;
    }

    // ── Mouse tracking ────────────────────────────────────────────────────

    public static void enableMouseTracking() {
        System.out.print("\033[?1000h\033[?1006h");
        System.out.flush();
    }

    public static void disableMouseTracking() {
        System.out.print("\033[?1006l\033[?1000l");
        System.out.flush();
    }

    // ── JNA definitions ───────────────────────────────────────────────────

    /**
     * Minimal libc interface — only what Terminal.java needs.
     * We do NOT extend com.sun.jna.platform.unix.LibC because that
     * interface does not expose the termios constants we need, and
     * mixing our Termios struct with its signatures causes type errors.
     */
    interface CLib extends Library {
        CLib INSTANCE = Native.load("c", CLib.class);

        int tcgetattr(int fd, Termios termios);
        int tcsetattr(int fd, int action, Termios termios);
        int ioctl(int fd, int request, Winsize ws);
        int isatty(int fd);
    }

    /**
     * JNA mapping of the POSIX termios struct for Linux x86-64.
     *
     * Field order MUST match the C struct memory layout exactly.
     * Verified against: /usr/include/x86_64-linux-gnu/bits/termios.h
     */
    @Structure.FieldOrder({
        "c_iflag", "c_oflag", "c_cflag", "c_lflag",
        "c_line", "c_cc", "__c_ispeed", "__c_ospeed"
    })
    public static class Termios extends Structure {
        public int    c_iflag;
        public int    c_oflag;
        public int    c_cflag;
        public int    c_lflag;
        public byte   c_line;
        public byte[] c_cc = new byte[32];
        public int    __c_ispeed;
        public int    __c_ospeed;

        public static Termios copyOf(Termios src) {
            Termios copy = new Termios();
            copy.c_iflag    = src.c_iflag;
            copy.c_oflag    = src.c_oflag;
            copy.c_cflag    = src.c_cflag;
            copy.c_lflag    = src.c_lflag;
            copy.c_line     = src.c_line;
            copy.__c_ispeed = src.__c_ispeed;
            copy.__c_ospeed = src.__c_ospeed;
            System.arraycopy(src.c_cc, 0, copy.c_cc, 0, src.c_cc.length);
            return copy;
        }
    }

    /**
     * JNA mapping of the POSIX winsize struct.
     */
    @Structure.FieldOrder({"ws_row", "ws_col", "ws_xpixel", "ws_ypixel"})
    public static class Winsize extends Structure {
        public short ws_row;
        public short ws_col;
        public short ws_xpixel;
        public short ws_ypixel;
    }

    // ── Exception ─────────────────────────────────────────────────────────

    public static class TerminalException extends RuntimeException {
        public TerminalException(String message) { super(message); }
        public TerminalException(String message, Throwable cause) {
            super(message, cause); }
    }
}