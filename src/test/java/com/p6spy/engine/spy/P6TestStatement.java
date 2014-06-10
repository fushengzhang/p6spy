/*
 * #%L
 * P6Spy
 * %%
 * Copyright (C) 2013 P6Spy
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */
package com.p6spy.engine.spy;

import com.p6spy.engine.logging.P6LogOptions;
import com.p6spy.engine.proxy.ProxyFactory;
import com.p6spy.engine.test.P6TestFramework;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class P6TestStatement extends P6TestFramework {

  public P6TestStatement(String db) throws SQLException, IOException {
    super(db);
  }

  @Test
  public void testExecute() throws SQLException {
    String query= "insert into customers(name,id) values ('bob', 100)";
    P6TestUtil.execute(connection, query);

    // validate logging
    assertTrue(super.getLastLogEntry().contains(query));

    // validate that the sql executed against the db
    assertEquals(1, P6TestUtil.queryForInt(connection, "select count(*) from customers where id=100"));
  }

  public void testExecuteUpdate() throws SQLException {
    String query= "update customers set name='xyz' where id=1";
    int rowCount = P6TestUtil.executeUpdate(connection, query);
    assertEquals(1, rowCount);

    // validate logging
    assertTrue(super.getLastLogEntry().contains(query));

    // validate that the sql executed against the db
    assertEquals(1, P6TestUtil.queryForInt(connection, "select count(*) from customers where id=100"));
  }

  @Test
  public void testExecuteBatch() throws SQLException {
    P6LogOptions.getActiveInstance().setExcludecategories("");
    // test batch inserts
    Statement stmt = connection.createStatement();
    String sql = "insert into customers(name,id) values ('jim', 101)";
    stmt.addBatch(sql);
    assertTrue(super.getLastLogEntry().contains(sql));
    assertTrue(super.getLastLogEntry().contains("|batch|"));
    sql = "insert into customers(name,id) values ('billy', 102)";
    stmt.addBatch(sql);
    assertTrue(super.getLastLogEntry().contains(sql));
    assertTrue(super.getLastLogEntry().contains("|batch|"));

    stmt.executeBatch();
    assertTrue(super.getLastLogEntry().contains(sql));

    assertEquals(2, P6TestUtil.queryForInt(connection,"select count(*) from customers where id > 100"));

    stmt.close();
  }
  
  @Test
  public void testExecuteEmptyBatch() throws SQLException {
    Statement stmt = connection.createStatement();
    stmt.executeBatch();
    stmt.close();
  }

  @Test
  public void testExecuteQuery() throws SQLException {
    Statement stmt = connection.createStatement();
    String query = "select count(*) from customers where id=1";
    ResultSet rs = stmt.executeQuery(query);

    // verify that we got back a proxy for the result set
    assertTrue("Resultset was not a proxy", ProxyFactory.isProxy(rs.getClass()));

    // verify statement logging
    assertTrue(super.getLastLogEntry().contains(query));

    rs.close();
    stmt.close();
  }

  @Test
  public void testExecutionThreshold() throws SQLException {
    Statement statement = connection.createStatement();

    try {
      // set the execution threshold very low
      P6LogOptions.getActiveInstance().setExecutionThreshold("0");

      // test a basic select
      String query = "select count(*) from customers";
      ResultSet rs = statement.executeQuery(query);
      assertTrue(super.getLastLogEntry().contains(query));
      // finally just make sure the query executed!
      rs.next();
      assertTrue(rs.getInt(1) > 0);
      rs.close();

      // now increase the execution threshold and make sure the query is not captured
      P6LogOptions.getActiveInstance().setExecutionThreshold("10000");

      // test a basic select
      String nextQuery = "select count(*) from customers where 1 = 2";
      rs = statement.executeQuery(nextQuery);
      // make sure the previous query is still the last query
      assertTrue(super.getLastLogEntry().contains(query));
      // and of course that the new query isn't
      assertFalse(super.getLastLogEntry().contains(nextQuery));
      // finally just make sure the query executed!
      rs.next();
      assertEquals(0, rs.getInt(1));
      rs.close();

      P6LogOptions.getActiveInstance().setExecutionThreshold("0");

      // finally, just make sure it now works as expected
      rs = statement.executeQuery(nextQuery);
      assertTrue(super.getLastLogEntry().contains(nextQuery));
      rs.next();
      assertEquals(0, rs.getInt(1));
      rs.close();
    } finally {
      if (statement != null) {
        statement.close();
      }
    }
  }

}
