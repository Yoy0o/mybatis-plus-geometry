package io.github.yoy0o.mybatis.geometry.handler;

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
 * @param <T> the specific geometry type (Point, Polygon, LineString)
 */
public abstract class AbstractGeometryTypeHandler<T extends Geometry> extends BaseTypeHandler<T> {
    
    protected final Logger log = LoggerFactory.getLogger(getClass());
    
    /** Default SRID to use when geometry has no SRID set */
    protected final int defaultSrid;
    
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
            
            // Convert to WKB bytes
            byte[] wkbBytes = WkbUtil.toWkbBytes(parameter);
            
            if (log.isDebugEnabled()) {
                log.debug("{} WKB bytes length: {}", getGeometryTypeName(), wkbBytes.length);
            }
            
            // Set as binary data for database GEOMETRY field
            ps.setBytes(i, wkbBytes);
            
        } catch (SQLException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error converting {} to WKB: {}", getGeometryTypeName(), e.getMessage());
            throw new SQLException("Failed to convert " + getGeometryTypeName() + " to WKB format: " + 
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
