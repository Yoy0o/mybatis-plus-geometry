package io.github.yoy0o.mybatis.geometry.strategy;

import io.github.yoy0o.mybatis.geometry.exception.GeometryConversionException;
import io.github.yoy0o.mybatis.geometry.util.WkbUtil;
import org.locationtech.jts.geom.Geometry;

/**
 * PostgreSQL/PostGIS-specific geometry handling strategy.
 * Uses EWKB format with PostGIS functions.
 * 
 * <p>Note: This is a placeholder implementation. Full PostGIS support
 * may require additional configuration and dependencies.</p>
 */
public class PostGISGeometryStrategy implements GeometryHandlerStrategy {
    
    @Override
    public DatabaseType getSupportedDatabaseType() {
        return DatabaseType.POSTGRESQL;
    }
    
    @Override
    public String wrapColumnForSelect(String columnName) {
        // PostGIS uses ST_AsHexEWKB to output geometry as hex EWKB
        return "ST_AsHexEWKB(" + columnName + ") AS " + columnName;
    }
    
    @Override
    public String getGeometryInputFunction() {
        // PostGIS uses ST_GeomFromEWKB to parse EWKB input
        return "ST_GeomFromEWKB(?)";
    }
    
    @Override
    public Object convertForDatabase(Geometry geometry) {
        if (geometry == null) {
            return null;
        }
        // PostGIS can accept WKB bytes directly with ST_GeomFromEWKB
        return WkbUtil.toWkbBytes(geometry);
    }
    
    @Override
    public Geometry parseFromDatabase(Object dbValue) {
        if (dbValue == null) {
            return null;
        }
        
        if (dbValue instanceof String hexString) {
            // PostGIS EWKB format is compatible with standard WKB
            return WkbUtil.fromWkb(hexString);
        }
        
        throw new GeometryConversionException(
            "Unexpected database value type",
            "Geometry",
            dbValue.getClass().getName()
        );
    }
}
