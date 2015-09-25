package org.smart4j.chapter2.helper;

import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.handlers.BeanHandler;
import org.apache.commons.dbutils.handlers.BeanListHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.slf4j.Logger;
import org.smart4j.chapter2.util.CollectionUtil;
import org.smart4j.chapter2.util.PropsUtil;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Created by Ckai on 15/9/24.
 */
public final class DatabaseHelper {
    private static final Logger LOGGER = org.slf4j.LoggerFactory.getLogger(DatabaseHelper.class);
    private static final String DRIVER;
    private static final String URL;
    private static final String USERNAME;
    private static final String PASSWORD;

    //Connection放入ThreadLocal中，确保一个线程中只有一个Connection
    private static final ThreadLocal<Connection> CONNECTION_HOLDER = new ThreadLocal<Connection>();
    private static final QueryRunner QUERY_RUNNER = new QueryRunner();

    static {
        Properties conf = PropsUtil.loadProps("config.properties");
        DRIVER = conf.getProperty("jdbc.driver");
        URL = conf.getProperty("jdbc.url");
        USERNAME = conf.getProperty("jdbc.username");
        PASSWORD = conf.getProperty("jdbc.password");

        try{
            Class.forName(DRIVER);
        }catch (ClassNotFoundException e){
            LOGGER.error("can not load jdbc driver",e);
        }
    }

    /**
     * 查询实体列表
     */
    public static <T> List<T> queryEntityList(Class<T> entityClass,String sql,Object... params){
        List<T> entityList;

        try {
            Connection conn = getConnection();
            entityList = QUERY_RUNNER.query(conn,sql,new BeanListHandler<T>(entityClass),params);
        }catch (SQLException e){
            LOGGER.error("query entity list failure",e);
            throw new RuntimeException(e);
        }finally {
            closeConnection();
        }

        return entityList;
    }

    /**
     * 查询实体
     */
    public static <T> T queryEntity(Class<T> entityClass,String sql,Object... params){
        T entity;

        try{
            Connection conn = getConnection();
            entity = QUERY_RUNNER.query(conn,sql,new BeanHandler<T>(entityClass),params);
        }catch (SQLException e){
            LOGGER.error("query entity failure",e);
            throw new RuntimeException(e);
        }finally {
            closeConnection();
        }

        return entity;
    }

    /**
     * 根据类名获取表名
     */
    private static String getTableName(Class<?> entityClass){
        return entityClass.getSimpleName();
    }

    /**
     * 插入实体
     */
    public static <T> boolean insertEntity(Class<T> entityClass,Map<String,Object> fieldMap){
        if(CollectionUtil.isEmpty(fieldMap)){
            LOGGER.error("cannot insert entity: fieldMap is empty");
            return false;
        }

        String sql = "INSERT INTO " + getTableName(entityClass);
        StringBuilder columns = new StringBuilder("(");
        StringBuilder values = new StringBuilder("(");

        for (String fieldName : fieldMap.keySet()){
            columns.append(fieldName).append(", ");
            values.append("?, ");
        }

        columns.replace(columns.lastIndexOf(","),columns.length(),")");
        values.replace(values.lastIndexOf(","),values.length(),")");
        sql += columns + "VALUES " + values;
        Object[] params = fieldMap.values().toArray();

        return executeUpdate(sql,params) == 1;
    }

    /**
     * 更新实体
     */
    public static <T> boolean updateEntity(Class<T> entityClass,long id,Map<String,Object> fieldMap){
        if(CollectionUtil.isEmpty(fieldMap)){
            LOGGER.error("cannot update entity; fieldMap is empty");
            return false;
        }

        String sql = "UPDATE " + getTableName(entityClass) + " SET ";
        StringBuilder columns = new StringBuilder();

        for(String fieldName : fieldMap.keySet()){
            columns.append(fieldName).append("=?, ");
        }

        sql += columns.substring(0,columns.indexOf(", ")) + " WHERE id = ?";
        List<Object> paramList = new ArrayList<Object>();
        paramList.addAll(fieldMap.values());
        paramList.add(id);
        Object[] params = paramList.toArray();

        return executeUpdate(sql,params) == 1;
    }

    /**
     * 删除实体
     */
    public static <T> boolean deleteEntity(Class<T> entityClass,long id){
        String sql = "DELETE FROM " + getTableName(entityClass) + " WHERE id = ?";
        return executeUpdate(sql,id) == 1;
    }

    /**
     * 执行查询语句
     */
    public static List<Map<String,Object>> executeQuery(String sql,Object... params){
        List<Map<String,Object>> result;

        try{
            Connection conn = getConnection();
            result = QUERY_RUNNER.query(conn, sql, new MapListHandler(), params);
        }catch (Exception e){
            LOGGER.error("execute query failure",e);
            throw new RuntimeException(e);
        }finally {
            closeConnection();
        }

        return result;
    }

    /**
     * 执行更新语句（包括update、insert、delete）
     */
    public static int executeUpdate(String sql,Object... params){
        int rows = 0;

        try{
            Connection conn = getConnection();
            rows = QUERY_RUNNER.update(conn,sql,params);
        }catch (SQLException e){
            LOGGER.error("execute update failure",e);
            throw new RuntimeException(e);
        }finally {
            closeConnection();
        }

        return rows;
    }

    /**
     * 获取数据库连接
     * V2.0 加入ThreadLocal
     */
    public static Connection getConnection()
    {
        Connection conn = CONNECTION_HOLDER.get();

        if(conn != null) {
            try {
                conn = DriverManager.getConnection(URL, USERNAME, PASSWORD);

            } catch (SQLException e) {
                LOGGER.error("get connection failure", e);
                throw new RuntimeException(e);
            }finally {
                CONNECTION_HOLDER.set(conn);
            }
        }

        return conn;
    }

    /**
     * 关闭数据库连接
     * V2.0 加入ThreadLocal
     */
    public static void closeConnection(){
        Connection conn = CONNECTION_HOLDER.get();
        if (conn != null){
            try {
                conn.close();
            }catch (SQLException e){
                LOGGER.error("close connection failure",e);
                throw new RuntimeException(e);
            }finally {
                CONNECTION_HOLDER.remove();
            }
        }
    }
}
