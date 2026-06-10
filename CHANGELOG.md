# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed (Architecture Refactoring)

- **P0: Strategy Constructor Injection** — `AbstractGeometryTypeHandler` and all concrete TypeHandlers (`PointTypeHandler`, `PolygonTypeHandler`, `LineStringTypeHandler`) now accept `GeometryHandlerStrategy` via constructor injection. Backward-compatible no-arg and single-arg constructors are retained.
- **P0: Auto-Configuration Wiring** — `GeometryAutoConfiguration` now injects the detected `GeometryHandlerStrategy` bean directly into TypeHandler constructors, eliminating dependency on global static state.
- **P1: WkbCodec Interface** — Extracted `WkbCodec` interface with `MySQLWkbCodec` and `PostGISWkbCodec` implementations. Each Strategy now delegates encoding/decoding to its own Codec.
- **P1: PostGIS EWKB Direct Encoding** — `PostGISWkbCodec` builds EWKB directly from JTS Geometry fields instead of parsing `WkbUtil.toWkbBytes()` byte offsets, eliminating tight coupling.
- **P2: Interceptor Separation** — `GeometryFieldInterceptor` decomposed into `GeometryFieldResolver` (reflection + caching) and `GeometrySqlRewriter` (SQL parsing + rewriting), with interceptor acting as thin orchestrator.

### Fixed

- **Polygon Interior Ring Support** — `WkbUtil.toWkb(Polygon)` now serializes all rings (exterior + interior), fixing data loss for polygons with holes.

### Added

- `io.github.yoy0o.mybatis.geometry.codec.WkbCodec` — Codec interface for geometry encode/decode
- `io.github.yoy0o.mybatis.geometry.codec.MySQLWkbCodec` — MySQL-specific codec (SRID prefix + WKB binary)
- `io.github.yoy0o.mybatis.geometry.codec.PostGISWkbCodec` — PostGIS-specific codec (EWKB hex, direct from JTS)
- `io.github.yoy0o.mybatis.geometry.interceptor.GeometryFieldResolver` — Field scanning and metadata caching
- `io.github.yoy0o.mybatis.geometry.interceptor.GeometrySqlRewriter` — SQL rewriting logic

## [1.0.1] - 2025-06-09

### Fixed

- **PostgreSQL/PostGIS SELECT**: Fixed `WkbUtil.fromWkb()` failing with "Unknown WKB type" error when reading geometry from PostGIS. Root cause: `encode(ST_AsBinary(), 'hex')` returns standard WKB without SRID prefix, but the parser expected 4-byte SRID prefix. Now uses SRID LE hex + WKB hex concatenation in SQL.
- **PostgreSQL/PostGIS INSERT**: Fixed `ps.setObject(hexString)` being rejected as `character varying` by PostgreSQL. Now uses `ps.setObject(str, Types.OTHER)` for proper type inference, and outputs EWKB hex format that PostGIS directly recognizes.
- **PostGIS write format**: Changed `convertForDatabase()` to produce standard EWKB hex (with SRID flag `0x20000000` in type field) instead of custom SRID-prefixed WKB that PostGIS cannot parse.

### Added

- Demo application (`demo/`) with REST CRUD endpoints for validation
- Spring profiles support: `mysql`, `postgresql`, `mariadb`
- GeoJSON format input/output via Jackson serializer registration
- MariaDB 11.x compatibility verified
- DDL scripts for MySQL, PostgreSQL/PostGIS, and MariaDB

### Verified Database Support

- MySQL 8.0 ✅ (remote AWS instance)
- PostgreSQL 14 + PostGIS 3.x ✅ (local)
- MariaDB 11.8 ✅ (Docker)

## [1.0.0] - 2024-XX-XX

### Added

- Initial release
- Support for JTS geometry types: Point, Polygon, LineString
- MyBatis Plus TypeHandlers for automatic WKB conversion
- Field annotations: `@PointTableField`, `@PolygonTableField`, `@LineStringTableField`
- Jackson serializers/deserializers for GeoJSON format
- SQL interceptor for automatic HEX() wrapping in SELECT queries
- Spring Boot auto-configuration for 2.7+ and 3.x
- MySQL and PostgreSQL/PostGIS database support
- Automatic database type detection from DataSource
- Configuration properties for SRID and interceptor settings
- Comprehensive documentation and examples

### Database Support

- MySQL 8.0+ with native GEOMETRY type
- MariaDB 10.5+ with native GEOMETRY type
- PostgreSQL 12+ with PostGIS 3.0+ extension

### Dependencies

- JTS Core 1.19.0
- MyBatis Plus 3.5.7 (compileOnly)
- Spring Boot 2.7+ / 3.x (compileOnly)
- Jackson Databind (compileOnly)
- Apache Commons Codec 1.16.0

[Unreleased]: https://github.com/yoy0o/mybatis-plus-geometry/compare/v1.0.1...HEAD
[1.0.1]: https://github.com/yoy0o/mybatis-plus-geometry/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/yoy0o/mybatis-plus-geometry/releases/tag/v1.0.0
