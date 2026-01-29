package iitism.ugproject.mr_dna_seq_analysis;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class AppTest {

    @TempDir
    Path tempDir;

    @Test
    public void testSparkMapReduceJob() throws Exception {
        // prepare input files
        String seq = "TTTACGTAAA";
        String pat = "ACGT";
        Path seqFile = tempDir.resolve("seq.txt");
        Path patFile = tempDir.resolve("pat.txt");
        Path outDir = tempDir.resolve("output");

        Files.writeString(seqFile, seq);
        Files.writeString(patFile, pat);

        // run Spark Job (k=0, partitions=2)
        String[] args = {
                seqFile.toString(),
                patFile.toString(),
                "0", // k
                "2", // w (partitions)
                outDir.toString()
        };
        App.main(args);

        // collect and verify output from part files
        List<String> results;
        try (Stream<Path> walk = Files.walk(outDir)) {
            results = walk
                    .filter(p -> p.getFileName().toString().startsWith("part-"))
                    .flatMap(p -> {
                        try {
                            return Files.readAllLines(p).stream();
                        } catch (Exception e) {
                            return Stream.empty();
                        }
                    })
                    .collect(Collectors.toList());
        }

        // Match for "ACGT" in "TTTACGTAAA" starts at index 3 and ends at 6
        assertTrue(results.contains("3,6"), "Output should contain the match at 3,6. Found: " + results);
    }
}
