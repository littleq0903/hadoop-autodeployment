/*
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
package org.apache.pig.test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import junit.framework.AssertionFailedError;


import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.InputFormat;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.RecordReader;
import org.apache.pig.ExecType;
import org.apache.pig.Expression;
import org.apache.pig.LoadFunc;
import org.apache.pig.LoadMetadata;
import org.apache.pig.PigServer;
import org.apache.pig.ResourceSchema;
import org.apache.pig.ResourceStatistics;
import org.apache.pig.backend.hadoop.executionengine.mapReduceLayer.PigSplit;
import org.apache.pig.data.Tuple;
import org.apache.pig.newplan.logical.expression.LogicalExpression;
import org.apache.pig.newplan.logical.optimizer.LogicalPlanOptimizer;
import org.apache.pig.newplan.logical.relational.LOFilter;
import org.apache.pig.newplan.logical.relational.LogicalPlan;
import org.apache.pig.newplan.logical.rules.PartitionFilterOptimizer;
import org.apache.pig.newplan.logical.rules.LoadTypeCastInserter;
import org.apache.pig.newplan.Operator;
import org.apache.pig.newplan.OperatorPlan;
import org.apache.pig.newplan.OperatorSubPlan;
import org.apache.pig.newplan.PColFilterExtractor;
import org.apache.pig.newplan.optimizer.PlanOptimizer;
import org.apache.pig.newplan.optimizer.Rule;
import org.apache.pig.newplan.optimizer.Transformer;
import org.apache.pig.parser.ParserException;
import org.apache.pig.impl.PigContext;
import org.apache.pig.impl.logicalLayer.schema.Schema;
import org.apache.pig.impl.util.LogUtils;
import org.apache.pig.impl.util.Utils;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * unit tests to test extracting partition filter conditions out of the filter
 * condition in the filter following a load which talks to metadata system (.i.e.
 * implements {@link LoadMetadata})
 */
@Deprecated
public class TestPartitionFilterPushDown {
    static PigContext pc = new PigContext(ExecType.LOCAL, new Properties());
    String query = "a = load 'foo' as (srcid:int, mrkt:chararray, dstid:int, name:chararray, " +
    		"age:int, browser:map[], location:tuple(country:chararray, zip:int));";

    @BeforeClass
    public static void setup() throws Exception {
    }

    @AfterClass
    public static void tearDown() {
    }

    /**
     * test case where there is a single expression on partition columns in
     * the filter expression along with an expression on non partition column
     * @throws Exception
     */
    @Test
    public void testSimpleMixed() throws Exception {
        String q = query + "b = filter a by srcid == 10 and name == 'foo';" + "store b into 'out';";
        test(q, Arrays.asList("srcid"), "(srcid == 10)", "(name == 'foo')");
    }

    /**
     * test case where filter does not contain any condition on partition cols
     * @throws Exception
     */
    @Test
    public void testNoPartFilter() throws Exception {
        String q = query + "b = filter a by age == 20 and name == 'foo';" + "store b into 'out';";
        test(q, Arrays.asList("srcid"), null,
        "((age == 20) and (name == 'foo'))");
    }

    /**
     * test case where filter only contains condition on partition cols
     * @throws Exception
     */
    @Test
    public void testOnlyPartFilter1() throws Exception {
        String q = query + "b = filter a by srcid > 20 and mrkt == 'us';" + "store b into 'out';";
        test(q, Arrays.asList("srcid", "mrkt"),
                "((srcid > 20) and (mrkt == 'us'))", null);

    }

    /**
     * test case where filter only contains condition on partition cols
     * @throws Exception
     */
    @Test
    public void testOnlyPartFilter2() throws Exception {
        String q = query + "b = filter a by mrkt == 'us';" + "store b into 'out';";
        test(q, Arrays.asList("srcid", "mrkt"),
                "(mrkt == 'us')", null);

    }

    /**
     * test case where filter only contains condition on partition cols
     * @throws Exception
     */
    @Test
    public void testOnlyPartFilter3() throws Exception {
        String q = query + "b = filter a by srcid == 20 or mrkt == 'us';" + "store b into 'out';";
        test(q, Arrays.asList("srcid", "mrkt"),
                "((srcid == 20) or (mrkt == 'us'))", null);

    }

    /**
     * test case where filter has both conditions on partition cols and non
     * partition cols and the filter condition will be split to extract the
     * conditions on partition columns
     */
    @Test
    public void testMixed1() throws Exception {
        String q = query + "b = filter a by " +
        "(age < 20 and  mrkt == 'us') and (srcid == 10 and " +
        "name == 'foo');" + "store b into 'out';";
        test(q, Arrays.asList("srcid", "mrkt"),
                "((mrkt == 'us') and (srcid == 10))",
        "((age < 20) and (name == 'foo'))");
    }


    /**
     * test case where filter has both conditions on partition cols and non
     * partition cols and the filter condition will be split to extract the
     * conditions on partition columns
     */
    @Test
    public void testMixed2() throws Exception {
        String q = query + "b = filter a by " +
        "(age >= 20 and  mrkt == 'us') and (srcid == 10 and " +
        "dstid == 15);" + "store b into 'out';";
        test(q, Arrays.asList("srcid", "dstid", "mrkt"),
                "((mrkt == 'us') and ((srcid == 10) and (dstid == 15)))",
        "(age >= 20)");
    }

