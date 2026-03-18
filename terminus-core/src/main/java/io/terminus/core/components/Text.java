package io.terminus.core.components;

import io.terminus.core.Bounds;
import io.terminus.core.Cell;
import io.terminus.core.Constraint;
import io.terminus.core.Leaf;

import java.util.ArrayList;
import java.util.List;

/**
 * A multi-line text display component with word wrapping.
 *
 * Supports:
 *   - Automatic word wrapping to component width
 *   - Text alignment (left, center, right)
 *   - Single-line truncation with ellipsis
 *   - ANSI-style inline bold/color via markup (future)
 *   - Minimum and maximum line count
 *
 * PATTERN: Immutable value + fluent builder.
 * Text content is set at construction or via setText().
 * Style is configured via the builder.
 *
 * WHY A SEPARATE TEXT COMPONENT AND NOT JUST writeString()?
 * writeString() is a low-level helper for components that know
 * exactly where to place text. Text is a first-class component
 * that participates in layout — it knows its preferred size,
 * wraps correctly, and can be composed in a Layout tree.
 *
 * Without Text, every container component would have to reimplement
 * word wrapping. With Text, you compose:
 *   Layout.row().add(new Text("label")).addFlex(input).build()
 */
public class Text extends Leaf {

    public enum Alignment { LEFT, CENTER, RIGHT }
    public enum Overflow  { WRAP, TRUNCATE, CLIP }

    // ── Configuration ─────────────────────────────────────────────────────

    private final int       fg;
    private final int       bg;
    private final byte      attrs;
    private final Alignment alignment;
    private final Overflow  overflow;
    private final int       maxLines;  // -1 = unlimited

    // ── Mutable state ─────────────────────────────────────────────────────

    private String content;

    // ── Constructor ───────────────────────────────────────────────────────

    private Text(Builder b) {
        this.content   = b.content;
        this.fg        = b.fg;
        this.bg        = b.bg;
        this.attrs     = b.attrs;
        this.alignment = b.alignment;
        this.overflow  = b.overflow;
        this.maxLines  = b.maxLines;
    }

    // ── Builder ───────────────────────────────────────────────────────────

    public static Builder of(String content) {
        return new Builder(content);
    }

    /** Convenience: plain text with default styling. */
    public static Text plain(String content) {
        return new Builder(content).build();
    }

    /** Convenience: bold text. */
    public static Text bold(String content, int fg) {
        return new Builder(content).fg(fg).bold().build();
    }

    /** Convenience: muted/secondary text. */
    public static Text muted(String content) {
        return new Builder(content).fg(0x888780).build();
    }

    public static class Builder {
        private String    content;
        private int       fg        = Cell.DEFAULT_COLOR;
        private int       bg        = Cell.DEFAULT_COLOR;
        private byte      attrs     = Cell.ATTR_NONE;
        private Alignment alignment = Alignment.LEFT;
        private Overflow  overflow  = Overflow.WRAP;
        private int       maxLines  = -1;

        private Builder(String content) {
            this.content = content == null ? "" : content;
        }

        public Builder fg(int color)           { this.fg = color;        return this; }
        public Builder bg(int color)           { this.bg = color;        return this; }
        public Builder bold()                  {
            this.attrs = (byte)(this.attrs | Cell.ATTR_BOLD);            return this; }
        public Builder italic()                {
            this.attrs = (byte)(this.attrs | Cell.ATTR_ITALIC);          return this; }
        public Builder underline()             {
            this.attrs = (byte)(this.attrs | Cell.ATTR_UNDERLINE);       return this; }
        public Builder align(Alignment a)      { this.alignment = a;     return this; }
        public Builder overflow(Overflow o)    { this.overflow = o;      return this; }
        public Builder maxLines(int n)         { this.maxLines = n;      return this; }
        public Text build()                    { return new Text(this);               }
    }

    // ── Public API ────────────────────────────────────────────────────────

    public void setText(String content) {
        String safe = content == null ? "" : content;
        if (!this.content.equals(safe)) {
            this.content = safe;
            markDirty();
        }
    }

    public String getText() { return content; }

    // ── Leaf contract ─────────────────────────────────────────────────────

