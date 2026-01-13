package iitism.ugproject.mr_dna_seq_analysis;

import java.io.*;
import java.nio.file.*;

public class Worker {

    public static void main(String[] args) throws Exception {

        int id = Integer.parseInt(args[0]);
        int w  = Integer.parseInt(args[1]);

        String seqFile = args[2];
        String patFile = args[3];
        String outFile = args[4];

        int k = Integer.parseInt(args[5]);
        long n = Long.parseLong(args[6]);   // sequence length

        // Pattern is small → load into memory
        char[] pattern = Files.readString(Path.of(patFile)).trim().toCharArray();
        int m = pattern.length;

        long chunk = n / w;
        long start = id * chunk;
        long end   = (id == w-1) ? (n-1) : ((id+1)*chunk - 1);

        int readLen = (int)((end - start + 1));

        // Read this worker's sequence block using CircularString
        CircularString sub = new CircularString(new CharacterStream(seqFile, start),m,k);

        for (int i = 0; i < readLen; i++) {
            boolean[] result = Algorithms.approximateEditDistance(sub, pattern, k);
            writeResults(outFile, start+i, result, m, k);
            if (!sub.getNext()) {
                break;
            }
        }
    }

    // write results to output file 
    private static synchronized void writeResults(String outFile,
                                                   long start,
                                                   boolean[] d,
                                                   int m,
                                                   int k) throws Exception {
        try (BufferedWriter bw = new BufferedWriter(new FileWriter(outFile, true))) {
            for (int i = 0; i < d.length; i++) {
                if (d[i]) {
                    long e = start + m-k-1+i;
                    bw.write(start + "," + e);
                    bw.newLine();
                }
            }
        }
    }
}