    /**
     * test case where filter has both conditions on partition cols and non
     * partition cols and the filter condition will be split to extract the
     * conditions on partition columns
     */
    @Test
    public void testMixed3() throws Exception {
        String q = query + "b = filter a by " +
        "age >= 20 and  mrkt == 'us' and srcid == 10;" + "store b into 'out';";
        test(q, Arrays.asList("srcid", "dstid", "mrkt"),
                "((mrkt == 'us') and (srcid == 10))", "(age >= 20)");
    }

    /**
     * test case where filter has both conditions on partition cols and non
     * partition cols and the filter condition will be split to extract the
     * conditions on partition columns - this testcase also has a condition
     * based on comparison of two partition columns
     */
    @Test
    public void testMixed4() throws Exception {
        String q = query + "b = filter a by " +
        "age >= 20 and  mrkt == 'us' and name == 'foo' and " +
        "srcid == dstid;" + "store b into 'out';";
        test(q, Arrays.asList("srcid", "dstid", "mrkt"),
                "((mrkt == 'us') and (srcid == dstid))",
        "((age >= 20) and (name == 'foo'))");
    }

    /**
     * test case where filter has both conditions on partition cols and non
     * partition cols and the filter condition will be split to extract the
     * conditions on partition columns -
     * This testcase has two partition col conditions  with OR +  non parition
     * col conditions
     */
    @Test
    public void testMixed5() throws Exception {
        String q = query + "b = filter a by " +
        "(srcid == 10 or mrkt == 'us') and name == 'foo' and " +
        "dstid == 30;" + "store b into 'out';";
        test(q, Arrays.asList("srcid", "dstid", "mrkt"),
                "(((srcid == 10) or (mrkt == 'us')) and (dstid == 30))",
        "(name == 'foo')");
    }

    /**
     * test case where filter has both conditions on partition cols and non
     * partition cols and the filter condition will be split to extract the
     * conditions on partition columns -
     * This testcase has two partition col conditions  with OR +  non parition
     * col conditions
     */
    @Test
    public void testMixed6() throws Exception {
        String q = query + "b = filter a by " +
        "dstid == 30 and (srcid == 10 or mrkt == 'us') and name == 'foo';" + "store b into 'out';";
        test(q, Arrays.asList("srcid", "dstid", "mrkt"),
                "((dstid == 30) and ((srcid == 10) or (mrkt == 'us')))",
        "(name == 'foo')");
    }

    @Test
    public void test7() throws Exception {
        String query = "a = load 'foo' using " + TestLoader.class.getName() +
            "('srcid, mrkt, dstid, name, age', 'srcid, name');" +
            "b = filter a by (srcid < 20 and age < 30) or (name == 'foo' and age > 40);" +
            "store b into 'output';";
        LogicalPlan plan = buildPlan(new PigServer(pc), query);

        Rule rule = new PartitionFilterOptimizer("test");
        List<OperatorPlan> matches = rule.match(plan);
        if (matches != null) {
            Transformer transformer = rule.getNewTransformer();
            for (OperatorPlan m : matches) {
                if (transformer.check(m)) {
                    transformer.transform(m);
                }
            }
            OperatorSubPlan newPlan = (OperatorSubPlan)transformer.reportChanges();

            Assert.assertTrue(newPlan.getBasePlan().isEqual(plan));
        }

    }

    @Test
    public void test8() throws Exception {
        String query = "a = load 'foo' using " + TestLoader.class.getName() +
            "('srcid, mrkt, dstid, name, age', 'srcid,name');" +
            "b = filter a by (srcid < 20) or (name == 'foo');" +
            "store b into 'output';";
        LogicalPlan plan = Util.buildLp(new PigServer(pc), query);

        Rule rule = new PartitionFilterOptimizer("test");
        List<OperatorPlan> matches = rule.match(plan);
        if (matches != null) {
            Transformer transformer = rule.getNewTransformer();
            for (OperatorPlan m : matches) {
                if (transformer.check(m)) {
                    transformer.transform(m);
                }
            }
            OperatorSubPlan newPlan = (OperatorSubPlan)transformer.reportChanges();

            Assert.assertTrue(newPlan.getBasePlan().size() == 3);
        }

    }


    /**
     * test case where filter has both conditions on partition cols and non
     * partition cols and the filter condition will be split to extract the
     * conditions on partition columns. This testcase also tests arithmetic
     * in partition column conditions
     */
    @Test
    public void testMixedArith() throws Exception {
        String q = query + "b = filter a by " +
        "mrkt == 'us' and srcid * 10 == 150 + 20 and age != 15;" + "store b into 'out';";
        test(q, Arrays.asList("srcid", "dstid", "mrkt"),
                "((mrkt == 'us') and ((srcid * 10) == (150 + 20)))",
        "(age != 15)");
    }

