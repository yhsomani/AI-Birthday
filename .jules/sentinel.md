## 2024-09-27 - [Recursive Redaction]
**Vulnerability:** Found that the app logger's redaction method only analyzed top-level keys. Sensitive nested data could be inadvertently exposed if nested inside safe top-level keys (e.g. `{'user': {'password': '123'}}`).
**Learning:** The application logs parameter maps deeply, which necessitates deep redaction instead of surface-level redaction.
**Prevention:** Ensure all object serialization/stringification mechanisms that run on unknown inputs deep-scan objects for redaction, or enforce schema-based logging only.
