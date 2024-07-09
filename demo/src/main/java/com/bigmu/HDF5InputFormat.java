package com.bigmu;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.hadoop.mapreduce.TaskAttemptContext;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import io.jhdf.HdfFile;
import io.jhdf.api.Dataset;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

public class HDF5InputFormat extends FileInputFormat<LongWritable, Text> {

    @Override
    protected boolean isSplitable(JobContext context, Path filename) {
        return false; // HDF5 文件不可分割
    }

    @Override
    public RecordReader<LongWritable, Text> createRecordReader(InputSplit split, TaskAttemptContext context) throws IOException, InterruptedException {
        return new HDF5RecordReader();
    }

    public static class HDF5RecordReader extends RecordReader<LongWritable, Text> {

        private LongWritable key = new LongWritable();
        private Text value = new Text();
        private boolean processed = false;
        private FileSplit fileSplit;
        private Configuration conf;

        @Override
        public void initialize(InputSplit split, TaskAttemptContext context) throws IOException, InterruptedException {
            this.fileSplit = (FileSplit) split;
            this.conf = context.getConfiguration();
        }

        @Override
        public boolean nextKeyValue() throws IOException, InterruptedException {
            if (!processed) {
                Path filePath = fileSplit.getPath();
                FileSystem fs = filePath.getFileSystem(conf);

                // 创建一个临时文件来存储HDF5文件
                java.nio.file.Path tempFile = Files.createTempFile("hdf5_temp", ".h5");

                // 将HDFS文件复制到本地临时文件
                try (java.io.InputStream in = fs.open(filePath)) {
                    java.nio.file.Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);

                    try (HdfFile hdfFile = new HdfFile(tempFile.toFile())) {
                        Map<String, Object> attributes = extractAttributes(hdfFile);

                        // 将数据格式化为 CSV 格式
                        String result = String.format("%s,%s,%s,%s,%s,%s,%d,%.2f,%.2f,%.2f,%.2f,%.2f,%d",
                            attributes.get("song_id"),
                            attributes.get("track_id"),
                            attributes.get("title"),
                            attributes.get("release"),
                            attributes.get("artist_id"),
                            attributes.get("artist_name"),
                            (int) attributes.get("mode"),
                            (float) attributes.get("energy"),
                            (float) attributes.get("tempo"),
                            (float) attributes.get("loudness"),
                            (float) attributes.get("duration"),
                            (float) attributes.get("danceability"),
                            (int) attributes.get("year")
                        );

                        key.set(0);
                        value.set(result);
                        processed = true;
                        return true;
                    }
                } finally {
                    // 删除临时文件
                    Files.deleteIfExists(tempFile);
                }
            }
            return false;
        }

        @Override
        public LongWritable getCurrentKey() throws IOException, InterruptedException {
            return key;
        }

        @Override
        public Text getCurrentValue() throws IOException, InterruptedException {
            return value;
        }

        @Override
        public float getProgress() throws IOException, InterruptedException {
            return processed ? 1.0f : 0.0f;
        }

        @Override
        public void close() throws IOException {
            // 不需要额外的关闭操作
        }

        private Map<String, Object> extractAttributes(HdfFile hdfFile) {
            Map<String, Object> attributes = new HashMap<>();
            String[] paths = {
                "/metadata/songs/song_id",
                "/analysis/songs/track_id",
                "/metadata/songs/title",
                "/metadata/songs/release",
                "/metadata/songs/artist_id",
                "/metadata/songs/artist_name",
                "/analysis/songs/mode",
                "/analysis/songs/energy",
                "/analysis/songs/tempo",
                "/analysis/songs/loudness",
                "/analysis/songs/duration",
                "/analysis/songs/danceability",
                "/musicbrainz/songs/year"
            };

            for (String path : paths) {
                Dataset dataset = hdfFile.getDatasetByPath(path);
                if (dataset != null) {
                    String key = path.substring(path.lastIndexOf('/') + 1);
                    Object data = dataset.getData();
                    Object value = convertToString(data);
                    attributes.put(key, value);
                }
            }
            return attributes;
        }

        private Object convertToString(Object data) {
            if (data instanceof int[]) {
                return ((int[]) data)[0];
            } else if (data instanceof float[]) {
                return ((float[]) data)[0];
            } else if (data instanceof String[]) {
                return ((String[]) data)[0];
            } else {
                return data;
            }
        }
    }
}
