---
name: Bug Report
about: Create a report to help us improve
title: '[BUG] '
labels: bug
assignees: ''
---

## Bug Description

A clear and concise description of what the bug is.

## Environment

- **Library Version**: [e.g., 1.0.0]
- **Java Version**: [e.g., 17, 21]
- **Spring Boot Version**: [e.g., 2.7.x, 3.2.x]
- **MyBatis Plus Version**: [e.g., 3.5.7]
- **Database**: [e.g., MySQL 8.0, PostgreSQL 15 + PostGIS 3.3]
- **Operating System**: [e.g., Windows 11, Ubuntu 22.04]

## Steps to Reproduce

1. Configure entity with '...'
2. Execute query '...'
3. See error

## Expected Behavior

A clear and concise description of what you expected to happen.

## Actual Behavior

A clear and concise description of what actually happened.

## Code Sample

```java
// Minimal code to reproduce the issue
@TableName(value = "warehouse", autoResultMap = true)
public class Warehouse {
    @PointTableField
    private Point location;
}
```

## Error Message/Stack Trace

```
Paste the complete error message or stack trace here
```

## Additional Context

Add any other context about the problem here (screenshots, logs, etc.).
