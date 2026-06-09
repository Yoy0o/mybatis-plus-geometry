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
        // PostGIS: Build the format expected by WkbUtil.fromWkb(): 4-byte LE SRID + standard WKB
        //
        // ST_SRID returns integer SRID (e.g., 4326 = 0x000010E6)
        // We need it as 4-byte little-endian hex: E6100000
        // Strategy: use lpad(to_hex(srid), 8, '0') to get 8-char big-endian hex,
        //           then reverse byte pairs for little-endian
        //
        // Example: SRID 4326 → to_hex = '10e6' → lpad = '000010e6'
        //          → reverse: 'e6' + '10' + '00' + '00' = 'e6100000'
        String simpleCol = extractSimpleColumnName(columnName);
        String sridHex = "lpad(to_hex(ST_SRID(" + columnName + ")), 8, '0')";
        String sridLE = "substr(" + sridHex + ",7,2)||substr(" + sridHex + ",5,2)||substr(" + sridHex + ",3,2)||substr(" + sridHex + ",1,2)";
        String wkbHex = "encode(ST_AsBinary(" + columnName + "), 'hex')";
        return "(" + sridLE + " || " + wkbHex + ") AS " + simpleCol;
    }

    /**
     * Extract simple column name from potentially qualified name (e.g., "t.location" → "location").
     */
    private String extractSimpleColumnName(String columnName) {
        int dotIndex = columnName.lastIndexOf('.');
        return dotIndex >= 0 ? columnName.substring(dotIndex + 1) : columnName;
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
        // Convert JTS Geometry to PostGIS EWKB hex format
        // EWKB format: byte_order(1) + type_with_srid_flag(4) + srid(4) + geometry_data
        // PostGIS geometry columns directly accept hex EWKB string
        return toEwkbHex(geometry);
    }

    /**
     * Convert JTS Geometry to EWKB hex string for PostGIS.
     * EWKB format includes SRID flag in the type field.
     */
    private String toEwkbHex(Geometry geometry) {
        // Get standard WKB from WkbUtil (which includes our custom 4-byte SRID prefix)
        byte[] customWkb = WkbUtil.toWkbBytes(geometry);
        // customWkb format: SRID(4 bytes LE) + byte_order(1) + type(4) + data

        // Extract components
        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(customWkb);
        buf.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        int srid = buf.getInt();           // 4 bytes: SRID
        byte byteOrder = buf.get();        // 1 byte: byte order (1=LE)
        int wkbType = buf.getInt();        // 4 bytes: geometry type

        // Build EWKB: byte_order + (type | 0x20000000) + srid + remaining data
        int remaining = customWkb.length - 9; // total - 4(srid) - 1(byte_order) - 4(type)
        java.nio.ByteBuffer ewkb = java.nio.ByteBuffer.allocate(1 + 4 + 4 + remaining);
        ewkb.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        ewkb.put(byteOrder);                        // byte order
        ewkb.putInt(wkbType | 0x20000000);          // type with SRID flag
        ewkb.putInt(srid);                          // SRID
        ewkb.put(customWkb, 9, remaining);          // geometry data (coordinates etc.)

        return Hex.encodeHexString(ewkb.array());
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
