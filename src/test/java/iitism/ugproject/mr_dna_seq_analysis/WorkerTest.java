package iitism.ugproject.mr_dna_seq_analysis;

import org.junit.jupiter.api.Test;

import java.nio.file.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Spark Worker.
 */
public class WorkerTest {

    private static List<String> runWorker(String seq,
                                         String pat,
                                         int k,
                                         int w,
                                         int id) throws Exception {

        Path seqFile = Files.createTempFile("seq", ".txt");
        Path patFile = Files.createTempFile("pat", ".txt");
        Path outFile = Files.createTempFile("out", ".txt");

        Files.writeString(seqFile, seq);
        Files.writeString(patFile, pat);

        String[] args = {
                String.valueOf(id),
                String.valueOf(w),
                seqFile.toString(),
                patFile.toString(),
                outFile.toString(),
                String.valueOf(k),
                String.valueOf(seq.length())
        };

        Worker.main(args);

        return Files.readAllLines(outFile);
    }

    // -------------------------
    // BASIC CORRECTNESS
    // -------------------------

    @Test
    public void testExactMatches() throws Exception {
        String seq = "ACGTACGT";
        String pat = "ACGT";

        List<String> out = runWorker(seq, pat, 0, 1, 0);

        System.out.println("Exact Matches Output:");
        System.out.println(out);

        assertEquals(2, out.size());
        assertTrue(out.contains("0,3"));
        assertTrue(out.contains("4,7"));
    }

    @Test
    public void testSlidingViaGetNext() throws Exception {
        String seq = "AAAAA";
        String pat = "AAA";

        List<String> out = runWorker(seq, pat, 0, 1, 0);

        // AAA occurs at positions 0,1,2
        assertEquals(3, out.size());
        assertTrue(out.contains("0,2"));
        assertTrue(out.contains("1,3"));
        assertTrue(out.contains("2,4"));
    }

    // -------------------------
    // APPROXIMATE MATCHES
    // -------------------------

    @Test
    public void testApproximateMatching() throws Exception {
        String seq = "ACGTACGT";
        String pat = "ACGG";   // differs by 1

        List<String> out = runWorker(seq, pat, 1, 1, 0);

        System.out.println("Approximate Matches Output:");
        System.out.println(out);

        // ACGT and ACGT both differ by 1 from ACGG
        assertEquals(4, out.size());
    }

    // -------------------------
    // NO DUPLICATES
    // -------------------------

    @Test
    public void testNoDuplicates() throws Exception {
        String seq = "AAAAAA";
        String pat = "AAA";

        List<String> out = runWorker(seq, pat, 0, 1, 0);

        Set<String> unique = new HashSet<>(out);
        assertEquals(out.size(), unique.size());
    }

    // -------------------------
    // MULTI-WORKER PARTITIONING
    // -------------------------

    @Test
    public void testTwoWorkersSplit() throws Exception {
        String seq = "ACGTACGT";
        String pat = "ACGT";

        List<String> out0 = runWorker(seq, pat, 0, 2, 0);
        List<String> out1 = runWorker(seq, pat, 0, 2, 1);

        List<String> all = new ArrayList<>();
        all.addAll(out0);
        all.addAll(out1);

        System.out.println("All Workers Output:");
        System.out.println(all);

        assertEquals(2, all.size());
        assertTrue(all.contains("0,3"));
        assertTrue(all.contains("4,7"));

    }

    // -------------------------
    // BOUNDARY OVERLAP HANDLING
    // -------------------------

    @Test
    public void testOverlapAcrossWorkerBoundary() throws Exception {
        String seq = "TTTACGTAAA";
        String pat = "ACGT";

        // worker0 handles [0..4], worker1 handles [5..9]
        List<String> out0 = runWorker(seq, pat, 0, 2, 0);
        List<String> out1 = runWorker(seq, pat, 0, 2, 1);

        // Match starts at index 3 (in worker0 range), but needs chars beyond 4
        assertTrue(out0.contains("3,6") || out1.contains("3,6"));
    }
}
