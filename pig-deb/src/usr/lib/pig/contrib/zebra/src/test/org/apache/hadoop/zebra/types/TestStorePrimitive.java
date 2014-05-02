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

package org.apache.hadoop.zebra.types;

import java.io.StringReader;
import java.util.Iterator;
import java.util.Map;
import java.util.HashSet;
import junit.framework.Assert;

import org.apache.hadoop.zebra.types.CGSchema;
import org.apache.hadoop.zebra.schema.ColumnType;
import org.apache.hadoop.zebra.parser.ParseException;
import org.apache.hadoop.zebra.types.Partition;
import org.apache.hadoop.zebra.schema.Schema;
import org.apache.hadoop.zebra.parser.TableSchemaParser;
import org.apache.hadoop.zebra.schema.Schema.ColumnSchema;
import org.junit.Before;
import org.junit.Test;

public class TestStorePrimitive {
  String strSch = "f1:int, f2:long, f3:float, f4:bool, f5:string, f6:bytes";
  TableSchemaParser parser;
  Schema schema;

  @Before
  public void init() throws ParseException {
    parser = new TableSchemaParser(new StringReader(strSch));
    schema = parser.RecordSchema(null);
  }

  @Test
  public void testSchema() throws ParseException {
    ColumnSchema f1 = schema.getColumn(0);
    Assert.assertEquals("f1", f1.getName());
    Assert.assertEquals(ColumnType.INT, f1.getType());
    ColumnSchema f2 = schema.getColumn(1);
    Assert.assertEquals("f2", f2.getName());
    Assert.assertEquals(ColumnType.LONG, f2.getType());
    ColumnSchema f3 = schema.getColumn(2);
    Assert.assertEquals("f3", f3.getName());
    Assert.assertEquals(ColumnType.FLOAT, f3.getType());
    ColumnSchema f4 = schema.getColumn(3);
    Assert.assertEquals("f4", f4.getName());
    Assert.assertEquals(ColumnType.BOOL, f4.getType());
    ColumnSchema f5 = schema.getColumn(4);
    Assert.assertEquals("f5", f5.getName());
    Assert.assertEquals(ColumnType.STRING, f5.getType());
    ColumnSchema f6 = schema.getColumn(5);
    Assert.assertEquals("f6", f6.getName());
    Assert.assertEquals(ColumnType.BYTES, f6.getType());

    System.out.println(schema.toString());
  }

