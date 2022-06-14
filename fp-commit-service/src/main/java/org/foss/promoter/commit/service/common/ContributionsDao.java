/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.foss.promoter.commit.service.common;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.SimpleStatement;
import com.datastax.oss.driver.api.core.type.DataTypes;
import com.datastax.oss.driver.api.querybuilder.QueryBuilder;
import com.datastax.oss.driver.api.querybuilder.SchemaBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContributionsDao {
    public static final String KEY_SPACE = "fp";
    public static final String TABLE_NAME = "contributions";

    private static final Logger LOG = LoggerFactory.getLogger(ContributionsDao.class);

    private final CqlSession session;

    public ContributionsDao(CqlSession session) {
        this.session = session;
    }

    public void createKeySpace() {
        Map<String, Object> replication = new HashMap<>();

        replication.put("class", "SimpleStrategy");
        replication.put("replication_factor", 3);

        String statement = SchemaBuilder.createKeyspace(KEY_SPACE)
                .ifNotExists()
                .withReplicationOptions(replication)
                .asCql();

        LOG.info("Executing {}", statement);

        ResultSet rs = session.execute(statement);

        if (!rs.wasApplied()) {
            LOG.warn("The create key space statement did not execute");
        }
    }

    public void useKeySpace() {
        // Use String.format because "Bind variables cannot be used for keyspace names"
        String statement = String.format("USE %s", KEY_SPACE);

        session.execute(statement);
    }

    public void createTable() {
        SimpleStatement statement = SchemaBuilder.createTable(TABLE_NAME)
                .ifNotExists()
                .withPartitionKey("id", DataTypes.TIMEUUID)
                .withClusteringColumn("text", DataTypes.TEXT)
                .withColumn("qr_code", DataTypes.BLOB)
                .builder()
                .setTimeout(Duration.ofSeconds(10)).build();

        LOG.info("Executing create table {}", statement);

        ResultSet rs = session.execute(statement);
        if (!rs.wasApplied()) {
            LOG.warn("The create table statement did not execute");
        }
    }

    public static String getInsertStatement() {
        SimpleStatement statement = QueryBuilder
                .insertInto(TABLE_NAME)
                .value("id", QueryBuilder.now())
                .value("text", QueryBuilder.bindMarker())
                .value("qr_code", QueryBuilder.bindMarker())
                .build();

        return statement.getQuery();
    }

    public static String getSelectStatement(int limitSize) {
        SimpleStatement statement = QueryBuilder
                .selectFrom(TABLE_NAME)
                .toDate("id").as("insertion_date")
                .column("text")
                .column("qr_code")
                .limit(limitSize)
                .build();


        return statement.getQuery();
    }
}
