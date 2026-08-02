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
    cwe_id INTEGER,
    affected_url TEXT,
    description TEXT,
    solution TEXT,
    detected_at TIMESTAMP DEFAULT NOW()
);