  @Test
  public void testStorageValid1() {
    try {
      String strStorage = "[f1, f2]; [f3, f4] COMPRESS BY gz SERIALIZE BY avro";
      Partition p = new Partition(schema.toString(), strStorage, null);
      CGSchema[] cgschemas = p.getCGSchemas();

      // 3 column group;
      int size = cgschemas.length;
      Assert.assertEquals(size, 3);
      System.out.println("********** Column Groups **********");
      for (int i = 0; i < cgschemas.length; i++) {
        System.out.println(cgschemas[i]);
        System.out.println("--------------------------------");
      }
      CGSchema cgs1 = cgschemas[0];
      CGSchema cgs2 = cgschemas[1];
      CGSchema cgs3 = cgschemas[2];

      ColumnSchema f11 = cgs1.getSchema().getColumn(0);
      Assert.assertEquals("f1", f11.getName());
      Assert.assertEquals(ColumnType.INT, f11.getType());
      ColumnSchema f12 = cgs1.getSchema().getColumn(1);
      Assert.assertEquals("f2", f12.getName());
      Assert.assertEquals(ColumnType.LONG, f12.getType());
      ColumnSchema f21 = cgs2.getSchema().getColumn(0);
      Assert.assertEquals("f3", f21.getName());
      Assert.assertEquals(ColumnType.FLOAT, f21.getType());
      ColumnSchema f22 = cgs2.getSchema().getColumn(1);
      Assert.assertEquals("f4", f22.getName());
      Assert.assertEquals(ColumnType.BOOL, f22.getType());
      ColumnSchema f31 = cgs3.getSchema().getColumn(0);
      Assert.assertEquals("f5", f31.getName());
      Assert.assertEquals(ColumnType.STRING, f31.getType());
      ColumnSchema f32 = cgs3.getSchema().getColumn(1);
      Assert.assertEquals("f6", f32.getName());
      Assert.assertEquals(ColumnType.BYTES, f32.getType());

      Assert.assertEquals(cgs1.getCompressor(), "gz");
      Assert.assertEquals(cgs1.getSerializer(), "pig");
      Assert.assertEquals(cgs2.getCompressor(), "gz");
      Assert.assertEquals(cgs2.getSerializer(), "avro");
      Assert.assertEquals(cgs3.getCompressor(), "gz");
      Assert.assertEquals(cgs3.getSerializer(), "pig");

      System.out.println("*********** Column Map **********");
      Map<String, HashSet<Partition.PartitionInfo.ColumnMappingEntry>> colmap = p
          .getPartitionInfo().getColMap();
      Assert.assertEquals(colmap.size(), 6);
      Iterator<Map.Entry<String, HashSet<Partition.PartitionInfo.ColumnMappingEntry>>> it = colmap
          .entrySet().iterator();
      for (int i = 0; i < colmap.size(); i++) {
        Map.Entry<String, HashSet<Partition.PartitionInfo.ColumnMappingEntry>> entry = (Map.Entry<String, HashSet<Partition.PartitionInfo.ColumnMappingEntry>>) it
            .next();
        String name = entry.getKey();
        HashSet<Partition.PartitionInfo.ColumnMappingEntry> hs = entry
            .getValue();
        Iterator<Partition.PartitionInfo.ColumnMappingEntry> it1 = hs
            .iterator();
        for (int j = 0; j < hs.size(); j++) {
          Partition.PartitionInfo.ColumnMappingEntry cme = (Partition.PartitionInfo.ColumnMappingEntry) it1
              .next();
          System.out.println("[Column = " + name + " CG = " + cme.getCGIndex()
              + "." + cme.getFieldIndex() + "]");
          if (i == 0 && j == 0) {
            Assert.assertEquals(name, "f6");
            Assert.assertEquals(cme.getCGIndex(), 2);
            Assert.assertEquals(cme.getFieldIndex(), 1);
          } else if (i == 1 && j == 0) {
            Assert.assertEquals(name, "f1");
            Assert.assertEquals(cme.getCGIndex(), 0);
            Assert.assertEquals(cme.getFieldIndex(), 0);
          } else if (i == 2 && j == 0) {
            Assert.assertEquals(name, "f3");
            Assert.assertEquals(cme.getCGIndex(), 1);
            Assert.assertEquals(cme.getFieldIndex(), 0);
          } else if (i == 3 && j == 0) {
            Assert.assertEquals(name, "f2");
            Assert.assertEquals(cme.getCGIndex(), 0);
            Assert.assertEquals(cme.getFieldIndex(), 1);
          } else if (i == 4 && j == 0) {
            Assert.assertEquals(name, "f5");
            Assert.assertEquals(cme.getCGIndex(), 2);
            Assert.assertEquals(cme.getFieldIndex(), 0);
          } else if (i == 5 && j == 0) {
            Assert.assertEquals(name, "f4");
            Assert.assertEquals(cme.getCGIndex(), 1);
            Assert.assertEquals(cme.getFieldIndex(), 1);
          }
        }
      }
    } catch (Exception e) {
      Assert.assertTrue(false);
    }
  }

