/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.zebra.mapreduce;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.net.URI;

import junit.framework.Assert;


import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocalFileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RawLocalFileSystem;
import org.apache.hadoop.io.BytesWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.Mapper.Context;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.hadoop.zebra.BaseTestCase;
import org.apache.hadoop.zebra.io.BasicTable;
import org.apache.hadoop.zebra.io.TableInserter;
import org.apache.hadoop.zebra.mapreduce.BasicTableOutputFormat;
import org.apache.hadoop.zebra.mapreduce.ZebraOutputPartition;
import org.apache.hadoop.zebra.parser.ParseException;
import org.apache.hadoop.zebra.schema.Schema;
import org.apache.hadoop.zebra.types.TypesUtils;
import org.apache.hadoop.zebra.types.ZebraTuple;
import org.apache.pig.ExecType;
import org.apache.pig.backend.executionengine.ExecException;
import org.apache.pig.data.Tuple;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * This is a sample a complete MR sample code for Table. It doens't contain
 * 'read' part. But, it should be similar and easier to write. Refer to test
 * cases in the same directory.
 * 
 * Assume the input files contain rows of word and count, separated by a space:
 * 
 * <pre>
 * us 2
 * japan 2
 * india 4
 * us 2
 * japan 1
 * india 3
 * nouse 5
 * nowhere 4
 * 
 */
public class TestBasicTableUnion extends BaseTestCase implements Tool {

  static String inputPath;
  
  final static String STR_SCHEMA1 = "a:string,b:string";
  final static String STR_STORAGE1 = "[a];[b]";
  final static String STR_SCHEMA2 = "a:string,c:string";
  final static String STR_STORAGE2 = "[a];[c]";
  
  static Path path1, path2, path3;

  @BeforeClass
  public static void setUpOnce() throws Exception {
    init();

    path1 = getTableFullPath("t1");
    path2 = getTableFullPath("t2");
    path3 = getTableFullPath("t3");
    removeDir(path1);
    removeDir(path2);
    removeDir(path3);

    /*
     * create 1st basic table;
     */
    BasicTable.Writer writer = new BasicTable.Writer(path1, STR_SCHEMA1, STR_STORAGE1, conf);
    Schema schema = writer.getSchema();
    Tuple tuple = TypesUtils.createTuple(schema);

    final int numsBatch = 10;
    final int numsInserters = 1;
    TableInserter[] inserters = new TableInserter[numsInserters];
    for (int i = 0; i < numsInserters; i++) {
      inserters[i] = writer.getInserter("ins" + i, false);
    }

    for (int b = 0; b < numsBatch; b++) {
      for (int i = 0; i < numsInserters; i++) {
        TypesUtils.resetTuple(tuple);
        for (int k = 0; k < tuple.size(); ++k) {
          try {
            tuple.set(k, b + "_" + i + "" + k);
          } catch (ExecException e) {

          }
        }// k
        inserters[i].insert(new BytesWritable(("key1" + i).getBytes()), tuple);
      }// i
    }// b
    for (int i = 0; i < numsInserters; i++) {
      inserters[i].close();
    }
    writer.close();

    /*
     * create 2nd basic table;
     */
    writer = new BasicTable.Writer(path2, STR_SCHEMA2, STR_STORAGE2, conf);
    schema = writer.getSchema();
    tuple = TypesUtils.createTuple(schema);

    inserters = new TableInserter[numsInserters];
    for (int i = 0; i < numsInserters; i++) {
      inserters[i] = writer.getInserter("ins" + i, false);
    }

    for (int b = 0; b < numsBatch; b++) {
      for (int i = 0; i < numsInserters; i++) {
        TypesUtils.resetTuple(tuple);
        for (int k = 0; k < tuple.size(); ++k) {
          try {
            tuple.set(k, b + "_" + i + "" + k);
          } catch (ExecException e) {

          }
        }
        inserters[i].insert(new BytesWritable(("key2" + i).getBytes()), tuple);
      }
    }
    for (int i = 0; i < numsInserters; i++) {
      inserters[i].close();
    }
    writer.close();  
  }
  
  @AfterClass
  public static void tearDown() throws Exception {
    removeDir(path1);
    removeDir(path2);
    removeDir(path3);
  }
 
  static class MapClass extends Mapper<BytesWritable, Tuple, BytesWritable, Tuple> {    
    @Override
    public void map(BytesWritable key, Tuple value, Context context) throws IOException, InterruptedException {      
      System.out.println("key = " + key);
      System.out.println("value = " + value);
      
      context.write(key, value);
    }

    @Override
    public void setup(Context context) throws IOException {
    }
  }

  @Test
  public void test1() throws ParseException, IOException,
      org.apache.hadoop.zebra.parser.ParseException, Exception {

    Job job = new Job(conf);
    job.setJobName("Test1");
    job.setJarByClass(TestBasicTableUnion.class);
    
   
    // input settings
    job.setInputFormatClass(TableInputFormat.class);
    job.setMapperClass(MapClass.class);
    job.setMapOutputKeyClass(BytesWritable.class);
    job.setMapOutputValueClass(ZebraTuple.class);
    job.setOutputFormatClass(BasicTableOutputFormat.class);
    
    TableInputFormat.setInputPaths(job, path1, path2);
    TableInputFormat.setProjection(job, "a,b,c");
    BasicTableOutputFormat.setOutputPath(job, path3);

    BasicTableOutputFormat.setSchema(job, "a:string, b:string, c:string");
    BasicTableOutputFormat.setStorageHint(job, "[a,b,c]");
    
    job.submit();
    job.waitForCompletion( true );
    
    BasicTableOutputFormat.close(job);    
  }
  
  @Test(expected = IOException.class)
  public void testNegative1() throws ParseException, IOException, InterruptedException, ClassNotFoundException {
    Job job = new Job(conf);
    job.setJobName("Test1");
    job.setJarByClass(TestBasicTableUnion.class);
   
    // input settings
    job.setInputFormatClass(TableInputFormat.class);
    job.setMapperClass(MapClass.class);
    job.setMapOutputKeyClass(BytesWritable.class);
    job.setMapOutputValueClass(ZebraTuple.class);
    job.setOutputFormatClass(BasicTableOutputFormat.class);
    
    TableInputFormat.setInputPaths(job, path1, path2);
    TableInputFormat.setProjection(job, "a,b,c");
    BasicTableOutputFormat.setOutputPath(job, path3);

    BasicTableOutputFormat.setSchema(job, "a:string, b:string, c:string");
    BasicTableOutputFormat.setStorageHint(job, "[a,b,c]");
    
    job.submit();
    job.waitForCompletion( true );
    
    BasicTableOutputFormat.close(job);        
  }

  @Override
  public int run(String[] args) throws Exception {
    TestBasicTableUnion test = new TestBasicTableUnion();
    TestBasicTableUnion.setUpOnce();
    System.out.println("after setup");

    test.test1();

    return 0;
  }

  
  public static void main(String[] args) throws Exception {
    conf = new Configuration();
    
    int res = ToolRunner.run(conf, new TestBasicTableUnion(), args);
    System.out.println("PASS");
    System.exit(res);
  }
}