    /**
     * test case where there is a single expression on partition columns in the
     * filter expression along with an expression on non partition column of
     * type map
     * @throws Exception
     */
    @Test
    public void testMixedNonPartitionTypeMap() throws Exception {
        String q = query + "b = filter a by srcid == 10 and browser#'type' == 'IE';" +
                "store b into 'out';";
        test(q, Arrays.asList("srcid"), "(srcid == 10)");

        q = query + "b = filter a by srcid == 10 and browser#'type' == 'IE' and " +
                "browser#'version'#'major' == '8.0';" + "store b into 'out';";
        test(q, Arrays.asList("srcid"), "(srcid == 10)");

        // Some complex partition filters with a non-partition filter
        q = query + "b = filter a by srcid == 10 and mrkt > '1' and mrkt < '5';" +
                "c = filter b by browser#'type' == 'IE';" + "store b into 'out';";
        test(q, Arrays.asList("srcid", "mrkt"),
                "(((srcid == 10) and (mrkt > '1')) and (mrkt < '5'))");

        q = query + "b = filter a by srcid == 10 and (mrkt > '1' and mrkt < '5');" +
                "c = filter b by browser#'type' == 'IE';" + "store b into 'out';";
        test(q, Arrays.asList("srcid", "mrkt"),
                "((srcid == 10) and ((mrkt > '1') and (mrkt < '5')))");
    }

    /**
     * test case where there is a single expression on partition columns in the
     * filter expression along with an expression on non partition column of
     * type tuple
     * @throws Exception
     */
    @Test
    public void testMixedNonPartitionTypeTuple() throws Exception {
        String q = query + "b = filter a by srcid == 10 and location.country == 'US';" +
                "store b into 'out';";
        test(q, Arrays.asList("srcid"), "(srcid == 10)");
    }

    @Test
    public void testAndORConditionPartitionKeyCol() throws Exception {
        // Case of AND and OR
        String q = query + "b = filter a by (srcid == 10 and dstid == 5) " +
                "or (srcid == 11 and dstid == 6) or (srcid == 12 and dstid == 7);" +
                "store b into 'out';";
        test(q, Arrays.asList("srcid", "dstid"),
                "((((srcid == 10) and (dstid == 5)) " +
                "or ((srcid == 11) and (dstid == 6))) or ((srcid == 12) and (dstid == 7)))",
                null);

        // Additional filter on non-partition key column
        q = query +
                "b = filter a by ((srcid == 10 and dstid == 5) " +
                "or (srcid == 11 and dstid == 6) or (srcid == 12 and dstid == 7)) and mrkt == 'US';" +
                "store b into 'out';";
        test(q, Arrays.asList("srcid", "dstid"),
                "((((srcid == 10) and (dstid == 5)) " +
                "or ((srcid == 11) and (dstid == 6))) or ((srcid == 12) and (dstid == 7)))",
                "(mrkt == 'US')");

        // partition key col but null condition which should not become part of
        // the pushed down filter
        q = query + "b = filter a by (srcid is null and dstid == 5) " +
                "or (srcid == 11 and dstid == 6) or (srcid == 12 and dstid == 7);" +
                "store b into 'out';";
        test(q, Arrays.asList("srcid", "dstid"), null);

        // Case of OR and AND
        q = query +
                "b = filter a by (mrkt == 'US' or mrkt == 'UK') and (srcid == 11 or srcid == 10);" +
                "store b into 'out';";
        test(q, Arrays.asList("srcid", "mrkt"),
                "(((mrkt == 'US') or (mrkt == 'UK')) and ((srcid == 11) or (srcid == 10)))", null);
        q = query +
                "b = filter a by (mrkt == 'US' or mrkt == 'UK') and (srcid == 11 or srcid == 10) and dstid == 10;" +
                "store b into 'out';";
        test(q, Arrays.asList("srcid", "mrkt"),
                "(((mrkt == 'US') or (mrkt == 'UK')) and ((srcid == 11) or (srcid == 10)))",
                "(dstid == 10)");
    }

