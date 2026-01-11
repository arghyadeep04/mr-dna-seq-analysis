package iitism.ugproject.mr_dna_seq_analysis;

import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;

public class App {
    public static void main(String[] args) {
        SparkSession spark = SparkSession.builder()
            .appName("SparkVSCodeApp")
            .master("local[*]")
            .getOrCreate();

        Dataset<Row> df = spark.read().json("sample.json");
        df.show();

        spark.stop();
    }
}
