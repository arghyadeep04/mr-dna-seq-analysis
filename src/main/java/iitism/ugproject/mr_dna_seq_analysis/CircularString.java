package iitism.ugproject.mr_dna_seq_analysis;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.*;

public class CircularString implements Closeable {

    // Consider providing an ExecutorService from outside in production. For tests this is fine,
    // but ensure you call close() to shutdown the pool.
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final char[] sub_sequence;
    private int offset;
    private final int t;
    private final CharacterStream sequence;
    // next_char must be guarded by synchronization or made volatile + CAS. We use synchronized methods.
    private CompletableFuture<Character> next_char;
    private int length;

    private CompletableFuture<Character> getNextAsCompletableFuture() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return sequence.next();
            } catch (IOException e) {
                // wrap in CompletionException so it arrives to future as cause
                throw new CompletionException(e);
            }
        }, executor);
    }

    public CircularString(CharacterStream sequence, int patternLength, int threshold) throws IOException {
        this.t = patternLength + threshold;
        if (t <= 0) {
            throw new IllegalArgumentException("patternLength + threshold must be > 0");
        }
        this.sub_sequence = new char[t];
        this.offset = 0;
        this.sequence = sequence;
        this.length = 0;

        // fill initial window
        for (int i = 0; i < t; i++) {
            char c = sequence.next(); // constructor declares throws IOException
            if (c == '#') {
                break;
            }
            sub_sequence[i] = c;
            length++;
        }
        // prefetch only if stream not exhausted — but fetching is safe either way
        this.next_char = getNextAsCompletableFuture();
    }

    /**
     * Returns the character at logical index indx in [0, length-1].
     *
     * @param indx 0-based index into current logical circular buffer (0 .. length-1)
     * @return character at that logical position
     * @throws IndexOutOfBoundsException if indx is negative or >= length
     */
    public synchronized char charAt(int indx) {
        if (indx < 0 || indx >= length) {
            throw new IndexOutOfBoundsException("index: " + indx + ", length: " + length);
        }
        // map logical index to actual index in array using offset
        int actualIndex = Math.floorMod(indx + offset, t);
        return sub_sequence[actualIndex];
    }

    /**
     * Attempts to advance the circular buffer by one character from the underlying sequence.
     *
     * @return true if the stream still has data after this advance; false if we've reached end (length == 0)
     * @throws IOException when underlying sequence throws IOException while reading next char
     */
    public synchronized boolean getNext() throws IOException {
        try {
            // blocking wait for the prefetched char
            char c = next_char.get();

            if (c == '#') {
                // remove one element logically by advancing offset and reducing length
                offset = (offset + 1) % t;
                length--;
                if (length <= 0) {
                    length = 0;
                    return false;
                }
                return true;
            } else {
                // overwrite the slot at offset, then advance offset
                sub_sequence[offset] = c;
                offset = (offset + 1) % t;
                // prefetch next char for the following call
                next_char = getNextAsCompletableFuture();
                return true;
            }
        } catch (InterruptedException ie) {
            // restore interrupt status and throw IOException (caller expects IO)
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while reading next char", ie);
        } catch (ExecutionException ee) {
            // unwrap cause
            Throwable cause = ee.getCause();
            if (cause instanceof CompletionException && cause.getCause() != null) {
                cause = cause.getCause(); // unwrap nested CompletionException
            }
            if (cause instanceof IOException) {
                throw (IOException) cause;
            } else if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;
            } else {
                throw new IOException("Failed to read next char", cause);
            }
        }
    }

    /**
     * Close the shared executor (tests should call this when done) — note: if you have multiple instances,
     * you might prefer to pass a shared executor in rather than using a static pool.
     */
    @Override
    public void close() {
        executor.shutdown();
    }

    // optional helpers
    public synchronized int getLength() {
        return length;
    }

    public int getT() {
        return t;
    }
}