  @Test
  public void testStorageValid2() {
    try {
      String strStorage = "[f1, f2] serialize by avro compress by gz; [f3, f4] SERIALIZE BY avro COMPRESS BY gz";
      Partition p = new Partition(schema.toString(), strStorage, null);
      CGSchema[] cgschemas = p.getCGSchemas();

      Assert.assertEquals(cgschemas.length, 3);
      CGSchema cgs1 = cgschemas[0];
      System.out.println(cgs1);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Test
  public void testStorageValid3() {
    try {
      String strStorage = "";
      Partition p = new Partition(schema.toString(), strStorage, null);
      CGSchema[] cgschemas = p.getCGSchemas();
      Assert.assertEquals(cgschemas.length, 1);
      CGSchema cgs1 = cgschemas[0];
      System.out.println(cgs1);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  @Test
  public void testStorageInvalid1() {
    try {
      String strStorage = "f1";
      Partition p = new Partition(schema.toString(), strStorage, null);
      CGSchema[] cgschemas = p.getCGSchemas();
      CGSchema cgs1 = cgschemas[0];
      System.out.println(cgs1);
    } catch (Exception e) {
      String errMsg = e.getMessage();
      String str = "Encountered \" <IDENTIFIER> \"f1 \"\" at line 1, column 1.\n";
      System.out.println(errMsg);
      System.out.println(str);
      Assert.assertEquals(errMsg.startsWith(str), true);
    }
  }

  @Test
  public void testStorageInvalid2() {
    try {
      String strStorage = "[f100]";
      Partition p = new Partition(schema.toString(), strStorage, null);
      CGSchema[] cgschemas = p.getCGSchemas();
      CGSchema cgs1 = cgschemas[0];
      System.out.println(cgs1);
    } catch (Exception e) {
      String errMsg = e.getMessage();
      String str = "Column f100 not defined in schema";
      System.out.println(errMsg);
      System.out.println(str);
      Assert.assertEquals(errMsg, str);
    }
  }

  @Test
  public void testStorageInvalid3() {
    try {
      String strStorage = "f1:long";
      Partition p = new Partition(schema.toString(), strStorage, null);
      CGSchema[] cgschemas = p.getCGSchemas();
      CGSchema cgs1 = cgschemas[0];
      System.out.println(cgs1);
    } catch (Exception e) {
      String errMsg = e.getMessage();
      String str = "Encountered \" <IDENTIFIER> \"f1 \"\" at line 1, column 1.\nWas expecting one of:\n";
      System.out.println(errMsg);
      System.out.println(str);
      Assert.assertEquals(errMsg.startsWith(str), true);
    }
  }

  @Test
  public void testStorageInvalid4() {
    try {
      String strStorage = "[";
      Partition p = new Partition(schema.toString(), strStorage, null);
      CGSchema[] cgschemas = p.getCGSchemas();
      CGSchema cgs1 = cgschemas[0];
      System.out.println(cgs1);
    } catch (Exception e) {
      String errMsg = e.getMessage();
      String str = "Encountered \"<EOF>\" at line 1, column 1.\nWas expecting one of:\n";
      System.out.println(errMsg);
      System.out.println(str);
      Assert.assertEquals(errMsg.startsWith(str), true);
    }
  }

  @Test
  public void testStorageInvalid5() {
    try {
      String strStorage = "[f1, f2]; [f1, f4]";
      Partition p = new Partition(schema.toString(), strStorage, null);
      CGSchema[] cgschemas = p.getCGSchemas();
      CGSchema cgs1 = cgschemas[0];
      System.out.println(cgs1);
    } catch (Exception e) {
      String errMsg = e.getMessage();
      String str = "Column f1 specified more than once!";
      System.out.println(errMsg);
      System.out.println(str);
      Assert.assertEquals(errMsg, str);
    }
  }

  @Test
  public void testStorageInvalid6() {
    try {
      String strStorage = ":";
      Partition p = new Partition(schema.toString(), strStorage, null);
      CGSchema[] cgschemas = p.getCGSchemas();
      CGSchema cgs1 = cgschemas[0];
      System.out.println(cgs1);
    } catch (Exception e) {
      String errMsg = e.getMessage();
      String str = "Encountered \" \":\" \": \"\" at line 1, column 1.\n";
      System.out.println(errMsg);
      System.out.println(str);
      Assert.assertEquals(errMsg.startsWith(str), true);
    }
  }

  @Test
  public void testStorageInvalid7() {
    try {
      String strStorage = "[f1, f2] serialize by xyz compress by gz; [f3, f4] SERIALIZE BY avro COMPRESS BY lzo";
      Partition p = new Partition(schema.toString(), strStorage, null);
      CGSchema[] cgschemas = p.getCGSchemas();
      CGSchema cgs1 = cgschemas[0];
      System.out.println(cgs1);
    } catch (Exception e) {
      String errMsg = e.getMessage();
      String str = "Encountered \" <IDENTIFIER> \"xyz \"\" at line 1, column 23.";
      System.out.println(errMsg);
      System.out.println(str);
      Assert.assertEquals(errMsg.startsWith(str), true);
    }
  }

  @Test
  public void testStorageInvalid8() {
    try {
      String strStorage = "[f1, f2] serialize by avro compress by xyz; [f3, f4] SERIALIZE BY avro COMPRESS BY lzo";
      Partition p = new Partition(schema.toString(), strStorage, null);
      CGSchema[] cgschemas = p.getCGSchemas();
      CGSchema cgs1 = cgschemas[0];
      System.out.println(cgs1);
    } catch (Exception e) {
      String errMsg = e.getMessage();
      String str = "Encountered \" <IDENTIFIER> \"xyz \"\" at line 1, column 40.";
      System.out.println(errMsg);
      System.out.println(str);
      Assert.assertEquals(errMsg.startsWith(str), true);
    }
  }
}
