package io.github.yoy0o.mybatis.geometry.codec;

import io.github.yoy0o.mybatis.geometry.util.WkbUtil;
import org.locationtech.jts.geom.Geometry;

/**
 * MySQL WKB codec using 4-byte SRID prefix + standard WKB binary format.
 * Delegates to WkbUtil for the actual byte manipulation.
 *
 * <p>Encode output: {@code byte[]} (used with {@code ps.setBytes()})</p>
 * <p>Decode input: {@code String} hex (from HEX() SQL function)</p>
 */
public class MySQLWkbCodec implements WkbCodec {

    @Override
    public Object encode(Geometry geometry) {
        if (geometry == null) {
            return null;
        }
        return WkbUtil.toWkbBytes(geometry);
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
            return WkbUtil.fromWkb(hexString);
        }
        throw new IllegalArgumentException(
            "MySQLWkbCodec expects String hex, got: " + dbValue.getClass().getName());
    }
}
