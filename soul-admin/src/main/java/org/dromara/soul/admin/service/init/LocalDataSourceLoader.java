/*
 *   Licensed to the Apache Software Foundation (ASF) under one or more
 *   contributor license agreements.  See the NOTICE file distributed with
 *   this work for additional information regarding copyright ownership.
 *   The ASF licenses this file to You under the Apache License, Version 2.0
 *   (the "License"); you may not use this file except in compliance with
 *   the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.dromara.soul.admin.service.init;

import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.jdbc.ScriptRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessor;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

/**
 * for execute schema sql file.
 *
 * 为了执行初始化sql脚本
 * InstantiationAwareBeanPostProcessor 用于类实例化阶段
 *
 * @author huangxiaofeng
 */
@Component
public class LocalDataSourceLoader implements InstantiationAwareBeanPostProcessor {
    
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalDataSourceLoader.class);
    
    private static final String SCHEMA_SQL_FILE = "META-INF/schema.sql";
    
    @Override
    public Object postProcessAfterInitialization(@NonNull final Object bean, final String beanName) throws BeansException {
        // 如果实例化的类是DataSourceProperties，即数据库连接配置已经实例化，就开始执行sql建表语句
        if (bean instanceof DataSourceProperties) {
            this.init((DataSourceProperties) bean);
        }
        return bean;
    }
    
    @SneakyThrows
    protected void init(final DataSourceProperties properties) {
        // If jdbcUrl in the configuration file specifies the soul database, it is removed,
        // because the soul database does not need to be specified when executing the SQL file,
        // otherwise the soul database will be disconnected when the soul database does not exist
        String jdbcUrl = StringUtils.replace(properties.getUrl(), "/soul?", "?");
        // 数据库连接
        Connection connection = DriverManager.getConnection(jdbcUrl, properties.getUsername(), properties.getPassword());
        this.execute(connection);
        
    }

    /**
     *  执行sql脚本
     *
     * @param conn
     * @throws Exception
     */
    private void execute(final Connection conn) throws Exception {
        ScriptRunner runner = new ScriptRunner(conn);
        // doesn't print logger
        runner.setLogWriter(null);
        Resources.setCharset(StandardCharsets.UTF_8);
        Reader read = Resources.getResourceAsReader(SCHEMA_SQL_FILE);
        LOGGER.info("execute soul schema sql: {}", SCHEMA_SQL_FILE);
        runner.runScript(read);
        runner.closeConnection();
        conn.close();
    }
    
}
