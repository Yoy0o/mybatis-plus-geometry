package io.github.yoy0o.mybatis.geometry.handler;

import io.github.yoy0o.mybatis.geometry.strategy.GeometryHandlerStrategy;
import io.github.yoy0o.mybatis.geometry.strategy.GeometryStrategyFactory;
import io.github.yoy0o.mybatis.geometry.util.WkbUtil;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.locationtech.jts.geom.Geometry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Abstract base class for geometry TypeHandlers.
 * Provides common functionality for converting JTS geometry objects to/from database GEOMETRY columns.
 * 
 * <p><strong>Database-Specific Handling:</strong></p>
 * <ul>
 *   <li><strong>MySQL:</strong> Uses ps.setBytes() with WKB binary data</li>
 *   <li><strong>PostgreSQL:</strong> Uses ps.setObject() with hex WKB string</li>
 * </ul>
 * 
 * <p><strong>Write Operations (INSERT/UPDATE):</strong></p>
 * <ol>
 *   <li>Validate geometry object</li>
 *   <li>Ensure SRID is set (default: 4326)</li>
 *   <li>Convert using database-specific strategy</li>
 *   <li>Set parameter based on database type</li>
 * </ol>
 * 
 * <p><strong>Read Operations (SELECT):</strong></p>
 * <ul>
 *   <li>Reads hex WKB string from ResultSet</li>
 *   <li>Parses using WkbUtil</li>
 *   <li>Returns typed JTS Geometry object</li>
 * </ul>
 *
 * @param <T> the specific geometry type (Point, Polygon, LineString)
 */
public abstract class AbstractGeometryTypeHandler<T extends Geometry> extends BaseTypeHandler<T> {
    
    protected final Logger log = LoggerFactory.getLogger(getClass());
    
    /** Default SRID to use when geometry has no SRID set */
    protected final int defaultSrid;
    
    /** Geometry handler strategy for database-specific operations */
    protected final GeometryHandlerStrategy strategy;
    
    /**
     * Create a new AbstractGeometryTypeHandler with default SRID.
     */
    protected AbstractGeometryTypeHandler() {
        this(WkbUtil.DEFAULT_SRID);
    }
    
    /**
     * Create a new AbstractGeometryTypeHandler with specified default SRID.
     *
     * @param defaultSrid the default SRID to use
     */
    protected AbstractGeometryTypeHandler(int defaultSrid) {
        this.defaultSrid = defaultSrid;
        this.strategy = GeometryStrategyFactory.getDefaultStrategy();
    }
    
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, T parameter, JdbcType jdbcType) 
            throws SQLException {
        if (parameter == null) {
            throw new SQLException("Parameter cannot be null");
        }
        
        try {
            // Validate geometry
            validateGeometry(parameter);
            
            // Ensure SRID is set
            ensureSrid(parameter);
            
            // Convert using database-specific strategy
            Object dbValue = strategy.convertForDatabase(parameter);
            
            if (log.isDebugEnabled()) {
                log.debug("{} converted to database format: {}", getGeometryTypeName(), 
                    dbValue.getClass().getSimpleName());
            }
            
            // Set parameter based on type
            if (dbValue instanceof byte[]) {
                // MySQL: binary data
                ps.setBytes(i, (byte[]) dbValue);
            } else if (dbValue instanceof String) {
                // PostgreSQL: hex WKB string
                // PostGIS geometry field can directly accept hex string format
                // Using setObject is recommended for better type handling
                ps.setObject(i, (String) dbValue);
            } else {
                throw new SQLException("Unsupported database value type: " + dbValue.getClass().getName());
            }
            
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error converting {} to database format: {}", getGeometryTypeName(), e.getMessage());
            throw new SQLException("Failed to convert " + getGeometryTypeName() + " to database format: " + 
                e.getMessage(), e);
        }
    }
    
    @Override
    public T getNullableResult(ResultSet rs, String columnName) throws SQLException {
        try {
            String hexString = rs.getString(columnName);
            return parseGeometry(hexString);
        } catch (Exception e) {
            log.error("Error reading {} from WKB: {}", getGeometryTypeName(), e.getMessage());
            throw new SQLException("Failed to read " + getGeometryTypeName() + " from WKB data", e);
        }
    }
    
    @Override
    public T getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        try {
            String hexString = rs.getString(columnIndex);
            return parseGeometry(hexString);
        } catch (Exception e) {
            log.error("Error reading {} from WKB: {}", getGeometryTypeName(), e.getMessage());
            throw new SQLException("Failed to read " + getGeometryTypeName() + " from WKB data", e);
        }
    }
    
    @Override
    public T getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        try {
            String hexString = cs.getString(columnIndex);
            return parseGeometry(hexString);
        } catch (Exception e) {
            log.error("Error reading {} from WKB: {}", getGeometryTypeName(), e.getMessage());
            throw new SQLException("Failed to read " + getGeometryTypeName() + " from WKB data", e);
        }
    }
    
    /**
     * Parse WKB hex string to geometry object.
     *
     * @param hexString the WKB hex string
     * @return the parsed geometry, or null if input is null/empty
     */
    protected abstract T parseGeometry(String hexString);
    
    /**
     * Validate the geometry object.
     *
     * @param geometry the geometry to validate
     * @throws SQLException if geometry is invalid
     */
    protected abstract void validateGeometry(T geometry) throws SQLException;
    
    /**
     * Get the geometry type name for logging.
     *
     * @return the geometry type name
     */
    protected abstract String getGeometryTypeName();
    
    /**
     * Ensure the geometry has a valid SRID.
     * If SRID is 0, set it to the default SRID.
     *
     * @param geometry the geometry to check
     */
    protected void ensureSrid(Geometry geometry) {
        if (geometry.getSRID() == 0) {
            geometry.setSRID(defaultSrid);
        }
    }
}
