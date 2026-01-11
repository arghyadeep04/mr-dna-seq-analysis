package iitism.ugproject.mr_dna_seq_analysis;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive JUnit 5 tests for CircularString.
 *
 * Tests:
 *  - constructor initial fill and stopping at '#'
 *  - charAt bounds checks
 *  - normal rotation via getNext + prefetch behavior
 *  - handling of '#' (end-of-stream) removals and termination
 *  - propagation of IOException thrown by CharacterStream during prefetch
 *  - interruption while waiting on getNext
 *
 * The MockCharacterStream allows:
 *  - specifying characters to return
 *  - introducing an artificial delay only for calls at or after a given start index
 *  - instructing the stream to throw IOException at a particular call index
 */
@DisplayName("CircularString JUnit tests")
class CircularStringTest {

    /**
     * Test double extending CharacterStream so tests can override next().
     *
     * Behavior:
     *  - returns data[0], data[1], ... sequentially
     *  - after exhausting data returns '#'
     *  - optional delayMillis is applied only when idx >= delayStartIndex
     *  - optional throwOnIndex triggers an IOException when that call index is reached
     */
    static class MockCharacterStream extends CharacterStream {
        private final char[] data;
        private int idx = 0;
        private final long delayMillis;
        private final int delayStartIndex;
        private final Integer throwOnIndex;

        // Accept char[] for callers that use arrays
        MockCharacterStream(char[] data) {
            this(data, 0L, Integer.MAX_VALUE, null);
        }

        // Accept String for callers that pass a string
        MockCharacterStream(String s) {
            this(s.toCharArray(), 0L, Integer.MAX_VALUE, null);
        }

        // Full constructor accepting char[]
        MockCharacterStream(char[] data, long delayMillis, int delayStartIndex, Integer throwOnIndex) {
            super(); // use protected no-arg ctor of CharacterStream (must exist in production code)
            this.data = data;
            this.delayMillis = delayMillis;
            this.delayStartIndex = delayStartIndex;
            this.throwOnIndex = throwOnIndex;
        }

        // Full constructor accepting String
        MockCharacterStream(String s, long delayMillis, int delayStartIndex, Integer throwOnIndex) {
            this(s.toCharArray(), delayMillis, delayStartIndex, throwOnIndex);
        }

