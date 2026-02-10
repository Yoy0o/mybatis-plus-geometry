package io.github.yoy0o.mybatis.geometry.strategy;

import io.github.yoy0o.mybatis.geometry.exception.GeometryConversionException;
import io.github.yoy0o.mybatis.geometry.util.WkbUtil;
import org.apache.commons.codec.binary.Hex;
import org.locationtech.jts.geom.Geometry;

/**
 * PostgreSQL/PostGIS-specific geometry handling strategy.
 * Uses hex WKB format for geometry data exchange.
 * 
 * <p><strong>INSERT/UPDATE Operations:</strong></p>
 * <ul>
 *   <li>Converts JTS Geometry to hex WKB string</li>
 *   <li>Uses ps.setObject(hexString) to pass data</li>
 *   <li>PostGIS directly recognizes hex WKB format without ST functions</li>
 *   <li>Example: '0101000000000000000000F03F000000000000F03F' for POINT(1 1)</li>
 * </ul>
 * 
 * <p><strong>SELECT Operations:</strong></p>
 * <ul>
 *   <li>Uses encode(ST_AsBinary(column), 'hex') to read geometry as hex string</li>
 *   <li>Returns standard WKB format (not EWKB)</li>
 *   <li>Compatible with WkbUtil parser</li>
 * </ul>
 * 
 * <p><strong>Advantages:</strong></p>
 * <ul>
 *   <li>No need for ST_GeomFromWKB or ST_GeomFromText functions</li>
 *   <li>Simplified SQL generation</li>
 *   <li>Better performance (reduced function call overhead)</li>
 *   <li>No additional PostGIS JDBC dependencies required</li>
 * </ul>
 */
public class PostGISGeometryStrategy implements GeometryHandlerStrategy {
    
    @Override
    public DatabaseType getSupportedDatabaseType() {
        return DatabaseType.POSTGRESQL;
    }
    
    @Override
    public String wrapColumnForSelect(String columnName) {
        // PostGIS: Use encode(ST_AsBinary()) to convert geometry to hex WKB string
        // ST_AsBinary() returns standard WKB format (not EWKB)
        // encode(..., 'hex') converts binary to hexadecimal string
        return "encode(ST_AsBinary(" + columnName + "), 'hex') AS " + columnName;
    }
    
    @Override
    public String getGeometryInputFunction() {
        // PostGIS can directly accept hex WKB string without ST function wrapping
        // The geometry column automatically recognizes hex WKB format
        // Example hex WKB: '0101000000000000000000F03F000000000000F03F' represents POINT(1 1)
        // No need for ST_GeomFromWKB(decode(?, 'hex')) wrapper
        return "?";
    }
    
    @Override
    public Object convertForDatabase(Geometry geometry) {
        if (geometry == null) {
            return null;
        }
        // Convert JTS Geometry to WKB bytes, then to hex string
        // PostgreSQL geometry columns can directly accept hex WKB string format
        byte[] wkbBytes = WkbUtil.toWkbBytes(geometry);
        return Hex.encodeHexString(wkbBytes);
    }
    
    @Override
    public Geometry parseFromDatabase(Object dbValue) {
        if (dbValue == null) {
            return null;
        }
        
        if (dbValue instanceof String hexString) {
            // Parse hex WKB string returned from encode(ST_AsBinary())
            // WkbUtil expects format: SRID(4 bytes) + standard WKB
            return WkbUtil.fromWkb(hexString);
        }
        
        throw new GeometryConversionException(
            "Unexpected database value type",
            "Geometry",
            dbValue.getClass().getName()
        );
    }
}
