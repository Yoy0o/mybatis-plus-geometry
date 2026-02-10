package io.github.yoy0o.mybatis.geometry.interceptor;

import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import io.github.yoy0o.mybatis.geometry.annotation.LineStringTableField;
import io.github.yoy0o.mybatis.geometry.annotation.PointTableField;
import io.github.yoy0o.mybatis.geometry.annotation.PolygonTableField;
import io.github.yoy0o.mybatis.geometry.strategy.GeometryHandlerStrategy;
import io.github.yoy0o.mybatis.geometry.strategy.GeometryStrategyFactory;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * MyBatis interceptor to automatically wrap geometry fields with HEX() function in SELECT queries.
 * 
 * <p>This interceptor detects geometry fields (annotated with @PointTableField, @PolygonTableField,
 * or @LineStringTableField) and wraps them with the appropriate database function for reading.</p>
 */
@Intercepts({
    @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
})
public class GeometryFieldInterceptor implements Interceptor {
    
    private static final Logger log = LoggerFactory.getLogger(GeometryFieldInterceptor.class);
    
    /** Cache for entity geometry fields */
    private static final Map<Class<?>, Set<String>> GEOMETRY_FIELDS_CACHE = new ConcurrentHashMap<>();
    
    /** Cache for entity all fields */
    private static final Map<Class<?>, List<String>> ALL_FIELDS_CACHE = new ConcurrentHashMap<>();
    
    /** Pattern to match FROM clause */
    private static final Pattern FROM_PATTERN = Pattern.compile(
        "(?i)FROM\\s+`?([a-zA-Z_][a-zA-Z0-9_]*)`?(?:\\s+(?:AS\\s+)?([a-zA-Z_][a-zA-Z0-9_]*))?",
        Pattern.CASE_INSENSITIVE
    );
    
    /** Pattern to match SELECT clause */
    private static final Pattern SELECT_PATTERN = Pattern.compile(
        "(?i)SELECT\\s+(.*?)\\s+FROM",
        Pattern.DOTALL
    );
    
    private final GeometryHandlerStrategy strategy;
    
    /**
     * Create interceptor with default strategy.
     */
    public GeometryFieldInterceptor() {
        this(GeometryStrategyFactory.getDefaultStrategy());
    }
    
    /**
     * Create interceptor with specified strategy.
     *
     * @param strategy the geometry handler strategy
     */
    public GeometryFieldInterceptor(GeometryHandlerStrategy strategy) {
        this.strategy = strategy;
    }
    
    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        StatementHandler statementHandler = PluginUtils.realTarget(invocation.getTarget());
        MetaObject metaObject = SystemMetaObject.forObject(statementHandler);
        
        MappedStatement mappedStatement = (MappedStatement) metaObject.getValue("delegate.mappedStatement");
        
        // Only process SELECT queries
        if (mappedStatement.getSqlCommandType() != SqlCommandType.SELECT) {
            return invocation.proceed();
        }
        
        BoundSql boundSql = statementHandler.getBoundSql();
        String originalSql = boundSql.getSql();
        
        String processedSql = processGeometryFields(originalSql, mappedStatement);
        
        if (!originalSql.equals(processedSql)) {
            log.debug("Original SQL: {}", originalSql);
            log.debug("Processed SQL: {}", processedSql);
            metaObject.setValue("delegate.boundSql.sql", processedSql);
        }
        
