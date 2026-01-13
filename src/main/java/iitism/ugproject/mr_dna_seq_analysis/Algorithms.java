package iitism.ugproject.mr_dna_seq_analysis;

/**
 * Fully 0-indexed implementation of ApproximateEditDistance(sub, pat, k).
 *
 * DP definition:
 *   D[i][j] = edit distance between pat[0..i-1] and sub[0..j-1]
 *
 * Banded DP constraint:
 *   Only compute j in [i-k, i+k]
 *
 * CurrIndex(i,j,k) (0-indexed) = j - i + k
 *
 * prev and curr arrays have size = (2k + 1) and store the DP band values.
 */
public class Algorithms {

    public static boolean[] approximateEditDistance(CircularString sub, char[] pat, int k) {

        final int m = pat.length;   // pattern length
        final int n = sub.getLength();   // sub-sequence length

        if(n>m+k) throw new IllegalArgumentException("constraint n<=m+k is violated");;

        // number of valid end positions j = m-k ... n => total = n - (m-k) + 1
        final int len = n - (m - k) + 1;
        if (len <= 0) return new boolean[0];

        final int band = 2 * k + 1;
        final int INF = k + 1; // anything > k is "too large"

        int[] prev = new int[band];
        int[] curr = new int[band];

        // 0-indexed CurrIndex
        java.util.function.BiFunction<Integer, Integer, Integer> CurrIndex =
                (i, j) -> j - i + k;   // PDF: j - i + k + 1, but in 0-index we drop +1

        // -----------------------------
        // Initialization: i = 0
        // D[0][j] = j
        // So prev[ CurrIndex(0,j) ] = j for j = 0..k
        // -----------------------------
        for (int x = 0; x < band; x++) prev[x] = INF;

        for (int j = 0; j <= k && j <= n; j++) {
            int idx = CurrIndex.apply(0, j);
            if (0 <= idx && idx < band) prev[idx] = j;
        }

        // -----------------------------
        // Main DP: i = 1..m
        // -----------------------------
        for (int i = 1; i <= m; i++) {

            // reset curr
            for (int x = 0; x < band; x++) curr[x] = INF;

            int start = Math.max(0, i - k);
            int end   = Math.min(i + k, n);

            for (int j = start; j <= end; j++) {

                int idx = CurrIndex.apply(i, j);
                if (idx < 0 || idx >= band) continue;

                if (j == 0) {
                    // D[i][0] = i
                    curr[idx] = i;
                } else {
                    // Compute left, up, corner
                    int left = INF;
                    int up = INF;
                    int corner = INF;

                    // left = curr[i][j-1] + 1
                    int idxLeft = CurrIndex.apply(i, j - 1);
                    if (j!=i-k) {
                        left = curr[idxLeft] + 1;
                    }

                    // up = prev[i-1][j] + 1
                    int idxUp = CurrIndex.apply(i - 1, j);
                    if (j!=i+k) {
                        up = prev[idxUp] + 1;
                    }

                    // corner = prev[i-1][j-1] + (pat[i-1] != sub[j-1] ? 1 : 0)
                    int idxCorner = CurrIndex.apply(i - 1, j - 1);
                    if (0 <= idxCorner && idxCorner < band) {
                        int cost = (pat[i - 1] == sub.charAt(j - 1)) ? 0 : 1;
                        corner = prev[idxCorner] + cost;
                    }

                    int best = left;
                    if (up < best) best = up;
                    if (corner < best) best = corner;

                    curr[idx] = best;
                }
            }

            // prev <- curr
            int[] temp = prev;
            prev = curr;
            curr = temp;
        }

        // -----------------------------
        // Output:
        // d[i] = ( D[m][j] ≤ k ) for j = (m-k)+i
        // -----------------------------
        boolean[] d = new boolean[len];

        for (int i = 0; i < len; i++) {
            int j = (m - k) + i;
            int idx = CurrIndex.apply(m, j);
            if (0 <= idx && idx < band) {
                d[i] = (prev[idx] <= k);
            } else {
                d[i] = false;
            }
        }

        return d;
    }
}
