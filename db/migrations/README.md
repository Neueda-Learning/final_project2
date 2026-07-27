# Database migrations

Flyway revisions live in `backend/src/main/resources/db/migration`. The canonical
bootstrap schema remains `docs/database/schema.sql`; every schema change must update both
representations. Local Docker bootstrap currently mounts the canonical schema directly.
