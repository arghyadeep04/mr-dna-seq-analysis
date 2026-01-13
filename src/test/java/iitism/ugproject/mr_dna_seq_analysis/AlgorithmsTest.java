package iitism.ugproject.mr_dna_seq_analysis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

class MockCharacterStream extends CharacterStream {

    private final char[] data;
    private int idx = 0;

    public MockCharacterStream(String content) {
        super();
        this.data = content.toCharArray();
    }

    @Override
    public char next() {
        if (idx >= data.length) return '#';
        return data[idx++];
    }
}


public class AlgorithmsTest {

    private MockCharacterStream stream;
    private CircularString circ;

    // reference Naive Levenshtein
    private static int fullEditDistance(String a, String b) {
        int n = a.length(), m = b.length();
        int[][] dp = new int[n + 1][m + 1];

        for (int i = 0; i <= n; i++) dp[i][0] = i;
        for (int j = 0; j <= m; j++) dp[0][j] = j;

        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[n][m];
    }

    private void verifyWithLevenstheinDistance(String sequence, String pattern, int k) throws Exception {

        int m = pattern.length();
        stream = new MockCharacterStream(sequence);
        circ = new CircularString(stream, pattern.length(), k);

        // verify across multiple sliding windows created by getNext()
        do {
            int n = circ.getLength();
            if (n < m - k) break;

            char[] subArr = new char[n];
            for (int i = 0; i < n; i++) subArr[i] = circ.charAt(i);

            String subStr = new String(subArr);

            boolean[] approx = Algorithms.approximateEditDistance(circ, pattern.toCharArray(), k);

            int expectedLen = n - (m - k) + 1;
            assertEquals(expectedLen, approx.length);

            for (int idx = 0; idx < expectedLen; idx++) {
                int end = (m - k - 1) + idx;
                String prefix = subStr.substring(0, Math.min(end + 1,subStr.length()));

                int dist = fullEditDistance(prefix, pattern);
                boolean shouldMatch = (dist <= k);

                assertEquals(
                        shouldMatch,
                        approx[idx],
                        "Mismatch at window prefix=" + prefix + " pat=" + pattern +
                                " dist=" + dist + " k=" + k +
                                " sequence=" + sequence
                );
            }

        } while (circ.getNext());

    }



    // ------------ actual tests ----------------

    @Test
    public void exactMatchNoErrors() throws Exception {
        verifyWithLevenstheinDistance("ATGC", "ATGC", 0);
    }

    @Test
    public void singleMismatchAllowed() throws Exception {
        verifyWithLevenstheinDistance("ATGCA", "ATGC", 1);
    }

    @Test
    public void insertionDeletionCases() throws Exception {
        verifyWithLevenstheinDistance("AAAATTTGGGG", "AATG", 2);
    }

    @Test
    public void repetitiveDNASequenceAndPattern() throws Exception {
        verifyWithLevenstheinDistance("ATATATATATATAT", "ATATA", 1);
    }

    @Test
    public void randomDNASequenceAndPatterns() throws Exception {
        java.util.Random r = new java.util.Random(42);
        String alphabet = "ATGC";

        for (int t = 0; t < 200; t++) {
            int n = 12 + r.nextInt(8);
            int m = 5 + r.nextInt(4);
            int k = r.nextInt(3);

            StringBuilder seq = new StringBuilder();
            StringBuilder pat = new StringBuilder();

            for (int i = 0; i < n; i++) seq.append(alphabet.charAt(r.nextInt(4)));
            for (int i = 0; i < m; i++) pat.append(alphabet.charAt(r.nextInt(4)));

            verifyWithLevenstheinDistance(seq.toString(), pat.toString(), k);
        }
    }
}