    @Override
    public Bounds measure(Constraint c) {
        int availW = c.isWidthUnbounded() ? 80 : c.maxWidth();
        List<String> lines = wrap(content, availW);
        if (maxLines > 0 && lines.size() > maxLines) {
            lines = lines.subList(0, maxLines);
        }
        int h = Math.max(1, lines.size());
        return Bounds.of(availW, h);
    }

    @Override
    public Cell[][] render() {
        Cell[][] grid = blankGrid();
        if (getWidth() == 0) return grid;

        List<String> lines = computeLines(getWidth());

        for (int row = 0; row < Math.min(lines.size(), getHeight()); row++) {
            String line = align(lines.get(row), getWidth());
            writeString(grid, row, 0, line, fg, bg, attrs);
        }

        return grid;
    }

    // ── Line computation ──────────────────────────────────────────────────

    private List<String> computeLines(int width) {
        if (overflow == Overflow.TRUNCATE) {
            String line = content.length() > width
                ? content.substring(0, Math.max(0, width - 1)) + "…"
                : content;
            return List.of(line);
        }
        if (overflow == Overflow.CLIP) {
            return List.of(content.length() > width
                ? content.substring(0, width)
                : content);
        }
        // WRAP (default)
        List<String> wrapped = wrap(content, width);
        if (maxLines > 0 && wrapped.size() > maxLines) {
            List<String> truncated = new ArrayList<>(
                wrapped.subList(0, maxLines));
            // Add ellipsis to last line if content was cut
            String last = truncated.get(maxLines - 1);
            if (last.length() < width) {
                truncated.set(maxLines - 1, last + "…");
            } else {
                truncated.set(maxLines - 1,
                    last.substring(0, width - 1) + "…");
            }
            return truncated;
        }
        return wrapped;
    }

    /**
     * Word-wrap algorithm.
     *
     * Splits content on explicit newlines first, then wraps each
     * paragraph to the given width. Words that are longer than the
     * width are hard-clipped to prevent infinite loops.
     *
     * WHY NOT JUST SPLIT ON SPACES?
     * Splitting on spaces loses the distinction between single spaces
     * (word separators) and multiple spaces (intentional spacing).
     * We use a greedy algorithm that appends words to the current line
     * until the next word would overflow, then starts a new line.
     * This is the same algorithm used by HTML word-wrap, CSS word-wrap,
     * and every word processor.
     */
    static List<String> wrap(String text, int width) {
        if (width <= 0) return List.of();
        List<String> result = new ArrayList<>();

        // Respect explicit newlines — treat each paragraph separately
        String[] paragraphs = text.split("\n", -1);

        for (String paragraph : paragraphs) {
            if (paragraph.isEmpty()) {
                result.add(""); // preserve blank lines
                continue;
            }

            String[] words = paragraph.split(" ", -1);
            StringBuilder current = new StringBuilder();

            for (String word : words) {
                // Hard-clip words longer than the width
                while (word.length() > width) {
                    if (current.length() > 0) {
                        result.add(current.toString());
                        current.setLength(0);
                    }
                    result.add(word.substring(0, width));
                    word = word.substring(width);
                }

                if (current.length() == 0) {
                    // First word on this line
                    current.append(word);
                } else if (current.length() + 1 + word.length() <= width) {
                    // Word fits on current line
                    current.append(' ').append(word);
                } else {
                    // Word doesn't fit — flush current line and start new one
                    result.add(current.toString());
                    current.setLength(0);
                    current.append(word);
                }
            }

            if (current.length() > 0) {
                result.add(current.toString());
            }
        }

        return result.isEmpty() ? List.of("") : result;
    }

    /**
     * Apply text alignment to a line, padding to the given width.
     */
    private String align(String line, int width) {
        if (line.length() >= width) return line.substring(0, width);
        int pad = width - line.length();
        return switch (alignment) {
            case LEFT   -> line + " ".repeat(pad);
            case RIGHT  -> " ".repeat(pad) + line;
            case CENTER -> {
                int lp = pad / 2;
                yield " ".repeat(lp) + line + " ".repeat(pad - lp);
            }
        };
    }
}