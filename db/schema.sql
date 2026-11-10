-- Este archivo se monta en /docker-entrypoint-initdb.d/ y el contenedor de
-- Postgres lo ejecuta automaticamente la primera vez que arranca (data dir
-- vacio) contra la base devsecops_dashboard, ya creada via POSTGRES_DB
-- (ver docker-compose.yml). No hace falta correrlo a mano salvo que estes
-- apuntando a un Postgres fuera de docker compose.
--
-- spring.jpa.hibernate.ddl-auto=validate, así que Hibernate no crea ni altera
-- esta tabla: debe existir de antemano exactamente con esta forma.

CREATE TABLE IF NOT EXISTS scan_findings (
    id SERIAL PRIMARY KEY,
    target_url TEXT NOT NULL,
    scan_type VARCHAR(20) NOT NULL,
    alert_name TEXT NOT NULL,
    risk_level VARCHAR(20) NOT NULL,
    confidence VARCHAR(20),
    plugin_id VARCHAR(20),
    cwe_id INTEGER,
    owasp_category VARCHAR(40),
    affected_url TEXT,
    evidence TEXT,
    description TEXT,
    solution TEXT,
    reference TEXT,
    detected_at TIMESTAMP DEFAULT NOW()
);

-- Columnas agregadas para los reportes ejecutivo/tecnico. En una base nueva ya
-- existen por el CREATE de arriba (estos ALTER no hacen nada); en una base creada
-- antes de este cambio, re-ejecutar este archivo las agrega sin borrar findings:
--   docker exec -i postgres psql -U <DB_USERNAME> -d devsecops_dashboard < db/schema.sql
ALTER TABLE scan_findings ADD COLUMN IF NOT EXISTS confidence VARCHAR(20);
ALTER TABLE scan_findings ADD COLUMN IF NOT EXISTS plugin_id VARCHAR(20);
ALTER TABLE scan_findings ADD COLUMN IF NOT EXISTS evidence TEXT;
ALTER TABLE scan_findings ADD COLUMN IF NOT EXISTS reference TEXT;

-- Historial de scans: una fila por scan, insertada al iniciarlo y completada al terminar
-- (status DONE/STOPPED/FAILED y conteo de hallazgos nuevos por severidad). Los que
-- quedan en RUNNING porque el backend se reinicio a mitad de scan se marcan FAILED al
-- arrancar (ver ScanService). Igual que arriba: re-ejecutar este archivo sobre una base
-- existente agrega la tabla y la columna sin borrar findings.
CREATE TABLE IF NOT EXISTS scan_runs (
    id VARCHAR(36) PRIMARY KEY,
    target_url TEXT NOT NULL,
    risk_levels VARCHAR(40),
    status VARCHAR(20) NOT NULL,
    started_at TIMESTAMP NOT NULL,
    finished_at TIMESTAMP,
    error_message TEXT,
    new_high INTEGER NOT NULL DEFAULT 0,
    new_medium INTEGER NOT NULL DEFAULT 0,
    new_low INTEGER NOT NULL DEFAULT 0,
    new_informational INTEGER NOT NULL DEFAULT 0
);

-- Scan que encontro por primera vez cada hallazgo (NULL en hallazgos guardados antes
-- de que existiera el historial).
ALTER TABLE scan_findings ADD COLUMN IF NOT EXISTS scan_run_id VARCHAR(36)
    REFERENCES scan_runs(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_scan_findings_scan_run_id ON scan_findings(scan_run_id);
