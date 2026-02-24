-- CCE Compliance Service — Initial Schema
-- Aligned with CCE Solution Design v0.3 and Technology Stack Proposal

-- ==================== PlanDefinition ====================
CREATE TABLE plan_definition (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    url          VARCHAR NOT NULL,
    version      VARCHAR NOT NULL,
    status       VARCHAR NOT NULL CHECK (status IN ('ACTIVE', 'RETIRED')),
    definition   JSONB NOT NULL,
    loaded_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (url, version)
);

CREATE INDEX idx_plan_definition_triggers
    ON plan_definition USING GIN (definition jsonb_path_ops);

-- ==================== Protocol Instance ====================
CREATE TABLE protocol_instance (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id          VARCHAR NOT NULL,
    protocol_canonical  VARCHAR NOT NULL,
    plan_definition_id  UUID NOT NULL REFERENCES plan_definition(id),
    enrolled_at         TIMESTAMPTZ NOT NULL,
    status              VARCHAR NOT NULL CHECK (status IN ('ACTIVE', 'COMPLETED', 'WITHDRAWN', 'EXPIRED')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_protocol_instance_patient ON protocol_instance (patient_id);
CREATE INDEX idx_protocol_instance_status  ON protocol_instance (status) WHERE status = 'ACTIVE';

-- ==================== Step Instance ====================
CREATE TABLE step_instance (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    protocol_instance_id  UUID NOT NULL REFERENCES protocol_instance(id),
    action_id             VARCHAR NOT NULL,
    repeat_index          INTEGER NOT NULL DEFAULT 0,
    state                 VARCHAR NOT NULL CHECK (state IN ('PENDING', 'DUE', 'OVERDUE', 'MISSED', 'COMPLETED', 'SKIPPED')),
    due_date              TIMESTAMPTZ,
    overdue_date          TIMESTAMPTZ,
    missed_date           TIMESTAMPTZ,
    completed_at          TIMESTAMPTZ,
    completed_by_source   VARCHAR,
    completion_status     VARCHAR CHECK (completion_status IN ('ON_TIME', 'EARLY', 'LATE')),
    matched_event_id      UUID,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_step_instance_protocol   ON step_instance (protocol_instance_id);
CREATE INDEX idx_step_instance_state      ON step_instance (state) WHERE state IN ('PENDING', 'DUE', 'OVERDUE');
CREATE INDEX idx_step_instance_due_date   ON step_instance (due_date) WHERE state IN ('PENDING', 'DUE', 'OVERDUE');

-- ==================== Deviation ====================
CREATE TABLE deviation (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    protocol_instance_id  UUID NOT NULL REFERENCES protocol_instance(id),
    step_instance_id      UUID NOT NULL REFERENCES step_instance(id),
    deviation_type        VARCHAR NOT NULL CHECK (deviation_type IN ('OVERDUE', 'MISSED', 'AMBIGUOUS')),
    detected_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    intelligence_event_id UUID,
    metadata              JSONB
);

CREATE INDEX idx_deviation_protocol ON deviation (protocol_instance_id);
CREATE INDEX idx_deviation_type     ON deviation (deviation_type);

-- ==================== Trigger Index ====================
CREATE TABLE trigger_index (
    resource_type       VARCHAR NOT NULL,
    code_system         VARCHAR NOT NULL DEFAULT '',
    code_value          VARCHAR NOT NULL DEFAULT '',
    plan_definition_id  UUID NOT NULL REFERENCES plan_definition(id),
    action_id           VARCHAR NOT NULL,
    trigger_mode        VARCHAR NOT NULL CHECK (trigger_mode IN ('DATA_ADDED', 'NAMED_EVENT')),
    PRIMARY KEY (resource_type, code_system, code_value, plan_definition_id, action_id)
);

CREATE INDEX idx_trigger_index_resource ON trigger_index (resource_type);
CREATE INDEX idx_trigger_index_code     ON trigger_index (resource_type, code_system, code_value);

-- ==================== Event Log (partitioned by received_at) ====================
CREATE TABLE event_log (
    id                       UUID NOT NULL DEFAULT gen_random_uuid(),
    cloudevents_id           VARCHAR NOT NULL,
    source                   VARCHAR NOT NULL,
    source_event_id          VARCHAR,
    subject                  VARCHAR NOT NULL,
    type                     VARCHAR NOT NULL,
    event_time               TIMESTAMPTZ NOT NULL,
    received_at              TIMESTAMPTZ NOT NULL,
    correlation_id           VARCHAR NOT NULL,
    data                     JSONB NOT NULL,
    protocol_instance_id     UUID,
    protocol_definition_id   UUID,
    action_id                VARCHAR,
    facility_id              VARCHAR,
    processing_status        VARCHAR NOT NULL,
    matched_step_instance_id UUID,
    UNIQUE (cloudevents_id, source, received_at)
) PARTITION BY RANGE (received_at);

CREATE UNIQUE INDEX idx_event_log_source_sourceeventid
    ON event_log (source, source_event_id, received_at) WHERE source_event_id IS NOT NULL;

CREATE INDEX idx_event_log_subject      ON event_log (subject);
CREATE INDEX idx_event_log_facility     ON event_log (facility_id) WHERE facility_id IS NOT NULL;

-- Initial monthly partitions
CREATE TABLE event_log_2026_02 PARTITION OF event_log
    FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');
CREATE TABLE event_log_2026_03 PARTITION OF event_log
    FOR VALUES FROM ('2026-03-01') TO ('2026-04-01');
CREATE TABLE event_log_2026_04 PARTITION OF event_log
    FOR VALUES FROM ('2026-04-01') TO ('2026-05-01');
CREATE TABLE event_log_2026_05 PARTITION OF event_log
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');
CREATE TABLE event_log_2026_06 PARTITION OF event_log
    FOR VALUES FROM ('2026-06-01') TO ('2026-07-01');

-- ==================== Dead-Letter Events ====================
CREATE TABLE dead_letter_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payload         JSONB NOT NULL,
    failure_reason  VARCHAR NOT NULL,
    failure_stage   VARCHAR NOT NULL CHECK (failure_stage IN ('KAFKA_PUBLISH', 'PROCESSING', 'VALIDATION')),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    retry_count     INTEGER NOT NULL DEFAULT 0,
    next_retry_at   TIMESTAMPTZ,
    resolved        BOOLEAN NOT NULL DEFAULT false,
    resolved_at     TIMESTAMPTZ
);

CREATE INDEX idx_dead_letter_unresolved ON dead_letter_events (next_retry_at) WHERE resolved = false;

-- ==================== Audit Log ====================
CREATE TABLE audit_log (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_category  VARCHAR NOT NULL,
    event_type      VARCHAR NOT NULL,
    actor           VARCHAR,
    resource_type   VARCHAR,
    resource_id     VARCHAR,
    details         JSONB,
    ip_address      VARCHAR,
    timestamp       TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_log_category  ON audit_log (event_category);
CREATE INDEX idx_audit_log_actor     ON audit_log (actor);
CREATE INDEX idx_audit_log_timestamp ON audit_log (timestamp);
