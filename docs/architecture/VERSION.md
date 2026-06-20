# Version Model

Versions are independent.

```text
engine_version
native_format_version
content_version
converter_version
save_schema_version
```

## Rules

- Runtime may reject content with unsupported `native_format_version`.
- Runtime may reject saves with newer `save_schema_version`.
- Converter version changes do not imply engine version changes.
- Content version changes do not imply save compatibility.