    @Test
    public void testAndORConditionMixedCol() throws Exception {
        // Case of AND and OR with partition key and non-partition key columns
        String q = query + "b = filter a by (srcid == 10 and dstid == 5) " +
                "or (srcid == 11 and dstid == 6) or (srcid == 12 and dstid == 7) " +
                "or (srcid == 13 and dstid == 8);" +
                "store b into 'out';";
        test(q, Arrays.asList("srcid"), null,
                "(((((srcid == 10) and (dstid == 5)) " +
                "or ((srcid == 11) and (dstid == 6))) or ((srcid == 12) and (dstid == 7))) " +
                "or ((srcid == 13) and (dstid == 8)))");

        // Additional filter on a partition key column
        q = query +
                "b = filter a by ((srcid == 10 and dstid == 5) or (srcid == 11 and dstid == 6) " +
                "or (srcid == 12 and dstid == 7)) and mrkt == 'US';" +
                "store b into 'out';";
        test(q, Arrays.asList("srcid", "mrkt"), "(mrkt == 'US')",
                "((((srcid == 10) and (dstid == 5)) or ((srcid == 11) and (dstid == 6))) " +
                "or ((srcid == 12) and (dstid == 7)))");

        q = query + "b = filter a by (mrkt == 'US' or mrkt == 'UK') and " +
                "((srcid == 10 and dstid == 5) or (srcid == 11 and dstid == 6) " +
                "or (srcid == 12 and dstid == 7));" +
                "store b into 'out';";
        test(q, Arrays.asList("srcid", "mrkt"), "((mrkt == 'US') or (mrkt == 'UK'))",
                "((((srcid == 10) and (dstid == 5)) or ((srcid == 11) and (dstid == 6))) " +
                "or ((srcid == 12) and (dstid == 7)))");

        // Additional filter on a non-partition key column
        q = query +
                "b = filter a by ((srcid == 10 and dstid == 5) or (srcid == 11 and dstid == 6) " +
                "or (srcid == 12 and dstid == 7)) and mrkt == 'US';" +
                "store b into 'out';";
        test(q, Arrays.asList("srcid"), null,
                "(((((srcid == 10) and (dstid == 5)) or ((srcid == 11) and (dstid == 6))) " +
                "or ((srcid == 12) and (dstid == 7))) and (mrkt == 'US'))");

        // Case of OR and AND
        q = query +
                "b = filter a by (mrkt == 'US' or mrkt == 'UK') and " +
                "(srcid == 11 or srcid == 10) and (dstid == 5 or dstid == 6);" +
                "store b into 'out';";
        test(q, Arrays.asList("srcid"),
                "((srcid == 11) or (srcid == 10))",
                "(((mrkt == 'US') or (mrkt == 'UK')) and ((dstid == 5) or (dstid == 6)))");
        test(q, Arrays.asList("mrkt"),
                "((mrkt == 'US') or (mrkt == 'UK'))",
                "(((srcid == 11) or (srcid == 10)) and ((dstid == 5) or (dstid == 6)))");
    }

    public void testNegPColConditionWithNonPCol() throws Exception {
        // use of partition column condition and non partition column in
        // same condition should fail
        String q = query + "b = filter a by " +
        "srcid > age;" + "store b into 'out';";
        negativeTest(q, Arrays.asList("srcid"), 1111);
        q = query + "b = filter a by " +
        "srcid + age == 20;" + "store b into 'out';";
        negativeTest(q, Arrays.asList("srcid"), 1111);

        // OR of partition column condition and non partiton col condition
        // should fail
        q = query + "b = filter a by " +
        "srcid > 10 or name == 'foo';" +
        "store b into 'out';";
        negativeTest(q, Arrays.asList("srcid"), 1111);
    }

    @Test
    public void testNegPColInWrongPlaces() throws Exception {
        int expectedErrCode = 1112;

        String q = query + "b = filter a by " +
        "(srcid > 10 and name == 'foo') or dstid == 10;" + "store b into 'out';";
        negativeTest(q, Arrays.asList("srcid", "dstid"), expectedErrCode);

        expectedErrCode = 1110;
        q = query + "b = filter a by " +
        "CONCAT(mrkt, '_10') == 'US_10' and age == 20;" + "store b into 'out';";
        negativeTest(q, Arrays.asList("srcid", "dstid", "mrkt"), expectedErrCode);

        q = query + "b = filter a by " +
        "mrkt matches '.*us.*' and age < 15;" + "store b into 'out';";
        negativeTest(q, Arrays.asList("srcid", "dstid", "mrkt"), expectedErrCode);

        q = query + "b = filter a by " +
        "(int)mrkt == 10 and name matches '.*foo.*';" + "store b into 'out';";
        negativeTest(q, Arrays.asList("srcid", "dstid", "mrkt"),expectedErrCode);

        q = query + "b = filter a by " +
        "(mrkt == 'us' ? age : age + 10) == 40 and name matches '.*foo.*';" + "store b into 'out';";
        negativeTest(q, Arrays.asList("srcid", "dstid", "mrkt"), expectedErrCode);

        q = query + "b = filter a by " +
        "(mrkt is null) and name matches '.*foo.*';" + "store b into 'out';";
        negativeTest(q, Arrays.asList("srcid", "dstid", "mrkt"), expectedErrCode);

        q = query + "b = filter a by " +
        "(mrkt is not null) and name matches '.*foo.*';" + "store b into 'out';";
        negativeTest(q, Arrays.asList("srcid", "dstid", "mrkt"), expectedErrCode);
    }

//    @Test
//    public void testNegPColInWrongPlaces2() throws Exception {
//
//        LogicalPlanTester tester = new LogicalPlanTester(pc);
//        tester.buildPlan("a = load 'foo' using " + TestLoader.class.getName()
//                + "('srcid, mrkt, dstid, name, age', 'srcid,dstid,mrkt');");
//
//        org.apache.pig.impl.logicalLayer.LogicalPlan lp = tester
//                .buildPlan("b = filter a by "
//                        + "(srcid > 10 and name == 'foo') or dstid == 10;");
//        negativeTest(lp);
//
//        lp = tester.buildPlan("b = filter a by " +
//                "CONCAT(mrkt, '_10') == 'US_10' and age == 20;");
//        negativeTest(lp);
//
//        lp = tester.buildPlan("b = filter a by " +
//                "mrkt matches '.*us.*' and age < 15;");
//        negativeTest(lp);
//
//        lp = tester.buildPlan("b = filter a by " +
//                "(int)mrkt == 10 and name matches '.*foo.*';");
//        negativeTest(lp);
//
//        lp = tester.buildPlan("b = filter a by " +
//            "(mrkt == 'us' ? age : age + 10) == 40 and name matches '.*foo.*';");
//        negativeTest(lp);
//
//        lp = tester.buildPlan("b = filter a by " +
//            "(mrkt is null) and name matches '.*foo.*';");
//        negativeTest(lp);
//
//        lp = tester.buildPlan("b = filter a by " +
//            "(mrkt is not null) and name matches '.*foo.*';");
//        negativeTest(lp);
//    }


