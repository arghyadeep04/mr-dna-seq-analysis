package iitism.ugproject.mr_dna_seq_analysis;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.broadcast.Broadcast;
import java.util.ArrayList;
import java.util.List;

public class App {
    public static void main(String[] args) throws Exception {
        if (args.length < 5) {
            System.err.println("Usage: App <seqFile> <patFile> <k> <w> <outDir>");
            System.exit(1);
        }

        String seqFilePath = args[0];
        String patFilePath = args[1];
        int k = Integer.parseInt(args[2]);
        int w = Integer.parseInt(args[3]);
        String outDir = args[4];

        SparkConf conf = new SparkConf().setAppName("DNA-Seq-Analysis").setIfMissing("spark.master", "local[*]");
        JavaSparkContext sc = new JavaSparkContext(conf);

        org.apache.hadoop.conf.Configuration hadoopConf = sc.hadoopConfiguration();

        // load pattern and metadata using Hadoop FileSystem
        org.apache.hadoop.fs.Path patPath = new org.apache.hadoop.fs.Path(patFilePath);
        org.apache.hadoop.fs.FileSystem fs = patPath.getFileSystem(hadoopConf);
        
        String patternStr;
        try(java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(fs.open(patPath)))) {
            patternStr = br.readLine(); // pattern is on the first line
        }
        
        org.apache.hadoop.fs.Path seqPath = new org.apache.hadoop.fs.Path(seqFilePath);
        org.apache.hadoop.fs.FileSystem seqFs = seqPath.getFileSystem(hadoopConf);
        long n = seqFs.getFileStatus(seqPath).getLen();
        
        Broadcast<String> bPattern = sc.broadcast(patternStr);
        Broadcast<Integer> bK = sc.broadcast(k);

        // create partition indices
        List<Integer> partitions = new ArrayList<>();
        for (int i = 0; i < w; i++) partitions.add(i);
        JavaRDD<Integer> partitionRDD = sc.parallelize(partitions, w);

        // map phase
        JavaRDD<String> results = partitionRDD.flatMap(id -> {
            char[] pattern = bPattern.value().toCharArray();
            int m = pattern.length;
            int localK = bK.value();
            
            long chunk = n / w;
            long start = id * chunk;
            long end = (id == w - 1) ? (n - 1) : ((id + 1) * chunk - 1);
            long numWindows = end - start + 1;

            List<String> matches = new ArrayList<>();
            try (CharacterStream stream = new CharacterStream(seqFilePath, start, new org.apache.hadoop.conf.Configuration())) {
                 
                // circularString reads patternLength + k (t) characters initially
                try (CircularString sub = new CircularString(stream, m, localK)) {
                    for (long i = 0; i < numWindows; i++) {
                        // check if current window has enough characters for a match
                        if (sub.getLength() < m - localK) break;

                        boolean[] d = Algorithms.approximateEditDistance(sub, pattern, localK);
                        for (int j = 0; j < d.length; j++) {
                            if (d[j]) {
                                long matchStart = start + i;
                                long matchEnd = matchStart + (m - localK - 1) + j;
                                matches.add(matchStart + "," + matchEnd);
                            }
                        }
                        
                        if (!sub.getNext()) break;
                    }
                }
            }
            return matches.iterator();
        });

        results.saveAsTextFile(outDir);
        sc.stop();
        sc.close();
    }
}
