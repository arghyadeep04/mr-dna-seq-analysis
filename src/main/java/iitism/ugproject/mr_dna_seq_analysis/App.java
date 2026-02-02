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

            org.apache.spark.TaskContext context = org.apache.spark.TaskContext.get();
            
            return new java.util.Iterator<String>() {
                private CharacterStream stream;
                private CircularString sub;
                private long currentWindow = 0;
                private boolean initialized = false;
                
                // matches for current window
                private boolean[] currentMatches = null;
                private int matchIndex = 0;
                
                private String nextMatch = null;
                private boolean done = false;

                private void init() {
                    if (initialized) return;
                    try {
                        // create resources
                        stream = new CharacterStream(seqFilePath, start, new org.apache.hadoop.conf.Configuration());
                        sub = new CircularString(stream, m, localK);
                        
                        context.addTaskCompletionListener(ctx -> {
                            try {
                                if (sub != null) sub.close();
                                if (stream != null) stream.close();
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        });
                        initialized = true;
                    } catch (java.io.IOException e) {
                        throw new RuntimeException(e);
                    }
                }

                @Override
                public boolean hasNext() {
                    if (nextMatch != null) return true;
                    if (done) return false;
                    
                    if (!initialized) init();

                    while (currentWindow < numWindows) {
                        // if we have pending matches from the last processed window, return them
                        if (currentMatches != null && matchIndex < currentMatches.length) {
                             if (currentMatches[matchIndex]) {
                                 long matchStart = start + currentWindow;
                                 long matchEnd = matchStart + (m - localK - 1) + matchIndex;
                                 nextMatch = matchStart + "," + matchEnd;
                                 matchIndex++;
                                 return true;
                             }
                             matchIndex++;
                             continue;
                        }

                        // no more pending matches for this window, move to next window
                        // first, check if we need to advance or if this is the first window
                        if (currentMatches != null) { 
                             // we just finished a window, try to advance
                             try {
                                 if (!sub.getNext()) {
                                     done = true;
                                     return false;
                                 }
                             } catch (java.io.IOException e) {
                                 throw new RuntimeException(e);
                             }
                             currentWindow++;
                             if (currentWindow >= numWindows) {
                                 done = true;
                                 return false; 
                             }
                        }
                        
                        // process current window
                        if (sub.getLength() < m - localK) {
                            done = true;
                            return false;
                        }

                        currentMatches = Algorithms.approximateEditDistance(sub, pattern, localK);
                        matchIndex = 0;
                    }
                    
                    done = true;
                    return false;
                }

                @Override
                public String next() {
                    if (!hasNext()) throw new java.util.NoSuchElementException();
                    String result = nextMatch;
                    nextMatch = null;
                    return result;
                }
            };
        });

        results.saveAsTextFile(outDir);
        sc.stop();
        sc.close();
    }
}