    /**
     * Test that pig sends correct partition column names in setPartitionFilter
     * when the user has a schema in the load statement which renames partition
     * columns
     * @throws Exception
     */
    @Test
    public void testColNameMapping1() throws Exception {
        TestLoader.partFilter = null;
        String q = "a = load 'foo' using "
            + TestLoader.class.getName() +
            "('srcid:int, mrkt:chararray, dstid:int, name:chararray, age:int', " +
            "'srcid,mrkt') as (f1, f2, f3, f4, f5);" +
            "b = filter a by " +
            "(f5 >= 20 and f2 == 'us') and (f1 == 10 and f3 == 15);" +
            "store b into 'out';";

        LogicalPlan newLogicalPlan = migrateAndOptimizePlan( q );

        Assert.assertEquals("checking partition filter:",
                "((mrkt == 'us') and (srcid == 10))",
                TestLoader.partFilter.toString());
        Operator op = newLogicalPlan.getSinks().get(0);
        LOFilter filter = (LOFilter)newLogicalPlan.getPredecessors(op).get(0);

        PColFilterExtractor extractor = new PColFilterExtractor(filter.getFilterPlan(), new ArrayList<String>());

        String actual = extractor.getExpression(
                (LogicalExpression)filter.getFilterPlan().getSources().get(0)).
                toString().toLowerCase();
        Assert.assertEquals("checking trimmed filter expression:",
                "((f5 >= 20) and (f3 == 15))", actual);
    }

    private LogicalPlan migrateAndOptimizePlan(String query) throws Exception {
        PigServer pigServer = new PigServer( pc );
        LogicalPlan newLogicalPlan = Util.buildLp(pigServer, query);
        PlanOptimizer optimizer = new MyPlanOptimizer( newLogicalPlan, 3 );
        optimizer.optimize();
        return newLogicalPlan;
    }


    /**
     * Test that pig sends correct partition column names in setPartitionFilter
     * when the user has a schema in the load statement which renames partition
     * columns - in this test case there is no condition on partition columns
     * - so setPartitionFilter() should not be called and the filter condition
     * should remain as is.
     * @throws Exception
     */
    @Test
    public void testColNameMapping2() throws Exception {
        TestLoader.partFilter = null;
        String q = "a = load 'foo' using "
            + TestLoader.class.getName() +
            "('srcid:int, mrkt:chararray, dstid:int, name:chararray, age:int', " +
            "'srcid') as (f1, f2, f3, f4, f5);" +
            "b = filter a by " +
            "f5 >= 20 and f2 == 'us' and f3 == 15;" +
            "store b into 'out';";

        LogicalPlan newLogicalPlan = migrateAndOptimizePlan( q );

        Assert.assertEquals("checking partition filter:",
                null,
                TestLoader.partFilter);
        Operator op = newLogicalPlan.getSinks().get(0);
        LOFilter filter = (LOFilter)newLogicalPlan.getPredecessors(op).get(0);

        PColFilterExtractor extractor = new PColFilterExtractor(filter.getFilterPlan(), new ArrayList<String>());

        String actual = extractor.getExpression(
                (LogicalExpression) filter.getFilterPlan().
                getSources().get(0)).
                toString().toLowerCase();
        Assert.assertEquals("checking trimmed filter expression:",
                "(((f5 >= 20) and (f2 == 'us')) and (f3 == 15))", actual);
    }

    /**
     * Test that pig sends correct partition column names in setPartitionFilter
     * when the user has a schema in the load statement which renames partition
     * columns - in this test case the filter only has conditions on partition
     * columns
     * @throws Exception
     */
    @Test
    public void testColNameMapping3() throws Exception {
        TestLoader.partFilter = null;
        String query = "a = load 'foo' using "
            + TestLoader.class.getName() +
            "('srcid:int, mrkt:chararray, dstid:int, name:chararray, age:int', " +
            "'srcid,mrkt,dstid,age') as (f1, f2, f3, f4, f5);" +
            "b = filter a by " +
            "(f5 >= 20 or f2 == 'us') and (f1 == 10 and f3 == 15);" +
            "store b into 'out';";

        LogicalPlan newLogicalPlan = migrateAndOptimizePlan( query );

        Assert.assertEquals("checking partition filter:",
                "(((age >= 20) or (mrkt == 'us')) and ((srcid == 10) and " +
                "(dstid == 15)))",
                TestLoader.partFilter.toString());
        Iterator<Operator> it = newLogicalPlan.getOperators();
        while( it.hasNext() ) {
	        Assert.assertFalse("Checking that filter has been removed since it contained" +
	                " only conditions on partition cols:",
	                (it.next() instanceof LOFilter));
        }
    }