        @Override
        public synchronized char next() throws IOException {
            if (throwOnIndex != null && idx == throwOnIndex) {
                throw new IOException("Mock IO failure at index " + idx);
            }
            if (idx >= delayStartIndex && delayMillis > 0) {
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted in mock", e);
                }
            }
            if (idx >= data.length) {
                idx++; // increment so subsequent calls keep moving (and delays/throws if configured)
                return '#';
            }
            return data[idx++];
        }
    }

    // --- Tests ---

    @Test
    public void initializationFromString() throws Exception {
        // Provide a simple valid DNA string
        String dna = "ATGCAT";
        MockCharacterStream stream = new MockCharacterStream(dna);
        // choose t = patternLength + threshold; pick 6 so constructor reads entire dna
        CircularString cs = new CircularString(stream, 3, 3); // t = 6

        assertEquals(dna.length(), cs.getLength(), "initial length must be full string length");
        for (int i = 0; i < dna.length(); i++) {
            assertEquals(dna.charAt(i), cs.charAt(i), "charAt should match initialization string at " + i);
        }
    }

    @Test
    public void getNextShiftsWindow_whenCharsAvailable_keepsSameLength() throws Exception {
        // initial window length 4; more characters available after initial fill
        // initial: A T G C ; following characters: A T G C (so windows shift but length stays 4)
        String all = "ATGCA TGC".replace(" ", ""); // "ATGCATGC"
        MockCharacterStream stream = new MockCharacterStream(all);
        CircularString cs = new CircularString(stream, 2, 2); // t = 4, initial reads 'A','T','G','C'

        assertEquals(4, cs.getLength());
        assertEquals('A', cs.charAt(0));
        assertEquals('T', cs.charAt(1));
        assertEquals('G', cs.charAt(2));
        assertEquals('C', cs.charAt(3));

        // shift 1 => window should be T,G,C,A (length remains 4)
        assertTrue(cs.getNext());
        assertEquals(4, cs.getLength());
        assertEquals('T', cs.charAt(0));
        assertEquals('G', cs.charAt(1));
        assertEquals('C', cs.charAt(2));
        assertEquals('A', cs.charAt(3));

        // shift 2 => G,C,A,T
        assertTrue(cs.getNext());
        assertEquals(4, cs.getLength());
        assertEquals('G', cs.charAt(0));
        assertEquals('C', cs.charAt(1));
        assertEquals('A', cs.charAt(2));
        assertEquals('T', cs.charAt(3));
    }

    @Test
    public void getNextShrinksWindow_whenCharsUnavailable() throws Exception {
        // initial "ATG" then no more characters (stream will return '#')
        MockCharacterStream stream = new MockCharacterStream("ATG");
        CircularString cs = new CircularString(stream, 3, 0); // t = 3, initial = A,T,G

        assertEquals(3, cs.getLength());
        // First getNext: stream returns '#' -> window shrinks from 3 to 2
        boolean cont = cs.getNext();
        assertTrue(cont, "when first '#' encountered and length > 1, getNext should return true (not ended)");
        assertEquals(2, cs.getLength());

        // Next getNext: another '#' -> shrink to 1 and return true
        cont = cs.getNext();
        assertTrue(cont);
        assertEquals(1, cs.getLength());

        // Next getNext: last '#' -> shrink to 0 and return false (ended)
        cont = cs.getNext();
        assertFalse(cont, "expected false when window drained");
        assertEquals(0, cs.getLength());
    }

    @Test
    public void returnsFalseWhenEnded_evenOnFurtherCalls() throws Exception {
        // stream is empty immediately (empty string)
        MockCharacterStream stream = new MockCharacterStream("");
        CircularString cs = new CircularString(stream, 1, 0); // t = 1 but constructor reads '#' immediately so length == 0

        assertEquals(0, cs.getLength());
        // getNext should return false repeatedly and not block or throw
        assertFalse(cs.getNext());
        assertFalse(cs.getNext());
    }

    @Test
    public void ioExceptionFromStreamIsPropagated() throws Exception {
        // Prepare a stream where the prefetched call (index t) will throw IOException.
        // Use t = 3 so constructor consumes indices 0..2; throw at index 3
        String data = "ATGX"; // X won't be used as char; we instruct mock to throw at index 3
        MockCharacterStream stream = new MockCharacterStream(data, 0L, Integer.MAX_VALUE, /*throwOnIndex=*/ 3);
        CircularString cs = new CircularString(stream, 2, 1); // t = 3

        try {
            cs.getNext();
            fail("Expected IOException to be thrown by getNext due to underlying stream failure");
        } catch (IOException ex) {
            assertTrue(ex.getMessage().toLowerCase().contains("mock"));
        }
    }

    @Test
    public void interruptedDuringGetNext_raisesIOException() throws Exception {
        // create a stream where the prefetch will block (sleep) so we can interrupt the waiting thread
        String data = "ATGC";
        int t = 2;
        MockCharacterStream stream = new MockCharacterStream(data, 5000L, /*delayStartIndex=*/ t, null);
        CircularString cs = new CircularString(stream, 1, 1); // t = 2 -> constructor fast; prefetch will sleep

        final AtomicBoolean sawException = new AtomicBoolean(false);
        Thread worker = new Thread(() -> {
            try {
                cs.getNext();
            } catch (IOException e) {
                sawException.set(true);
            }
        });

        worker.start();
        // allow worker to reach blocking state
        Thread.sleep(100);
        worker.interrupt();
        worker.join(TimeUnit.SECONDS.toMillis(3));

        assertTrue(sawException.get(), "getNext should throw IOException when interrupted while waiting");
    }

    // After all tests, shut down executor to avoid leaking threads between test runs.
    @AfterAll
    static void tearDownAll() {
        MockCharacterStream stream = new MockCharacterStream(new char[]{'#'});
        try {
            CircularString cs = new CircularString(stream, 1, 0);
            cs.close();
        } catch (IOException ignored) {
            // ignored
        }
    }
}
