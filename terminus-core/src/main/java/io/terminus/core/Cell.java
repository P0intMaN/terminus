package io.terminus.core;


/**
 * The atomic unit of terminal rendering.
 *
 * A Cell represents one character position on the terminal screen.
 * The entire screen is modelled as a 2D array of Cells — Cell[][].
 *
 * WHY A RECORD?
 * Cell is a pure value object — two Cells are equal if their contents
 * are equal, regardless of identity. Java 21 records give us:
 *   - Immutability by default (all fields are final)
 *   - equals() + hashCode() + toString() generated correctly
 *   - Compact syntax that communicates intent
 *
 * WHY IMMUTABLE?
 * The ScreenDiffer compares the previous frame's Cell[][] against the
 * new frame's Cell[][]. If Cells were mutable, a component could change
 * a Cell after it was written to the buffer, making the diff unreliable.
 * Immutability eliminates this entire class of bug.
 *
 * DESIGN DECISION — int for colors, not an enum:
 * We pack 24-bit RGB into a single int: 0xRRGGBB.
 * An enum would limit us to a fixed palette. True-color terminals
 * support 16 million colors — we want to support all of them.
 * Special sentinel: -1 means "use terminal default color".
 *
 * DESIGN DECISION — byte for attrs, not boolean fields:
 * Style attributes (bold, dim, italic, underline, strikethrough) are
 * represented as bit flags in a single byte. This lets us:
 *   1. Store 8 attributes in 1 byte instead of 8 booleans (8 bytes)
 *   2. Combine attributes with bitwise OR: BOLD | ITALIC
 *   3. Check attributes with bitwise AND: (attrs & BOLD) != 0
 * This is the same approach used by ncurses and every serious TUI library.
 */


public record Cell(
    int glyph,    // Unicode codepoint (not char — char can't hold emoji)
    int fg,       // foreground color: 0xRRGGBB, or -1 for terminal default
    int bg,       // background color: 0xRRGGBB, or -1 for terminal default
    byte attrs,   // style bit flags — see constants below
    byte width    // 1 for normal chars, 2 for wide chars (CJK, some emoji)
) {


    // ── Attribute bit flags ──────────────────────────────────────────────
    // WHY CONSTANTS ON THE RECORD ITSELF?
    // They belong here because they only have meaning in the context of
    // a Cell's attrs field. Keeping them co-located with the type makes
    // the code self-documenting and prevents scattering magic numbers.

    public static final byte ATTR_NONE          = 0;        // 0000_0000
    public static final byte ATTR_BOLD          = 1;        // 0000_0001
    public static final byte ATTR_DIM           = 1 << 1;   // 0000_0010
    public static final byte ATTR_ITALIC        = 1 << 2;   // 0000_0100
    public static final byte ATTR_UNDERLINE     = 1 << 3;   // 0000_1000
    public static final byte ATTR_STRIKETHROUGH = 1 << 4;   // 0001_0000
    public static final byte ATTR_BLINK         = 1 << 5;   // 0010_0000

    // ── Sentinel color value ─────────────────────────────────────────────
    public static final int DEFAULT_COLOR = -1;

    // ── Compact constructor — validation ─────────────────────────────────
    // WHY A COMPACT CONSTRUCTOR?
    // Records allow a "compact constructor" that runs before the fields
    // are assigned. This is where we validate invariants. If a Cell is
    // ever constructed with invalid data, we fail loudly at construction
    // time — not silently at render time 10 frames later.
    public Cell {
        if (width != 1 && width != 2) {
            throw new IllegalArgumentException(
                "Cell width must be 1 or 2, got: " + width
            );
        }
        if (fg != DEFAULT_COLOR && (fg < 0 || fg > 0xFFFFFF)) {
            throw new IllegalArgumentException(
                "fg color must be 0xRRGGBB or DEFAULT_COLOR (-1), got: " + fg
            );
        }
        if (bg != DEFAULT_COLOR && (bg < 0 || bg > 0xFFFFFF)) {
            throw new IllegalArgumentException(
                "bg color must be 0xRRGGBB or DEFAULT_COLOR (-1), got: " + bg
            );
        }
    }


    // ── Factory methods ───────────────────────────────────────────────────
    // WHY FACTORY METHODS INSTEAD OF CALLING THE CONSTRUCTOR DIRECTLY?
    // The canonical constructor Cell(glyph, fg, bg, attrs, width) has
    // 5 parameters — easy to mix up. Factory methods are self-documenting:
    //   Cell.of('A') is clearer than new Cell(65, -1, -1, (byte)0, (byte)1)
    // They also give us a place to add caching later (e.g. BLANK is always
    // the same object — no need to allocate millions of identical Cells).

    /** A plain character with terminal default colors and no styling. */
    public static Cell of(char c) {
        return new Cell(c, DEFAULT_COLOR, DEFAULT_COLOR, ATTR_NONE, (byte) 1);
    }

    /** A character with explicit foreground color (0xRRGGBB). */
    public static Cell of(char c, int fg) {
        return new Cell(c, fg, DEFAULT_COLOR, ATTR_NONE, (byte) 1);
    }

    /** A character with explicit foreground + background + attributes. */
    public static Cell of(char c, int fg, int bg, byte attrs) {
        return new Cell(c, fg, bg, attrs, (byte) 1);
    }

    /** A wide character (CJK, some emoji) that occupies 2 terminal columns. */
    public static Cell wide(int codepoint, int fg, int bg, byte attrs) {
        return new Cell(codepoint, fg, bg, attrs, (byte) 2);
    }

    /** An empty cell — space character, terminal default colors. */
    public static final Cell BLANK = new Cell(' ', DEFAULT_COLOR, DEFAULT_COLOR, ATTR_NONE, (byte) 1);


    // ── Attribute helpers ─────────────────────────────────────────────────
    // WHY HELPER METHODS ON THE RECORD?
    // Bitwise operations on byte flags are not readable inline.
    //   if ((cell.attrs() & Cell.ATTR_BOLD) != 0)  — hard to read
    //   if (cell.isBold())                          — reads like English
    // These are pure derived values — no state mutation — which is safe
    // on an immutable record.
    public boolean isBold()          { return (attrs & ATTR_BOLD) != 0; }
    public boolean isDim()           { return (attrs & ATTR_DIM) != 0; }
    public boolean isItalic()        { return (attrs & ATTR_ITALIC) != 0; }
    public boolean isUnderline()     { return (attrs & ATTR_UNDERLINE) != 0; }
    public boolean isStrikethrough() { return (attrs & ATTR_STRIKETHROUGH) != 0; }
    public boolean isBlink()         { return (attrs & ATTR_BLINK) != 0; }

    /** Returns a new Cell with the given attribute added. Does not mutate. */
    public Cell withAttr(byte attr) {
        return new Cell(glyph, fg, bg, (byte)(attrs | attr), width);
    }

    /** Returns a new Cell with the given foreground color. Does not mutate. */
    public Cell withFg(int newFg) {
        return new Cell(glyph, newFg, bg, attrs, width);
    }

    /** Returns a new Cell with the given background color. Does not mutate. */
    public Cell withBg(int newBg) {
        return new Cell(glyph, fg, newBg, attrs, width);
    }
}