    /**
     * Test that pig sends correct partition column names in setPartitionFilter
     * when the user has a schema in the load statement which renames partition
     * columns - in this test case the schema in load statement is a prefix
     * (with columns renamed) of the schema returned by
     * {@link LoadMetadata#getSchema(String, Configuration)}
     * @throws Exception
     */
    @Test
    public void testColNameMapping4() throws Exception {
        TestLoader.partFilter = null;
        String q = "a = load 'foo' using "
            + TestLoader.class.getName() +
            "('srcid:int, mrkt:chararray, dstid:int, name:chararray, age:int', " +
            "'srcid,mrkt') as (f1:int, f2:chararray, f3:int, name:chararray, age:int);" +
            "b = filter a by " +
            "(age >= 20 and f2 == 'us') and (f1 == 10 and f3 == 15);" + "store b into 'out';";

        LogicalPlan newLogicalPlan = migrateAndOptimizePlan( q );

        Assert.assertEquals("checking partition filter:",
                "((mrkt == 'us') and (srcid == 10))",
                TestLoader.partFilter.toString());
        Operator op = newLogicalPlan.getSinks().get(0);
        LOFilter filter = (LOFilter)newLogicalPlan.getPredecessors(op).get(0);

        PColFilterExtractor extractor = new PColFilterExtractor(filter.getFilterPlan(), new ArrayList<String>());

        String actual = extractor.getExpression(
                (LogicalExpression) filter.getFilterPlan().getSources().get(0)).
                toString().toLowerCase();
        Assert.assertEquals("checking trimmed filter expression:",
                "((age >= 20) and (f3 == 15))", actual);
    }

    /**
     * Test PIG-1267
     * @throws Exception
     */
    @Test
    public void testColNameMapping5() throws Exception {
        TestLoader.partFilter = null;
        String q = "a = load 'foo' using "
            + TestLoader.class.getName() +
            "('mrkt:chararray, a1:chararray, a2:chararray, srcid:int, bcookie:chararray', " +
            "'srcid');" +
            "b = load 'bar' using "
            + TestLoader.class.getName() +
            "('dstid:int, b1:int, b2:int, srcid:int, bcookie:chararray, mrkt:chararray'," +
            "'srcid');" +
            "a1 = filter a by srcid == 10;" +
            "b1 = filter b by srcid == 20;"+
            "c = join a1 by bcookie, b1 by bcookie;" +
            "d = foreach c generate $4 as bcookie:chararray, " +
            "$5 as dstid:int, $0 as mrkt:chararray;" +
            "store d into 'out';";

        LogicalPlan newLogicalPlan = migrateAndOptimizePlan( q );

        String partFilter = TestLoader.partFilter.toString();
        Assert.assertTrue( "(srcid == 20)".equals( partFilter ) ||  "(srcid == 10)".equals( partFilter ) );

        int counter = 0;
        Iterator<Operator> iter = newLogicalPlan.getOperators();
        while (iter.hasNext()) {
            Assert.assertTrue(!(iter.next() instanceof LOFilter));
            counter++;
        }
        Assert.assertEquals(counter, 5);
    }

    /**
     * Test PIG-2778 Add matches operator to predicate pushdown
     * @throws Exception
     */
    @Test
    public void testMatchOpPushDown() throws Exception {
        // regexp condition on a partition col
        String q = query + "b = filter a by name matches 'foo*';" + "store b into 'out';";
        test(q, Arrays.asList("name"), "(name matches 'foo*')", null);

        // regexp condition on a non-partition col
        q = query + "b = filter a by name matches 'foo*';" + "store b into 'out';";
        test(q, Arrays.asList("srcid"), null, "(name matches 'foo*')");
    }

