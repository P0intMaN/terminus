package io.terminus.core.render;

/**
 * ANSI escape sequence constants and builders.
 *
 * Every ANSI sequence starts with ESC (0x1B) followed by '['.
 * This two-character prefix is called the Control Sequence Introducer (CSI).
 *
 * WHY A UTILITY CLASS AND NOT AN ENUM?
 * Many sequences are parameterized (row, col, RGB values).
 * Enums can't hold parameterized string builders cleanly.
 * A final class with static methods and constants is the right tool.
 *
 * WHY NOT JUST INLINE THE STRINGS IN ScreenDiffer?
 * Because ANSI codes are magic numbers with non-obvious meanings.
 * "\033[38;2;" means nothing to a reader. "Ansi.FG_RGB_PREFIX" does.
 * Naming things is the most important readability tool we have.
 *
 * REFERENCE: https://en.wikipedia.org/wiki/ANSI_escape_code
 */
public final class Ansi {

    private Ansi() {} // no instances

    /** The ESC character — starts every ANSI control sequence. */
    public static final char ESC = '\033'; // octal, same as 0x1B

    /** Control Sequence Introducer — ESC followed by '['. */
    public static final String CSI = "\033[";

    // ── Cursor control ────────────────────────────────────────────────────

    /** Hide the cursor — prevents flicker during frame composition. */
    public static final String HIDE_CURSOR = CSI + "?25l";

    /** Show the cursor — restore after frame is written. */
    public static final String SHOW_CURSOR = CSI + "?25h";

    /** Move cursor to top-left (home position). */
    public static final String CURSOR_HOME = CSI + "H";

    /** Clear entire screen. Used on first frame only. */
    public static final String CLEAR_SCREEN = CSI + "2J";

    /** Save cursor position. */
    public static final String CURSOR_SAVE    = CSI + "s";

    /** Restore saved cursor position. */
    public static final String CURSOR_RESTORE = CSI + "u";

    // ── SGR (Select Graphic Rendition) — attributes ───────────────────────

    /**
     * Reset ALL attributes to terminal default.
     *
     * WHY ALWAYS RESET BEFORE SETTING?
     * If we emit bold+red for cell A, then just "white" for cell B,
     * the terminal is still in bold mode for cell B. We'd need to
     * track every attribute individually and emit "un-bold" codes.
     * Resetting first is simpler, correct, and the extra bytes are
     * negligible compared to the complexity of differential SGR tracking.
     */
    public static final String RESET = CSI + "0m";

    /** Bold / bright intensity. */
    public static final String BOLD          = CSI + "1m";
    /** Dim / decreased intensity. */
    public static final String DIM           = CSI + "2m";
    /** Italic. */
    public static final String ITALIC        = CSI + "3m";
    /** Underline. */
    public static final String UNDERLINE     = CSI + "4m";
    /** Blink. */
    public static final String BLINK         = CSI + "5m";
    /** Strikethrough. */
    public static final String STRIKETHROUGH = CSI + "9m";

    // ── Color builders ────────────────────────────────────────────────────

    /**
     * Build a 24-bit (truecolor) foreground color sequence.
     *
     * Format: ESC[38;2;R;G;Bm
     * '38' = set foreground, '2' = truecolor mode, then R, G, B components.
     *
     * @param rgb packed 24-bit color: 0xRRGGBB
     */
    public static String fgRgb(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >>  8) & 0xFF;
        int b =  rgb        & 0xFF;
        return CSI + "38;2;" + r + ";" + g + ";" + b + "m";
    }

    /**
     * Build a 24-bit background color sequence.
     *
     * Format: ESC[48;2;R;G;Bm
     * '48' = set background, '2' = truecolor mode, then R, G, B components.
     *
     * @param rgb packed 24-bit color: 0xRRGGBB
     */
    public static String bgRgb(int rgb) {
        int r = (rgb >> 16) & 0xFF;
        int g = (rgb >>  8) & 0xFF;
        int b =  rgb        & 0xFF;
        return CSI + "48;2;" + r + ";" + g + ";" + b + "m";
    }

    /**
     * Build a cursor move sequence.
     *
     * Format: ESC[{row};{col}H
     *
     * CRITICAL: ANSI row and column are 1-indexed.
     * Our Cell[][] is 0-indexed. We add 1 here so callers
     * always work in 0-indexed space and never think about this.
     *
     * @param row 0-indexed row
     * @param col 0-indexed column
     */
    public static String moveTo(int row, int col) {
        return CSI + (row + 1) + ";" + (col + 1) + "H";
    }

    /**
     * Build the complete SGR sequence for a Cell's attributes.
     *
     * Always starts with RESET to clear previous state, then
     * appends each active attribute and color in sequence.
     *
     * WHY RETURN A STRING AND NOT APPEND TO A StringBuilder?
     * This method is called per-changed-cell. Returning a String
     * keeps it pure and testable. The caller (ScreenDiffer) owns
     * the StringBuilder and appends the result.
     *
     * PERFORMANCE NOTE: For a typical frame with ~50 changed cells,
     * this produces ~50 small String objects. The JVM's escape
     * analysis will often stack-allocate these. If profiling shows
     * this as a hotspot, we can change the signature to accept
     * a StringBuilder directly.
     */
    public static String sgrFor(int fg, int bg, byte attrs) {
        StringBuilder sb = new StringBuilder();
        sb.append(RESET); // always reset first

        // Attributes — only emit sequences for active flags
        if ((attrs & io.terminus.core.Cell.ATTR_BOLD)          != 0) sb.append(BOLD);
        if ((attrs & io.terminus.core.Cell.ATTR_DIM)           != 0) sb.append(DIM);
        if ((attrs & io.terminus.core.Cell.ATTR_ITALIC)        != 0) sb.append(ITALIC);
        if ((attrs & io.terminus.core.Cell.ATTR_UNDERLINE)     != 0) sb.append(UNDERLINE);
        if ((attrs & io.terminus.core.Cell.ATTR_BLINK)         != 0) sb.append(BLINK);
        if ((attrs & io.terminus.core.Cell.ATTR_STRIKETHROUGH) != 0) sb.append(STRIKETHROUGH);

        // Colors — only emit if not using terminal default
        if (fg != io.terminus.core.Cell.DEFAULT_COLOR) sb.append(fgRgb(fg));
        if (bg != io.terminus.core.Cell.DEFAULT_COLOR) sb.append(bgRgb(bg));

        return sb.toString();
    }
}