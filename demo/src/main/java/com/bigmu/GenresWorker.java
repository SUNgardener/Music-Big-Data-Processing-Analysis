package com.bigmu;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import java.io.IOException;

import java.util.StringTokenizer;


/**
 *  Map phase
 *  Input: Each line in the text file.
 *  Processing:
 *    Process regular lines in the map method: input format track_id\tgenre\n
 *      Ignore lines starting with #.
 *      For regular lines, output key-value pairs (track_id, genre).
 *
 *  Reduce phase
 *  Input: Key-value pairs output from the map phase.
 *  Processing:
 *    Directly output key-value pairs.
 */

public class GenresWorker {
    
    public static class TokenizerMapper extends Mapper<Object, Text, Text, Text> {
        public void map(Object key, Text value, Context context) throws IOException, InterruptedException {
            String line = value.toString();
            if (!line.startsWith("#")) {
                StringTokenizer itr = new StringTokenizer(line, "\t");
                if (itr.countTokens() == 2) {
                    Text trackId = new Text(itr.nextToken());
                    Text genre = new Text(itr.nextToken());
                    context.write(trackId, genre);
                }
            }
        }
    }

    public static class IntSumReducer extends Reducer<Text, Text, Text, Text> {
        public void reduce(Text key, Iterable<Text> values, Context context) throws IOException, InterruptedException {
            for (Text val : values) {
                Text result = new Text(key.toString() + "," + val.toString());
                context.write(new Text(""), result);
            }
        }
    }
    public static void main(String[] args) throws Exception {
        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf, "genres preprocess");
        job.setJarByClass(GenresWorker.class);
        job.setMapperClass(TokenizerMapper.class);
        job.setReducerClass(IntSumReducer.class);
        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(Text.class);
        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));
        // 设置自定义的 OutputFormat 类
        job.setOutputFormatClass(CustomTextOutputFormat.class);

        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}