    /**
     * Test PIG-3395 Large filter expression makes Pig hang
     * @throws Exception
     */
    @Test
    public void testLargeAndOrCondition() throws Exception {
        String q = query + "b = filter a by " +
                "(srcid == 1 and mrkt == '2' and dstid == 3) " +
                "or (srcid == 4 and mrkt == '5' and dstid == 6) " +
                "or (srcid == 7 and mrkt == '8' and dstid == 9) " +
                "or (srcid == 10 and mrkt == '11' and dstid == 12) " +
                "or (srcid == 13 and mrkt == '14' and dstid == 15) " +
                "or (srcid == 16 and mrkt == '17' and dstid == 18) " +
                "or (srcid == 19 and mrkt == '20' and dstid == 21) " +
                "or (srcid == 22 and mrkt == '23' and dstid == 24) " +
                "or (srcid == 25 and mrkt == '26' and dstid == 27) " +
                "or (srcid == 28 and mrkt == '29' and dstid == 30) " +
                "or (srcid == 31 and mrkt == '32' and dstid == 33) " +
                "or (srcid == 34 and mrkt == '35' and dstid == 36) " +
                "or (srcid == 37 and mrkt == '38' and dstid == 39) " +
                "or (srcid == 40 and mrkt == '41' and dstid == 42) " +
                "or (srcid == 43 and mrkt == '44' and dstid == 45) " +
                "or (srcid == 46 and mrkt == '47' and dstid == 48) " +
                "or (srcid == 49 and mrkt == '50' and dstid == 51) " +
                "or (srcid == 52 and mrkt == '53' and dstid == 54) " +
                "or (srcid == 55 and mrkt == '56' and dstid == 57) " +
                "or (srcid == 58 and mrkt == '59' and dstid == 60) " +
                "or (srcid == 61 and mrkt == '62' and dstid == 63) " +
                "or (srcid == 64 and mrkt == '65' and dstid == 66) " +
                "or (srcid == 67 and mrkt == '68' and dstid == 69);" +
                "store b into 'out';";
        test(q, Arrays.asList("srcid", "mrkt", "dstid"),
                "(((((((((((((((((((((((((srcid == 1) and (mrkt == '2')) and (dstid == 3)) " +
                "or (((srcid == 4) and (mrkt == '5')) and (dstid == 6))) " +
                "or (((srcid == 7) and (mrkt == '8')) and (dstid == 9))) " +
                "or (((srcid == 10) and (mrkt == '11')) and (dstid == 12))) " +
                "or (((srcid == 13) and (mrkt == '14')) and (dstid == 15))) " +
                "or (((srcid == 16) and (mrkt == '17')) and (dstid == 18))) " +
                "or (((srcid == 19) and (mrkt == '20')) and (dstid == 21))) " +
                "or (((srcid == 22) and (mrkt == '23')) and (dstid == 24))) " +
                "or (((srcid == 25) and (mrkt == '26')) and (dstid == 27))) " +
                "or (((srcid == 28) and (mrkt == '29')) and (dstid == 30))) " +
                "or (((srcid == 31) and (mrkt == '32')) and (dstid == 33))) " +
                "or (((srcid == 34) and (mrkt == '35')) and (dstid == 36))) " +
                "or (((srcid == 37) and (mrkt == '38')) and (dstid == 39))) " +
                "or (((srcid == 40) and (mrkt == '41')) and (dstid == 42))) " +
                "or (((srcid == 43) and (mrkt == '44')) and (dstid == 45))) " +
                "or (((srcid == 46) and (mrkt == '47')) and (dstid == 48))) " +
                "or (((srcid == 49) and (mrkt == '50')) and (dstid == 51))) " +
                "or (((srcid == 52) and (mrkt == '53')) and (dstid == 54))) " +
                "or (((srcid == 55) and (mrkt == '56')) and (dstid == 57))) " +
                "or (((srcid == 58) and (mrkt == '59')) and (dstid == 60))) " +
                "or (((srcid == 61) and (mrkt == '62')) and (dstid == 63))) " +
                "or (((srcid == 64) and (mrkt == '65')) and (dstid == 66))) " +
                "or (((srcid == 67) and (mrkt == '68')) and (dstid == 69)))",
                null);
    }

    // UDF expression should make the entire filter be rejected
    @Test
    public void testAndOrConditionMixedWithUdfExpr() throws Exception {
        String q = query + "b = filter a by " +
                "(UPPER(name) == 'FOO')" +
                "or (srcid == 1 and mrkt == '2' and dstid == 3) " +
                "or (srcid == 4 and mrkt == '5' and dstid == 6) " +
                "or (srcid == 7 and mrkt == '8' and dstid == 9);" +
                "store b into 'out';";
        negativeTest(q, Arrays.asList("srcid", "mrkt", "dstid"));
    }

    // Cast expression should make the entire filter be rejected
    @Test
    public void testAndOrConditionMixedWithCastExpr() throws Exception {
        String q = query + "b = filter a by " +
                "(srcid == 1 and mrkt == '2' and dstid == 3) " +
                "or (srcid == 4 and (int)mrkt == 5 and dstid == 6) " +
                "or (srcid == 7 and mrkt == '8' and dstid == 9);" +
                "store b into 'out';";
        negativeTest(q, Arrays.asList("srcid", "mrkt", "dstid"));
    }

    // Null expression should make the entire filter be rejected
    @Test
    public void testAndOrConditionMixedWithNullExpr() throws Exception {
        String q = query + "b = filter a by " +
                "(srcid == 1 and mrkt == '2' and dstid == 3) " +
                "or (srcid == 4 and mrkt == '5' and dstid == 6) " +
                "or (srcid == 7 and mrkt == '8' and dstid == 9) " +
                "or (name is null);" +
                "store b into 'out';";
        negativeTest(q, Arrays.asList("srcid", "mrkt", "dstid"));
    }

    //// helper methods ///////

    private PColFilterExtractor test(String query, List<String> partitionCols,
            String expPartFilterString) throws Exception {
        return test(query, partitionCols, expPartFilterString, null, true);
    }

    private PColFilterExtractor test(String query, List<String> partitionCols,
            String expPartFilterString, String expFilterString) throws Exception {
        return test(query, partitionCols, expPartFilterString, expFilterString, false);
    }

