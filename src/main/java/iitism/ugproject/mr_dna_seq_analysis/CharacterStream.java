package iitism.ugproject.mr_dna_seq_analysis;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class CharacterStream implements AutoCloseable {
    private final InputStream is;

    // for creating mock classes
    protected CharacterStream() {
        this.is = null;
    }

    public CharacterStream(String path, long start) throws IOException {
        if (start < 0) throw new IllegalArgumentException("start cannot be negative");

        is = new FileInputStream(path);

        long skipsReqd = start;
        while (skipsReqd > 0) {
            long skipped = is.skip(skipsReqd);

            if (skipped == 0) {
                // try reading 1 byte to avoid infinite loop
                if (is.read() == -1) break; // EOF
                skipped = 1;
            }

            skipsReqd -= skipped;
        }
    }

    public char next() throws IOException {
        if (is == null)
            throw new IOException("Mock subclass must override next(); InputStream is null.");

        int val = is.read();
        if (val == -1) return '#';
        return (char) val;
    }

    public void close() throws IOException {
        if (is != null) is.close();
    }
}
