package io.github.yoy0o.mybatis.geometry.strategy;

import io.github.yoy0o.mybatis.geometry.exception.GeometryConversionException;
import io.github.yoy0o.mybatis.geometry.util.WkbUtil;
import org.locationtech.jts.geom.Geometry;

/**
 * MySQL-specific geometry handling strategy.
 * Uses WKB binary format for storage and HEX() function for reading.
 */
public class MySQLGeometryStrategy implements GeometryHandlerStrategy {
    
    @Override
    public DatabaseType getSupportedDatabaseType() {
        return DatabaseType.MYSQL;
    }
    
    @Override
    public String wrapColumnForSelect(String columnName) {
        return "HEX(" + columnName + ") AS " + columnName;
    }
    
    @Override
    public String getGeometryInputFunction() {
        // MySQL accepts direct binary input for GEOMETRY columns
        return "?";
    }
    
    @Override
    public Object convertForDatabase(Geometry geometry) {
        if (geometry == null) {
            return null;
        }
        return WkbUtil.toWkbBytes(geometry);
    }
    
    @Override
    public Geometry parseFromDatabase(Object dbValue) {
        if (dbValue == null) {
            return null;
        }
        
        if (dbValue instanceof String hexString) {
            return WkbUtil.fromWkb(hexString);
        }
        
        throw new GeometryConversionException(
            "Unexpected database value type",
            "Geometry",
            dbValue.getClass().getName()
        );
    }
}