    private PColFilterExtractor test(String query, List<String> partitionCols,
            String expPartFilterString, String expFilterString, boolean skipTrimmedFilterCheck)
    throws Exception {
        PigServer pigServer = new PigServer( pc );
        LogicalPlan newLogicalPlan = Util.buildLp(pigServer, query);
        Operator op = newLogicalPlan.getSinks().get(0);
        LOFilter filter = (LOFilter)newLogicalPlan.getPredecessors(op).get(0);
        PColFilterExtractor pColExtractor = new PColFilterExtractor(
                filter.getFilterPlan(), partitionCols);
        pColExtractor.visit();

        if(expPartFilterString == null) {
            Assert.assertEquals("Checking partition column filter:", null,
                    pColExtractor.getPColCondition());
        } else  {
            Assert.assertEquals("Checking partition column filter:",
                    expPartFilterString,
                    pColExtractor.getPColCondition().toString());
        }

        // The getExpression() in PColFilterExtractor was written to get the
        // pushdown filter expression and does not have support for columns of
        // type tuple or map as partition columns are expected to be of
        // primitive data type. But we are using the method in the tests for forming
        // trimmed filter after pushdown. So skip check in cases where we expect a
        // trimmed filter to have a map or tuple in the condition.
        if (!skipTrimmedFilterCheck) {
            if (expFilterString == null) {
                Assert.assertTrue("Check that filter can be removed:",
                        pColExtractor.isFilterRemovable());
            } else {
                String actual = pColExtractor
                        .getExpression(
                                (LogicalExpression) filter.getFilterPlan().getSources().get(0))
                        .toString();
                Assert.assertEquals("checking trimmed filter expression:", expFilterString, actual);
            }
        }
        return pColExtractor;
    }

    // The filter cannot be pushed down unless it meets certain conditions. In
    // that case, PColExtractor.getPColCondition() should return null.
    private void negativeTest(String query, List<String> partitionCols) throws Exception {
        PigServer pigServer = new PigServer( pc );
        LogicalPlan newLogicalPlan = Util.buildLp(pigServer, query);
        Operator op = newLogicalPlan.getSinks().get(0);
        LOFilter filter = (LOFilter)newLogicalPlan.getPredecessors(op).get(0);
        PColFilterExtractor pColExtractor = new PColFilterExtractor(
                filter.getFilterPlan(), partitionCols);
        pColExtractor.visit();
        Assert.assertFalse(pColExtractor.canPushDown());
        Assert.assertNull(pColExtractor.getPColCondition());
    }

    private void negativeTest(String query, List<String> partitionCols,
            int expectedErrorCode) throws Exception {
        PigServer pigServer = new PigServer( pc );
        LogicalPlan newLogicalPlan = Util.buildLp(pigServer, query);
        Operator op = newLogicalPlan.getSinks().get(0);
        LOFilter filter = (LOFilter)newLogicalPlan.getPredecessors(op).get(0);
        PColFilterExtractor pColExtractor = new PColFilterExtractor(
                filter.getFilterPlan(), partitionCols);
        try {
            pColExtractor.visit();
        } catch(Exception e) {
            Assert.assertEquals("Checking if exception has right error code",
                    expectedErrorCode, LogUtils.getPigException(e).getErrorCode());
            return;
        }
    }

    /**
     * this loader is only used to test that parition column filters are given
     * in the manner expected in terms of column names - hence it does not
     * implement many of the methods and only implements required ones.
     */
    public static class TestLoader extends LoadFunc implements LoadMetadata {

        Schema schema;
        String[] partCols;
        static Expression partFilter = null;

        public TestLoader(String schemaString, String commaSepPartitionCols)
        throws ParserException {
            schema = Utils.getSchemaFromString(schemaString);
            partCols = commaSepPartitionCols.split(",");
        }

        @Override
        public InputFormat getInputFormat() throws IOException {
            return null;
        }

        @Override
        public Tuple getNext() throws IOException {
            return null;
        }

        @Override
        public void prepareToRead(RecordReader reader, PigSplit split)
        throws IOException {
        }

        @Override
        public void setLocation(String location, Job job) throws IOException {
        }

        @Override
        public String[] getPartitionKeys(String location, Job job)
        throws IOException {
            return partCols;
        }

        @Override
        public ResourceSchema getSchema(String location, Job job)
        throws IOException {
            return new ResourceSchema(schema);
        }

        @Override
        public ResourceStatistics getStatistics(String location,
                Job job) throws IOException {
            return null;
        }

        @Override
        public void setPartitionFilter(Expression partitionFilter)
        throws IOException {
            partFilter = partitionFilter;
        }

    }

    public class MyPlanOptimizer extends LogicalPlanOptimizer {
        protected MyPlanOptimizer(OperatorPlan p,  int iterations) {
            super( p, iterations, new HashSet<String>() );
        }

        protected List<Set<Rule>> buildRuleSets() {
            List<Set<Rule>> ls = new ArrayList<Set<Rule>>();

            Set<Rule> s = new HashSet<Rule>();
            // add split filter rule
            Rule r = new PartitionFilterOptimizer("PartitionFilterPushDown");
            s = new HashSet<Rule>();
            s.add(r);
            ls.add(s);

            r = new LoadTypeCastInserter( "LoadTypeCastInserter" );
            s = new HashSet<Rule>();
            s.add(r);
            ls.add(s);
            return ls;
        }
    }

    // Helper Functions
    public LogicalPlan buildPlan(PigServer pigServer, String query) throws Exception {
    	try {
            return Util.buildLp(pigServer, query);
    	} catch(Throwable t) {
    		throw new AssertionFailedError(t.getMessage());
    	}
    }

}
