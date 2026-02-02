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
        this(path, start, new org.apache.hadoop.conf.Configuration());
    }

    public CharacterStream(String path, long start, org.apache.hadoop.conf.Configuration conf) throws IOException {
        if (start < 0) throw new IllegalArgumentException("start cannot be negative");

        org.apache.hadoop.fs.Path hadoopPath = new org.apache.hadoop.fs.Path(path);
        org.apache.hadoop.fs.FileSystem fs = hadoopPath.getFileSystem(conf);
        org.apache.hadoop.fs.FSDataInputStream fileStream = fs.open(hadoopPath);
        
        fileStream.seek(start);
        this.is = fileStream;
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