        return invocation.proceed();
    }
    
    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }
    
    @Override
    public void setProperties(Properties properties) {
        // No properties needed
    }

    
    private String processGeometryFields(String sql, MappedStatement mappedStatement) {
        try {
            Class<?> entityClass = getEntityClass(mappedStatement);
            if (entityClass == null) {
                return sql;
            }
            
            Set<String> geometryFields = getGeometryFields(entityClass);
            if (geometryFields.isEmpty()) {
                return sql;
            }
            
            return wrapGeometryFieldsWithHex(sql, geometryFields, entityClass);
            
        } catch (Exception e) {
            log.warn("Failed to process geometry fields in SQL: {}", e.getMessage());
            return sql;
        }
    }
    
    private Class<?> getEntityClass(MappedStatement mappedStatement) {
        String id = mappedStatement.getId();
        
        try {
            String mapperClassName = id.substring(0, id.lastIndexOf('.'));
            Class<?> mapperClass = Class.forName(mapperClassName);
            return getEntityClassFromMapper(mapperClass);
        } catch (Exception e) {
            log.debug("Could not determine entity class for: {}", id);
            return null;
        }
    }
    
    private Class<?> getEntityClassFromMapper(Class<?> mapperClass) {
        try {
            for (java.lang.reflect.Type genericInterface : mapperClass.getGenericInterfaces()) {
                if (genericInterface instanceof java.lang.reflect.ParameterizedType parameterizedType) {
                    if (parameterizedType.getRawType().getTypeName().contains("BaseMapper")) {
                        java.lang.reflect.Type[] typeArguments = parameterizedType.getActualTypeArguments();
                        if (typeArguments.length > 0 && typeArguments[0] instanceof Class<?> entityClass) {
                            return entityClass;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to extract entity class from mapper: {}", e.getMessage());
        }
        return null;
    }
    
    private Set<String> getGeometryFields(Class<?> entityClass) {
        return GEOMETRY_FIELDS_CACHE.computeIfAbsent(entityClass, clazz -> {
            Set<String> fields = new HashSet<>();
            
            Class<?> currentClass = clazz;
            while (currentClass != null && currentClass != Object.class) {
                for (Field field : currentClass.getDeclaredFields()) {
                    if (isGeometryField(field)) {
                        String columnName = getColumnName(field);
                        fields.add(columnName);
                    }
                }
                currentClass = currentClass.getSuperclass();
            }
            
            log.debug("Cached geometry fields for {}: {}", clazz.getSimpleName(), fields);
            return fields;
        });
    }
    
    private boolean isGeometryField(Field field) {
        return field.isAnnotationPresent(PointTableField.class) ||
               field.isAnnotationPresent(PolygonTableField.class) ||
               field.isAnnotationPresent(LineStringTableField.class);
    }
    
    private String getColumnName(Field field) {
        com.baomidou.mybatisplus.annotation.TableField tableField = 
            field.getAnnotation(com.baomidou.mybatisplus.annotation.TableField.class);
        
        if (tableField != null && !tableField.value().isEmpty()) {
            return tableField.value();
        }
        
        return camelToSnake(field.getName());
    }
    
    private String camelToSnake(String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    
    private String wrapGeometryFieldsWithHex(String sql, Set<String> geometryFields, Class<?> entityClass) {
        // Check if this is SELECT *
        if (sql.matches("(?i).*SELECT\\s+\\*\\s+FROM.*")) {
            log.debug("Detected SELECT * query, expanding to explicit columns");
            return expandSelectStar(sql, geometryFields, entityClass);
        }
        
        return wrapExplicitColumns(sql, geometryFields);
    }
    
    private String expandSelectStar(String sql, Set<String> geometryFields, Class<?> entityClass) {
        try {
            List<String> allFields = getAllFields(entityClass);
            if (allFields.isEmpty()) {
                log.debug("No fields found for entity, skipping expansion");
                return sql;
            }
            
            Matcher matcher = FROM_PATTERN.matcher(sql);
            String tableAlias = null;
            if (matcher.find()) {
                tableAlias = matcher.group(2);
            }
            
            List<String> columnList = new ArrayList<>();
            String prefix = tableAlias != null ? tableAlias + "." : "";
            
            for (String fieldName : allFields) {
                if (geometryFields.contains(fieldName)) {
                    // Use strategy to wrap geometry column
                    String wrappedColumn = strategy.wrapColumnForSelect(prefix + fieldName);
                    // Extract alias from wrapped column or use fieldName
                    if (wrappedColumn.toUpperCase().contains(" AS ")) {
                        columnList.add(wrappedColumn);
                    } else {
                        columnList.add(wrappedColumn + " AS " + fieldName);
                    }
                } else {
                    columnList.add(prefix + fieldName);
                }
            }
            
            String columnListStr = String.join(", ", columnList);
            String result = sql.replaceFirst(
                "(?i)SELECT\\s+\\*\\s+FROM",
                "SELECT " + columnListStr + " FROM"
            );
            
            log.debug("Expanded SELECT * to: {}", columnListStr);
            return result;
            
        } catch (Exception e) {
            log.warn("Failed to expand SELECT *, falling back to original SQL: {}", e.getMessage());
            return sql;
        }
    }
    
    private List<String> getAllFields(Class<?> entityClass) {
        return ALL_FIELDS_CACHE.computeIfAbsent(entityClass, clazz -> {
            List<String> fields = new ArrayList<>();
            
            Class<?> currentClass = clazz;
            while (currentClass != null && currentClass != Object.class) {
                for (Field field : currentClass.getDeclaredFields()) {
                    if (java.lang.reflect.Modifier.isStatic(field.getModifiers()) ||
                        java.lang.reflect.Modifier.isTransient(field.getModifiers())) {
                        continue;
                    }
                    
                    com.baomidou.mybatisplus.annotation.TableField tableField = 
                        field.getAnnotation(com.baomidou.mybatisplus.annotation.TableField.class);
                    if (tableField != null && !tableField.exist()) {
                        continue;
                    }
                    
                    String columnName = getColumnName(field);
                    if (!fields.contains(columnName)) {
                        fields.add(columnName);
                    }
                }
                currentClass = currentClass.getSuperclass();
            }
            
            log.debug("Cached all fields for {}: {}", clazz.getSimpleName(), fields);
            return fields;
        });
    }
    
    private String wrapExplicitColumns(String sql, Set<String> geometryFields) {
        Matcher matcher = SELECT_PATTERN.matcher(sql);
        if (!matcher.find()) {
            return sql;
        }
        
        String selectClause = matcher.group(1);
        String beforeSelect = sql.substring(0, matcher.start(1));
        String afterSelect = sql.substring(matcher.end(1));
        
        String[] columns = selectClause.split(",");
        List<String> processedColumns = new ArrayList<>();
        
        for (String column : columns) {
            String trimmedColumn = column.trim();
            String processedColumn = processColumnForGeometry(trimmedColumn, geometryFields);
            processedColumns.add(processedColumn);
        }
        
        return beforeSelect + String.join(", ", processedColumns) + afterSelect;
    }
    
    private String processColumnForGeometry(String column, Set<String> geometryFields) {
        String upperColumn = column.toUpperCase();
        
        // Skip if already wrapped with function
        if (upperColumn.contains("HEX(") || upperColumn.contains("ENCODE(") || 
            upperColumn.contains("ST_ASBINARY(") || upperColumn.contains("COUNT(") || 
            upperColumn.contains("SUM(") || upperColumn.contains("AVG(")) {
            return column;
        }
        
        String columnName = extractColumnName(column);
        String snakeCaseColumnName = camelToSnake(columnName);
        
        if (geometryFields.contains(columnName) || geometryFields.contains(snakeCaseColumnName)) {
            if (column.toLowerCase().contains(" as ")) {
                String[] parts = column.split("(?i)\\s+as\\s+", 2);
                String colPart = parts[0].trim();
                String aliasPart = parts[1].trim();
                // Use strategy to wrap geometry column
                String wrappedColumn = strategy.wrapColumnForSelect(colPart);
                // Extract the function part (before AS if exists)
                if (wrappedColumn.toUpperCase().contains(" AS ")) {
                    wrappedColumn = wrappedColumn.substring(0, wrappedColumn.toUpperCase().indexOf(" AS ")).trim();
                }
                return wrappedColumn + " AS " + aliasPart;
            } else {
                // Use strategy to wrap geometry column
                return strategy.wrapColumnForSelect(column.trim());
            }
        }
        
        return column;
    }
    
    private String extractColumnName(String column) {
        String name = column.trim();
        
        if (name.toLowerCase().contains(" as ")) {
            name = name.split("(?i)\\s+as\\s+", 2)[0].trim();
        }
        
        if (name.contains(".")) {
            name = name.substring(name.lastIndexOf('.') + 1);
        }
        
        name = name.replace("`", "").replace("\"", "").replace("'", "");
        
        return name.trim();
    }
    
    /**
     * Clear the field caches.
     * Useful for testing or reconfiguration.
     */
    public static void clearCache() {
        GEOMETRY_FIELDS_CACHE.clear();
        ALL_FIELDS_CACHE.clear();
    }
}
