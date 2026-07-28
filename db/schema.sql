-- Ejecutar contra la base de datos devsecops_dashboard (crearla antes si no existe):
--   CREATE DATABASE devsecops_dashboard;
--
-- spring.jpa.hibernate.ddl-auto=validate, así que Hibernate no crea ni altera
-- esta tabla: debe existir de antemano exactamente con esta forma.

CREATE TABLE IF NOT EXISTS scan_findings (
    id SERIAL PRIMARY KEY,
    target_url TEXT NOT NULL,
    scan_type VARCHAR(20) NOT NULL,
    alert_name TEXT NOT NULL,
    risk_level VARCHAR(20) NOT NULL,
    cwe_id INTEGER,
    affected_url TEXT,
    description TEXT,
    solution TEXT,
    detected_at TIMESTAMP DEFAULT NOW()
);
