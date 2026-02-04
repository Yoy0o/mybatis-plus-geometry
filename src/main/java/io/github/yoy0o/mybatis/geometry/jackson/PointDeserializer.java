package io.github.yoy0o.mybatis.geometry.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.yoy0o.mybatis.geometry.exception.GeoJsonParseException;
import io.github.yoy0o.mybatis.geometry.exception.InvalidCoordinateException;
import io.github.yoy0o.mybatis.geometry.util.GeometryFactoryProvider;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Point;

import java.io.IOException;

/**
 * Jackson deserializer for GeoJSON Point to JTS Point.
 * 
 * <p>Expected input format:</p>
 * <pre>{@code
 * {
 *   "type": "Point",
 *   "coordinates": [longitude, latitude]
 * }
 * }</pre>
 * 
 * <p>Usage in DTO:</p>
 * <pre>{@code
 * @JsonSerialize(using = PointSerializer.class)
 * @JsonDeserialize(using = PointDeserializer.class)
 * private Point location;
 * }</pre>
 */
public class PointDeserializer extends JsonDeserializer<Point> {
    
    @Override
    public Point deserialize(JsonParser parser, DeserializationContext ctx) throws IOException {
        JsonNode node = parser.getCodec().readTree(parser);
        
        if (node == null || node.isNull()) {
            return null;
        }
        
        // Validate GeoJSON type
        JsonNode typeNode = node.get("type");
        if (typeNode == null) {
            throw new GeoJsonParseException("Missing 'type' field", "type");
        }
        
        String type = typeNode.asText();
        if (!"Point".equals(type)) {
            throw GeoJsonParseException.forTypeMismatch("Point", type);
        }
        
        // Validate coordinates
        JsonNode coordinatesNode = node.get("coordinates");
        if (coordinatesNode == null || !coordinatesNode.isArray()) {
            throw new GeoJsonParseException("Missing or invalid 'coordinates' field", "coordinates");
        }
        
        if (coordinatesNode.size() < 2) {
            throw new GeoJsonParseException("Coordinates array must have at least 2 elements", "coordinates");
        }
        
        // Parse coordinates [longitude, latitude]
        double longitude = coordinatesNode.get(0).asDouble();
        double latitude = coordinatesNode.get(1).asDouble();
        
        // Validate coordinate ranges
        if (longitude < -180 || longitude > 180) {
            throw InvalidCoordinateException.forLongitude(longitude);
        }
        if (latitude < -90 || latitude > 90) {
            throw InvalidCoordinateException.forLatitude(latitude);
        }
        
        return GeometryFactoryProvider.getFactory().createPoint(new Coordinate(longitude, latitude));
    }
}
