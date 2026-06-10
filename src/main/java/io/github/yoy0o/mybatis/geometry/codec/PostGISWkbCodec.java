package io.github.yoy0o.mybatis.geometry.codec;

import org.locationtech.jts.geom.*;
import org.locationtech.jts.io.WKBReader;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * PostGIS EWKB codec that builds EWKB directly from JTS Geometry fields.
 * Does NOT depend on WkbUtil.toWkbBytes() internal byte layout.
 *
 * <p>Encode output: {@code String} hex EWKB (used with {@code ps.setObject(str, Types.OTHER)})</p>
 * <p>Decode input: {@code String} hex (4-byte LE SRID prefix + standard WKB from SELECT)</p>
 */
public class PostGISWkbCodec implements WkbCodec {

    private static final int SRID_FLAG = 0x20000000;
    private static final byte LITTLE_ENDIAN_BYTE = 0x01;

    private static final int WKB_POINT = 1;
    private static final int WKB_LINESTRING = 2;
    private static final int WKB_POLYGON = 3;

    private static final int DEFAULT_SRID = 4326;

    private static final ThreadLocal<WKBReader> WKB_READER =
        ThreadLocal.withInitial(WKBReader::new);

    @Override
    public Object encode(Geometry geometry) {
        if (geometry == null) {
            return null;
        }
        return toEwkbHex(geometry);
    }

    @Override
    public Geometry decode(Object dbValue) {
        if (dbValue == null) {
            return null;
        }
        if (dbValue instanceof String hexString) {
            if (hexString.isEmpty()) {
                return null;
            }
            return fromSridPrefixedWkb(hexString);
        }
        throw new IllegalArgumentException(
            "PostGISWkbCodec expects String hex, got: " + dbValue.getClass().getName());
    }

    // --- Encoding: build EWKB directly from JTS fields ---

    private String toEwkbHex(Geometry geometry) {
        if (geometry instanceof Point p) return encodePoint(p);
        if (geometry instanceof LineString ls) return encodeLineString(ls);
        if (geometry instanceof Polygon pg) return encodePolygon(pg);
        throw new IllegalArgumentException("Unsupported geometry type: " + geometry.getGeometryType());
    }

    private String encodePoint(Point point) {
        int srid = effectiveSrid(point);
        // 1(byte_order) + 4(type) + 4(srid) + 8(x) + 8(y) = 25 bytes
        ByteBuffer buf = ByteBuffer.allocate(25);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.put(LITTLE_ENDIAN_BYTE);
        buf.putInt(WKB_POINT | SRID_FLAG);
        buf.putInt(srid);
        buf.putDouble(point.getX());
        buf.putDouble(point.getY());
        return bytesToHex(buf.array());
    }

    private String encodeLineString(LineString lineString) {
        int srid = effectiveSrid(lineString);
        int numPoints = lineString.getNumPoints();
        // 1 + 4 + 4 + 4(numPoints) + numPoints*16
        ByteBuffer buf = ByteBuffer.allocate(13 + numPoints * 16);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.put(LITTLE_ENDIAN_BYTE);
        buf.putInt(WKB_LINESTRING | SRID_FLAG);
        buf.putInt(srid);
        buf.putInt(numPoints);
        for (Coordinate c : lineString.getCoordinates()) {
            buf.putDouble(c.x);
            buf.putDouble(c.y);
        }
        return bytesToHex(buf.array());
    }

    private String encodePolygon(Polygon polygon) {
        int srid = effectiveSrid(polygon);
        int numRings = 1 + polygon.getNumInteriorRing();
        int totalPoints = polygon.getExteriorRing().getNumPoints();
        for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
            totalPoints += polygon.getInteriorRingN(i).getNumPoints();
        }
        // 1 + 4 + 4 + 4(numRings) + numRings*4(numPoints per ring) + totalPoints*16
        ByteBuffer buf = ByteBuffer.allocate(13 + numRings * 4 + totalPoints * 16);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.put(LITTLE_ENDIAN_BYTE);
        buf.putInt(WKB_POLYGON | SRID_FLAG);
        buf.putInt(srid);
        buf.putInt(numRings);
        writeRing(buf, polygon.getExteriorRing());
        for (int i = 0; i < polygon.getNumInteriorRing(); i++) {
            writeRing(buf, polygon.getInteriorRingN(i));
        }
        return bytesToHex(buf.array());
    }

    private void writeRing(ByteBuffer buf, LineString ring) {
        int numPoints = ring.getNumPoints();
        buf.putInt(numPoints);
        for (Coordinate c : ring.getCoordinates()) {
            buf.putDouble(c.x);
            buf.putDouble(c.y);
        }
    }

    // --- Decoding: parse SRID-prefixed WKB from SELECT ---

    private Geometry fromSridPrefixedWkb(String hexString) {
        byte[] bytes = hexToBytes(hexString);
        // Format from wrapColumnForSelect: 4-byte LE SRID + standard WKB
        ByteBuffer header = ByteBuffer.wrap(bytes, 0, 4);
        header.order(ByteOrder.LITTLE_ENDIAN);
        int srid = header.getInt();
        byte[] wkbOnly = new byte[bytes.length - 4];
        System.arraycopy(bytes, 4, wkbOnly, 0, wkbOnly.length);
        try {
            Geometry geom = WKB_READER.get().read(wkbOnly);
            geom.setSRID(srid);
            return geom;
        } catch (Exception e) {
            String prefix = hexString.length() > 20 ? hexString.substring(0, 20) + "..." : hexString;
            throw new RuntimeException("Failed to decode WKB from PostGIS: " + prefix, e);
        }
    }

    // --- Helpers ---

    private int effectiveSrid(Geometry geometry) {
        return geometry.getSRID() == 0 ? DEFAULT_SRID : geometry.getSRID();
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
