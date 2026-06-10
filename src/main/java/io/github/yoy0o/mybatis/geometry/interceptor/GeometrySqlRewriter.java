package io.github.yoy0o.mybatis.geometry.interceptor;

import io.github.yoy0o.mybatis.geometry.strategy.GeometryHandlerStrategy;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Responsible for parsing and rewriting SQL strings to wrap geometry columns
 * with the appropriate database-specific functions.
 *
 * <p>Extracted from GeometryFieldInterceptor for single responsibility
 * and independent testability.</p>
 */
public class GeometrySqlRewriter {

    private static final Pattern FROM_PATTERN = Pattern.compile(
        "(?i)FROM\\s+`?([a-zA-Z_][a-zA-Z0-9_]*)`?(?:\\s+(?:AS\\s+)?([a-zA-Z_][a-zA-Z0-9_]*))?",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern SELECT_PATTERN = Pattern.compile(
        "(?i)SELECT\\s+(.*?)\\s+FROM", Pattern.DOTALL
    );

    private final GeometryHandlerStrategy strategy;

    /**
     * Create a SQL rewriter with the specified strategy.
     *
     * @param strategy the geometry handler strategy for column wrapping
     */
    public GeometrySqlRewriter(GeometryHandlerStrategy strategy) {
        this.strategy = strategy;
    }

    /**
     * Rewrite a SQL SELECT statement to wrap geometry columns.
     *
     * @param sql             the original SQL
     * @param geometryColumns set of geometry column names
     * @param allColumns      ordered list of all columns (needed for SELECT * expansion), nullable
     * @return rewritten SQL, or original SQL if no changes needed
     */
    public String rewrite(String sql, Set<String> geometryColumns, List<String> allColumns) {
        if (geometryColumns == null || geometryColumns.isEmpty()) {
            return sql;
        }
        if (isSelectStar(sql) && allColumns != null && !allColumns.isEmpty()) {
            return expandSelectStar(sql, geometryColumns, allColumns);
        }
        return wrapExplicitColumns(sql, geometryColumns);
    }

    private boolean isSelectStar(String sql) {
        return sql.matches("(?i).*SELECT\\s+\\*\\s+FROM.*");
    }

    private String expandSelectStar(String sql, Set<String> geometryColumns, List<String> allColumns) {
        Matcher matcher = FROM_PATTERN.matcher(sql);
        String tableAlias = matcher.find() ? matcher.group(2) : null;
        String prefix = tableAlias != null ? tableAlias + "." : "";

        List<String> columnList = new ArrayList<>();
        for (String col : allColumns) {
            if (geometryColumns.contains(col)) {
                String wrappedColumn = strategy.wrapColumnForSelect(prefix + col);
                if (wrappedColumn.toUpperCase().contains(" AS ")) {
                    columnList.add(wrappedColumn);
                } else {
                    columnList.add(wrappedColumn + " AS " + col);
                }
            } else {
                columnList.add(prefix + col);
            }
        }
        String cols = String.join(", ", columnList);
        return sql.replaceFirst("(?i)SELECT\\s+\\*\\s+FROM", "SELECT " + cols + " FROM");
    }

    private String wrapExplicitColumns(String sql, Set<String> geometryColumns) {
        Matcher matcher = SELECT_PATTERN.matcher(sql);
        if (!matcher.find()) {
            return sql;
        }

        String selectClause = matcher.group(1);
        String before = sql.substring(0, matcher.start(1));
        String after = sql.substring(matcher.end(1));

        String[] columns = selectClause.split(",");
        List<String> processed = new ArrayList<>();
        for (String column : columns) {
            processed.add(processColumn(column.trim(), geometryColumns));
        }
        return before + String.join(", ", processed) + after;
    }

    private String processColumn(String column, Set<String> geometryColumns) {
        String upper = column.toUpperCase();
        // Skip if already wrapped with function
        if (upper.contains("HEX(") || upper.contains("ENCODE(")
            || upper.contains("ST_ASBINARY(") || upper.contains("COUNT(")
            || upper.contains("SUM(") || upper.contains("AVG(")) {
            return column;
        }
        String colName = extractColumnName(column);
        String snakeCase = colName.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
        if (geometryColumns.contains(colName) || geometryColumns.contains(snakeCase)) {
            if (column.toLowerCase().contains(" as ")) {
                String[] parts = column.split("(?i)\\s+as\\s+", 2);
                String colPart = parts[0].trim();
                String aliasPart = parts[1].trim();
                String wrappedColumn = strategy.wrapColumnForSelect(colPart);
                if (wrappedColumn.toUpperCase().contains(" AS ")) {
                    wrappedColumn = wrappedColumn.substring(0, wrappedColumn.toUpperCase().indexOf(" AS ")).trim();
                }
                return wrappedColumn + " AS " + aliasPart;
            } else {
                return strategy.wrapColumnForSelect(column);
            }
        }
        return column;
    }

    private String extractColumnName(String column) {
        String name = column;
        if (name.toLowerCase().contains(" as ")) {
            name = name.split("(?i)\\s+as\\s+", 2)[0].trim();
        }
        if (name.contains(".")) {
            name = name.substring(name.lastIndexOf('.') + 1);
        }
        return name.replace("`", "").replace("\"", "").replace("'", "").trim();
    }
}
