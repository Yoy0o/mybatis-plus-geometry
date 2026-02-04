package io.github.yoy0o.mybatis.geometry.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.yoy0o.mybatis.geometry.exception.GeoJsonParseException;
import io.github.yoy0o.mybatis.geometry.exception.InvalidCoordinateException;
import io.github.yoy0o.mybatis.geometry.util.GeometryFactoryProvider;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.LineString;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Jackson deserializer for GeoJSON LineString to JTS LineString.
 * 
 * <p>Expected input format:</p>
 * <pre>{@code
 * {
 *   "type": "LineString",
 *   "coordinates": [[lon1, lat1], [lon2, lat2], ...]
 * }
 * }</pre>
 * 
 * <p>Usage in DTO:</p>
 * <pre>{@code
 * @JsonSerialize(using = LineStringSerializer.class)
 * @JsonDeserialize(using = LineStringDeserializer.class)
 * private LineString route;
 * }</pre>
 */
public class LineStringDeserializer extends JsonDeserializer<LineString> {
    
    @Override
    public LineString deserialize(JsonParser parser, DeserializationContext ctx) throws IOException {
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
        if (!"LineString".equals(type)) {
            throw GeoJsonParseException.forTypeMismatch("LineString", type);
        }
        
        // Validate coordinates
        JsonNode coordinatesNode = node.get("coordinates");
        if (coordinatesNode == null || !coordinatesNode.isArray()) {
            throw new GeoJsonParseException("Missing or invalid 'coordinates' field", "coordinates");
        }
        
        // Parse coordinates
        List<Coordinate> coordinates = new ArrayList<>();
        
        for (int i = 0; i < coordinatesNode.size(); i++) {
            JsonNode coordNode = coordinatesNode.get(i);
            if (!coordNode.isArray() || coordNode.size() < 2) {
                throw new GeoJsonParseException("Invalid coordinate pair at index " + i, "coordinates");
            }
            
            double longitude = coordNode.get(0).asDouble();
            double latitude = coordNode.get(1).asDouble();
            
            // Validate coordinate ranges
            if (longitude < -180 || longitude > 180) {
                throw InvalidCoordinateException.forLongitude(longitude);
            }
            if (latitude < -90 || latitude > 90) {
                throw InvalidCoordinateException.forLatitude(latitude);
            }
            
            coordinates.add(new Coordinate(longitude, latitude));
        }
        
        // Validate minimum points
        if (coordinates.size() < 2) {
            throw new GeoJsonParseException("LineString must have at least 2 points", "coordinates");
        }
        
        return GeometryFactoryProvider.getFactory()
            .createLineString(coordinates.toArray(new Coordinate[0]));
    }
}
