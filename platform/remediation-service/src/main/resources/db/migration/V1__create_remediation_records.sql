CREATE TABLE IF NOT EXISTS remediation_records (
    command_id    UUID PRIMARY KEY,
    incident_id   VARCHAR(64)  NOT NULL,
    action        VARCHAR(16)  NOT NULL,
    service_id    VARCHAR(128) NOT NULL,
    environment   VARCHAR(16)  NOT NULL,
    target_version VARCHAR(64),
    subject       VARCHAR(128),
    outcome        VARCHAR(16) NOT NULL,
    detail         TEXT,
    completed_at   TIMESTAMP    NOT NULL
);