package io.terminus.core.render;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.*;

@DisplayName("AnsiWriter")
class AnsiWriterTest {

    @Test
    @DisplayName("write() sends UTF-8 encoded bytes to the output stream")
    void write_sendsUtf8Bytes() throws Exception {
        ByteArrayOutputStream capture = new ByteArrayOutputStream();
        AnsiWriter writer = new AnsiWriter(capture);

        writer.write("Hello \033[0m");

        String written = capture.toString(StandardCharsets.UTF_8);
        assertThat(written).isEqualTo("Hello \033[0m");
    }

    @Test
    @DisplayName("write() with null or empty string writes nothing")
    void write_nullOrEmpty_writesNothing() {
        ByteArrayOutputStream capture = new ByteArrayOutputStream();
        AnsiWriter writer = new AnsiWriter(capture);

        writer.write(null);
        writer.write("");

        assertThat(capture.size()).isEqualTo(0);
    }

    @Test
    @DisplayName("write() handles Unicode characters including wide chars")
    void write_handlesUnicode() throws Exception {
        ByteArrayOutputStream capture = new ByteArrayOutputStream();
        AnsiWriter writer = new AnsiWriter(capture);

        // A CJK character that takes 3 bytes in UTF-8
        writer.write("中");

        byte[] bytes = capture.toByteArray();
        // '中' (U+4E2D) encodes to 3 bytes in UTF-8: E4 B8 AD
        assertThat(bytes).hasSize(3);
        assertThat(bytes[0] & 0xFF).isEqualTo(0xE4);
        assertThat(bytes[1] & 0xFF).isEqualTo(0xB8);
        assertThat(bytes[2] & 0xFF).isEqualTo(0xAD);
    }

    @Test
    @DisplayName("reset() writes RESET + SHOW_CURSOR sequence")
    void reset_writesResetAndShowCursor() throws Exception {
        ByteArrayOutputStream capture = new ByteArrayOutputStream();
        AnsiWriter writer = new AnsiWriter(capture);

        writer.reset();

        String written = capture.toString(StandardCharsets.UTF_8);
        assertThat(written).contains(Ansi.RESET);
        assertThat(written).contains(Ansi.SHOW_CURSOR);
    }
}