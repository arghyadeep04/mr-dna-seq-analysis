package iitism.ugproject.mr_dna_seq_analysis;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CharacterStreamTest {

    // keep temp files to delete at the end
    private static final List<Path> tmpFiles = new ArrayList<>();

    private static Path createTempFileWith(String content) throws IOException {
        Path p = Files.createTempFile("charstream-test-", ".txt");
        Files.writeString(p, content);
        tmpFiles.add(p);
        return p;
    }

    @Test
    void testStartZero_readsFromBeginningAndReturnsHashOnEof() throws Exception {
        String content = "ATGCCGTTA";
        Path p = createTempFileWith(content);

        CharacterStream cs = new CharacterStream(p.toString(), 0L);
        try {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < content.length(); i++) {
                char c = cs.next();
                sb.append(c);
            }
            // next call should return '#' to indicate EOF
            char eof = cs.next();
            assertEquals('#', eof, "Expected '#' after EOF");

            assertEquals(content, sb.toString(), "All characters read should match file content");
        } finally {
            // ensure we close the underlying stream
            cs.close();
        }
    }

    @Test
    void testNonZeroStart_skipsBytesAndReadsFromOffset() throws Exception {
        // generate a 20-char sequence for clarity
        String content = "0123456789ABCDEFGHIJ"; // length 20
        Path p = createTempFileWith(content);

        long startOffset = 7; // should skip '0'..'6' and begin at '7'
        CharacterStream cs = new CharacterStream(p.toString(), startOffset);
        try {
            // build expected substring from startOffset to end
            String expected = content.substring((int) startOffset);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < expected.length(); i++) {
                sb.append(cs.next());
            }
            // after reading expected chars, next() should return '#'
            assertEquals('#', cs.next());
            assertEquals(expected, sb.toString(), "Stream should begin at byte offset " + startOffset);
        } finally {
            cs.close();
        }
    }

    @Test
    void testClose_thenNext_throwsIOException() throws Exception {
        String content = "HELLO";
        Path p = createTempFileWith(content);

        CharacterStream cs = new CharacterStream(p.toString(), 0L);
        // read one char to ensure stream is active
        char first = cs.next();
        assertEquals('H', first);

        // close the stream
        cs.close();

        // subsequent next() should throw IOException (because underlying stream is closed)
        assertThrows(IOException.class, cs::next);
    }

    @Test
    void testNegativeStart_throwsIllegalArgumentException() throws Exception {
        Path p = createTempFileWith("ABC");
        assertThrows(IllegalArgumentException.class, () -> new CharacterStream(p.toString(), -1L));
    }

    @AfterAll
    static void cleanup() {
        for (Path p : tmpFiles) {
            try {
                Files.deleteIfExists(p);
            } catch (IOException ignored) {}
        }
    }
}
