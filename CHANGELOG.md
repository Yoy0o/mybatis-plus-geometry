# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Changed

- **WKB Codec → JTS Official Implementation** — `MySQLWkbCodec` and `PostGISWkbCodec` rewritten to use JTS `WKBWriter`/`WKBReader`. Eliminates hand-written byte manipulation, supports all geometry types (including Multi* and GeometryCollection), and is thread-safe via per-call instance creation.
- **TypeHandler Strategy Injection** — `AbstractGeometryTypeHandler` and concrete TypeHandlers accept `GeometryHandlerStrategy` via constructor injection. No-arg constructors are retained for MyBatis annotation-based reflection, pulling from `GeometryStrategyFactory.getDefaultStrategy()`.
- **Auto-Configuration Strategy Sync** — `GeometryAutoConfiguration.geometryHandlerStrategy()` calls `GeometryStrategyFactory.setDefaultStrategy(strategy)` before returning, guaranteeing TypeHandlers created via reflection share the same instance as the Spring Bean.
- **GeometryStrategyFactory Concurrency** — `defaultStrategy` field uses `volatile` write semantics (removed redundant `synchronized` on setter). `getDefaultStrategy()` retains double-check locking for safe fallback initialization to MySQL.
- **Interceptor Decomposition** — `GeometryFieldInterceptor` split into `GeometryFieldResolver` (reflection + caching) and `GeometrySqlRewriter` (SQL parsing + rewriting), with the interceptor as a thin orchestrator.
- **SQL Field Splitting** — `GeometrySqlRewriter.splitSelectFields()` uses parenthesis depth counting to correctly skip commas inside function calls (e.g., `COALESCE(a, b)`). Expressions containing `(` are never wrapped as geometry columns.
- **Hex Encoding** — All hex encode/decode operations migrated from Apache Commons Codec to `java.util.HexFormat` (JDK 17+).

### Fixed

- **SQL Alias Dot Bug** — `wrapColumnForSelect("t.location")` previously generated invalid alias `AS t.location`. Now extracts the part after the last dot: `HEX(t.location) AS location`. Applied to both MySQL and PostGIS strategies.
- **Polygon Interior Rings** — `WkbUtil.toWkb(Polygon)` now serializes all rings (exterior + interior), fixing silent data loss for polygons with holes.
- **Version Number Mismatch** — `build.gradle` no longer hardcodes the version string; reads from `gradle.properties` as the single source of truth.
- **.gitignore Over-Exclusion** — Removed `demo/` and `src/test/` ignore rules that prevented source code from being tracked.

### Added

- `GeometryJacksonModule` — Unified Jackson Module that auto-registers Point/LineString/Polygon serializers and deserializers via Spring Boot `@AutoConfiguration`. Zero user configuration required when Jackson is on the classpath.
- **Automatic Coordinate Validation** — When `default-srid` is 4326 (WGS84), GeoJSON deserializers validate coordinate ranges (lng -180~180, lat -90~90). For any other SRID, range validation is automatically disabled and only finite-number checks apply. No separate configuration property needed.
- `GeoJsonParseException` — Structured exceptions with explicit field names (`type`, `coordinates`) for all malformed GeoJSON input scenarios.
- `WkbCodec` interface — Clean abstraction for database-specific geometry encoding/decoding, with `MySQLWkbCodec` (4-byte LE SRID + standard WKB) and `PostGISWkbCodec` (EWKB with SRID flag in type field).
- `GeometryFieldResolver` — Dedicated class for entity field scanning, annotation detection, and metadata caching.

### Removed

- `commons-codec:commons-codec` runtime dependency — No longer needed; replaced by JDK 17+ `HexFormat` and JTS built-in WKB I/O.

## [1.0.1] - 2025-06-09

### Fixed

- **PostgreSQL/PostGIS SELECT**: Fixed `WkbUtil.fromWkb()` failing with "Unknown WKB type" error when reading geometry from PostGIS. Root cause: `encode(ST_AsBinary(), 'hex')` returns standard WKB without SRID prefix, but the parser expected 4-byte SRID prefix. Now uses SRID LE hex + WKB hex concatenation in SQL.
- **PostgreSQL/PostGIS INSERT**: Fixed `ps.setObject(hexString)` being rejected as `character varying` by PostgreSQL. Now uses `ps.setObject(str, Types.OTHER)` for proper type inference, and outputs EWKB hex format that PostGIS directly recognizes.
- **PostGIS write format**: Changed `convertForDatabase()` to produce standard EWKB hex (with SRID flag `0x20000000` in type field) instead of custom SRID-prefixed WKB that PostGIS cannot parse.

### Added

- Spring profiles support: `mysql`, `postgresql`, `mariadb`
- GeoJSON format input/output via Jackson serializer registration
- MariaDB 11.x compatibility verified
- DDL scripts for MySQL, PostgreSQL/PostGIS, and MariaDB

### Verified Database Support

- MySQL 8.0 ✅
- PostgreSQL 14 + PostGIS 3.x ✅
- MariaDB 11.8 ✅

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
- Apache Commons Codec 1.16.0 *(removed in Unreleased)*

[Unreleased]: https://github.com/yoy0o/mybatis-plus-geometry/compare/v1.0.1...HEAD
[1.0.1]: https://github.com/yoy0o/mybatis-plus-geometry/compare/v1.0.0...v1.0.1
[1.0.0]: https://github.com/yoy0o/mybatis-plus-geometry/releases/tag/v1.0.0
