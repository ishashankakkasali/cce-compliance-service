# CCE Compliance Service — AI Agent Instructions (Standalone Repo)

## Project Overview

This is the **Compliance Service** — the core processing engine of the Care Coordination Engine (CCE). It is built and deployed as a standalone Spring Boot application in its own repository. It consumes care events from Kafka, matches them to FHIR PlanDefinition protocol steps, manages protocol/step instance lifecycles, and persists compliance state to PostgreSQL.

**Upstream design documents (maintained in the `cce-compliance-sub_system` repo):**
- `CCE Solution Design v0.3 Draft.pdf` — full architecture, event model, matching logic, APIs
- `CCE_Technology_Stack_Proposal.md` — tech choices, DB schema, infrastructure

---

## What This Service Does

The Compliance Service is responsible for:
1. **Consuming events** from Kafka topic `cce.events.inbound` (published by the Collector Service)
2. **Consuming scheduler triggers** from Kafka topic `cce.scheduler.triggers` (published by the Scheduler Service)
3. **Two-tier trigger matching** — matching event data payloads against PlanDefinition step triggers
4. **Protocol instance management** — creating/updating patient enrollments in protocols
5. **Step instance lifecycle** — progressive instantiation, state transitions (completion by event)
6. **Deviation recording** — creating deviation records when steps become overdue/missed
7. **Serving REST APIs** — protocol definitions CRUD, protocol instance queries, patient compliance tracking, event timeline
8. **Publishing intelligence triggers** to Kafka topic `cce.intelligence.triggers` (out of scope for now — log only)

**What this service does NOT do:**
- Event ingestion/validation (Collector Service)
- OAuth token validation or routing (Gateway Service)
- Time-based state transitions like pending→due→overdue→missed (Scheduler Service)
- Analytics aggregations (Analytics Service)
- Intelligence event delivery or action execution (Intelligence Subsystem — out of scope)

---

## Technology Stack

| Concern | Technology | Version |
|---------|------------|---------|
| Language | Java | 21 (LTS) |
| Framework | Spring Boot | 3.x |
| Build tool | Maven | 3.9+ |
| Database | PostgreSQL | 16+ (latest stable) |
| Message broker | Apache Kafka | 3.7+ (KRaft mode) |
| Cache | Redis | 7.x |
| FHIR library | HAPI FHIR | 7.4.0 |
| Expression eval | json-logic-java | 1.0.7 |
| DB access | Spring Data JPA + Hibernate | (Spring Boot managed) |
| DB migration | Flyway | (Spring Boot managed) |
| Connection pool | HikariCP | (Spring Boot default) |
| Observability | Micrometer + OpenTelemetry | (Spring Boot managed) |
| Testing | JUnit 5, Testcontainers, MockMvc | |

### Key Maven Dependencies

```xml
<!-- HAPI FHIR -->
<dependency>
    <groupId>ca.uhn.hapi.fhir</groupId>
    <artifactId>hapi-fhir-structures-r4</artifactId>
    <version>7.4.0</version>
</dependency>
<dependency>
    <groupId>ca.uhn.hapi.fhir</groupId>
    <artifactId>hapi-fhir-validation</artifactId>
    <version>7.4.0</version>
</dependency>
<dependency>
    <groupId>ca.uhn.hapi.fhir</groupId>
    <artifactId>hapi-fhir-validation-resources-r4</artifactId>
    <version>7.4.0</version>
</dependency>

<!-- JSONLogic -->
<dependency>
    <groupId>io.github.jamsesso</groupId>
    <artifactId>json-logic-java</artifactId>
    <version>1.0.7</version>
</dependency>

<!-- Spring Boot starters -->
<!-- spring-boot-starter-web, spring-boot-starter-data-jpa, spring-kafka,
     spring-boot-starter-data-redis, spring-boot-starter-actuator,
     spring-boot-starter-validation -->
```

---

## Recommended Project Structure

```
cce-compliance-service/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/org/openphc/cce/compliance/
│   │   │   ├── ComplianceServiceApplication.java
│   │   │   ├── config/
│   │   │   │   ├── KafkaConsumerConfig.java
│   │   │   │   ├── KafkaProducerConfig.java
│   │   │   │   ├── FhirConfig.java              # FhirContext.forR4() singleton bean
│   │   │   │   ├── RedisConfig.java
│   │   │   │   ├── JpaConfig.java
│   │   │   │   └── JsonLogicConfig.java
│   │   │   ├── domain/
│   │   │   │   ├── model/
│   │   │   │   │   ├── PlanDefinitionEntity.java
│   │   │   │   │   ├── ProtocolInstance.java
│   │   │   │   │   ├── StepInstance.java
│   │   │   │   │   ├── Deviation.java
│   │   │   │   │   ├── TriggerIndexEntry.java
│   │   │   │   │   ├── EventLog.java
│   │   │   │   │   └── enums/
│   │   │   │   │       ├── ProtocolStatus.java      # active, completed, withdrawn, expired
│   │   │   │   │       ├── StepState.java            # pending, due, overdue, missed, completed, skipped
│   │   │   │   │       ├── DeviationType.java        # overdue, missed, ambiguous
│   │   │   │   │       ├── CompletionStatus.java     # on_time, early, late
│   │   │   │   │       ├── ProcessingStatus.java     # matched, zero_match, ambiguous, duplicate
│   │   │   │   │       └── TriggerMode.java          # data-added, named-event
│   │   │   │   └── repository/
│   │   │   │       ├── PlanDefinitionRepository.java
│   │   │   │       ├── ProtocolInstanceRepository.java
│   │   │   │       ├── StepInstanceRepository.java
│   │   │   │       ├── DeviationRepository.java
│   │   │   │       ├── TriggerIndexRepository.java
│   │   │   │       └── EventLogRepository.java
│   │   │   ├── engine/
│   │   │   │   ├── EventProcessor.java              # main orchestrator
│   │   │   │   ├── ProtocolInstanceResolver.java    # resolves patient → active protocol instances
│   │   │   │   ├── TriggerMatcher.java              # two-tier matching engine
│   │   │   │   ├── StructuralMatcher.java           # Tier 1: DataRequirement / named-event
│   │   │   │   ├── ConditionEvaluator.java          # Tier 2: JSONLogic / FHIRPath dispatch
│   │   │   │   ├── StepInstanceManager.java         # progressive instantiation & completion logic
│   │   │   │   ├── AmbiguityResolver.java           # multi-match disambiguation
│   │   │   │   └── TriggerIndexBuilder.java         # builds trigger_index at protocol load time
│   │   │   ├── kafka/
│   │   │   │   ├── InboundEventConsumer.java        # @KafkaListener for cce.events.inbound
│   │   │   │   ├── SchedulerTriggerConsumer.java    # @KafkaListener for cce.scheduler.triggers
│   │   │   │   └── IntelligenceTriggerProducer.java # publishes to cce.intelligence.triggers
│   │   │   ├── api/
│   │   │   │   ├── controller/
│   │   │   │   │   ├── ProtocolDefinitionController.java
│   │   │   │   │   ├── ProtocolInstanceController.java
│   │   │   │   │   ├── PatientComplianceController.java
│   │   │   │   │   └── PatientEventController.java
│   │   │   │   ├── dto/
│   │   │   │   │   ├── ApiResponse.java             # { "data": ... } envelope
│   │   │   │   │   ├── ApiError.java                # { "error": { "code", "message" } }
│   │   │   │   │   ├── PaginatedResponse.java
│   │   │   │   │   ├── ProtocolDefinitionDto.java
│   │   │   │   │   ├── ProtocolInstanceDto.java
│   │   │   │   │   ├── StepInstanceDto.java
│   │   │   │   │   └── PatientEventDto.java
│   │   │   │   └── exception/
│   │   │   │       └── GlobalExceptionHandler.java
│   │   │   └── fhir/
│   │   │       ├── PlanDefinitionParser.java        # HAPI FHIR parse/serialize
│   │   │       ├── DataRequirementEvaluator.java    # evaluates DataRequirement against resource
│   │   │       └── FhirResourceValidator.java       # validates incoming FHIR resources
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-local.yml
│   │       ├── application-staging.yml
│   │       ├── application-production.yml
│   │       └── db/migration/                        # Flyway migrations
│   │           ├── V1__create_plan_definition.sql
│   │           ├── V2__create_protocol_instance.sql
│   │           ├── V3__create_step_instance.sql
│   │           ├── V4__create_deviation.sql
│   │           ├── V5__create_trigger_index.sql
│   │           ├── V6__create_event_log.sql
│   │           └── V7__create_audit_log.sql
│   └── test/
│       └── java/org/openphc/cce/compliance/
│           ├── engine/
│           ├── kafka/
│           ├── api/
│           ├── fhir/
│           └── integration/
├── Dockerfile
├── docker-compose.yml                               # local dev: PostgreSQL, Kafka, Redis
├── .github/
│   └── copilot-instructions.md                      # this file
└── README.md
```

---

## Database Schema

The Compliance Service owns the following tables. Use Flyway for all schema migrations.

### Tables Owned by This Service

```sql
-- PlanDefinition stored as queryable JSONB
CREATE TABLE plan_definition (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    url          VARCHAR NOT NULL,
    version      VARCHAR NOT NULL,
    status       VARCHAR NOT NULL CHECK (status IN ('active', 'retired')),
    definition   JSONB NOT NULL,
    loaded_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (url, version)
);

CREATE INDEX idx_plan_definition_triggers
    ON plan_definition USING GIN (definition jsonb_path_ops);

-- Protocol instance — a patient's enrollment in a specific protocol
CREATE TABLE protocol_instance (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    patient_id          VARCHAR NOT NULL,
    protocol_canonical  VARCHAR NOT NULL,             -- PlanDefinition URL|version (pinned)
    plan_definition_id  UUID NOT NULL REFERENCES plan_definition(id),
    enrolled_at         TIMESTAMPTZ NOT NULL,
    status              VARCHAR NOT NULL CHECK (status IN ('active', 'completed', 'withdrawn', 'expired')),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_protocol_instance_patient ON protocol_instance (patient_id);
CREATE INDEX idx_protocol_instance_status  ON protocol_instance (status) WHERE status = 'active';

-- Step instance — individual step occurrence within a protocol instance
CREATE TABLE step_instance (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    protocol_instance_id  UUID NOT NULL REFERENCES protocol_instance(id),
    action_id             VARCHAR NOT NULL,
    repeat_index          INTEGER NOT NULL DEFAULT 0,
    state                 VARCHAR NOT NULL CHECK (state IN ('pending', 'due', 'overdue', 'missed', 'completed', 'skipped')),
    due_date              TIMESTAMPTZ,
    overdue_date          TIMESTAMPTZ,
    missed_date           TIMESTAMPTZ,
    completed_at          TIMESTAMPTZ,
    completed_by_source   VARCHAR,
    completion_status     VARCHAR CHECK (completion_status IN ('on_time', 'early', 'late')),
    matched_event_id      UUID,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_step_instance_protocol   ON step_instance (protocol_instance_id);
CREATE INDEX idx_step_instance_state      ON step_instance (state) WHERE state IN ('pending', 'due', 'overdue');
CREATE INDEX idx_step_instance_due_date   ON step_instance (due_date) WHERE state IN ('pending', 'due', 'overdue');

-- Deviation — recorded deviations from expected protocol pathway
CREATE TABLE deviation (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    protocol_instance_id  UUID NOT NULL REFERENCES protocol_instance(id),
    step_instance_id      UUID NOT NULL REFERENCES step_instance(id),
    deviation_type        VARCHAR NOT NULL CHECK (deviation_type IN ('overdue', 'missed', 'ambiguous')),
    detected_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    intelligence_event_id UUID,
    metadata              JSONB
);

CREATE INDEX idx_deviation_protocol ON deviation (protocol_instance_id);
CREATE INDEX idx_deviation_type     ON deviation (deviation_type);

-- Trigger index — inverted index for Tier 1 structural matching
CREATE TABLE trigger_index (
    resource_type       VARCHAR NOT NULL,
    code_system         VARCHAR,
    code_value          VARCHAR,
    plan_definition_id  UUID NOT NULL REFERENCES plan_definition(id),
    action_id           VARCHAR NOT NULL,
    trigger_mode        VARCHAR NOT NULL CHECK (trigger_mode IN ('data-added', 'named-event')),
    PRIMARY KEY (resource_type, COALESCE(code_system, ''), COALESCE(code_value, ''), plan_definition_id, action_id)
);

CREATE INDEX idx_trigger_index_resource ON trigger_index (resource_type);
CREATE INDEX idx_trigger_index_code     ON trigger_index (resource_type, code_system, code_value);

-- Event log — partitioned monthly by received_at
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
    UNIQUE (cloudevents_id, source)
) PARTITION BY RANGE (received_at);

CREATE UNIQUE INDEX idx_event_log_source_sourceeventid
    ON event_log (source, source_event_id) WHERE source_event_id IS NOT NULL;

CREATE INDEX idx_event_log_subject  ON event_log (subject);
CREATE INDEX idx_event_log_facility ON event_log (facility_id) WHERE facility_id IS NOT NULL;

-- Audit log — immutable, append-only
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
```

### Schema Conventions

- All PKs are `UUID DEFAULT gen_random_uuid()`
- All timestamps are `TIMESTAMPTZ` (always stored/queried in UTC)
- Status/state columns use `CHECK` constraints with enumerated values
- JSONB columns use GIN indexes with `jsonb_path_ops`
- Internal fields use `snake_case`
- CloudEvents extension attributes use `lowercase` (no separators) per CloudEvents spec
- Use Flyway versioned migrations (`V1__`, `V2__`, etc.) — never modify existing migrations

---

## Kafka Integration

### Topics This Service Consumes

| Topic | Key | Consumer Group | Purpose |
|-------|-----|----------------|---------|
| `cce.events.inbound` | `subject` (patient_id) | `compliance-service` | Care events from Collector |
| `cce.scheduler.triggers` | `subject` | `compliance-service` | Time-based transition requests from Scheduler |

### Topics This Service Produces

| Topic | Key | Purpose |
|-------|-----|---------|
| `cce.intelligence.triggers` | `subject` | Intelligence triggers (log-only for now — Intelligence system out of scope) |

### Kafka Consumer Configuration

```java
@KafkaListener(
    topics = "cce.events.inbound",
    groupId = "compliance-service",
    containerFactory = "kafkaListenerContainerFactory"
)
public void consumeEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
    // Process event
    // Manual acknowledgment after successful processing
    ack.acknowledge();
}
```

- Use `AckMode.MANUAL_IMMEDIATE` — acknowledge only after successful processing and DB commit
- Partition key is `subject` (patient_id) — guarantees per-patient ordering
- Idempotent writes — reprocessing the same event must not create duplicate state
- On unrecoverable error, publish to `cce.deadletter` topic and acknowledge
- Use `JsonDeserializer` or `StringDeserializer` + manual JSON parsing

### Consumer Scaling

- `cce.events.inbound` has **12 partitions**
- Deploy up to 12 Compliance Service instances (one consumer per partition)
- Initial deployment: **3 instances** (each consumes 4 partitions)

---

## REST API Endpoints

The Compliance Service serves these endpoints. All are accessed through the CCE Gateway (not directly by external callers). The Gateway forwards `user_id`, `facility_id`, `roles` as HTTP headers.

### Protocol Definitions

| Method | Path | Scope | Description |
|--------|------|-------|-------------|
| POST | `/v1/protocol-definitions` | `protocol-definitions:write` | Register a PlanDefinition |
| GET | `/v1/protocol-definitions` | `protocol-definitions:read` | List protocol definitions |
| GET | `/v1/protocol-definitions/{id}` | `protocol-definitions:read` | Get a PlanDefinition by ID |
| DELETE | `/v1/protocol-definitions/{id}` | `protocol-definitions:write` | Remove (only if not in use) |

**No PUT** — PlanDefinitions are immutable once active. Publish a new version instead.

### Protocol Instances

| Method | Path | Scope | Description |
|--------|------|-------|-------------|
| GET | `/v1/protocol-instances` | `protocol-tracking:read` | List instances (filter by patient, protocol, status, facility) |

### Patient Compliance

| Method | Path | Scope | Description |
|--------|------|-------|-------------|
| GET | `/v1/patients/{patient_id}/protocol-tracking` | `protocol-tracking:read` | All protocol instances for a patient |
| GET | `/v1/patients/{patient_id}/protocol-tracking/{protocol_instance_id}` | `protocol-tracking:read` | Tracking details with step instances |
| GET | `/v1/patients/{patient_id}/events` | `protocol-tracking:read` | Event timeline for patient |

### Response Envelope

```json
// Success (single resource)
{ "data": { ... } }

// Success (list)
{
  "data": [ ... ],
  "pagination": {
    "limit": 50,
    "next_cursor": "eyJpZCI6MTIzfQ==",
    "has_more": true
  }
}

// Error
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Missing required field: 'url'",
    "details": { "field": "url" }
  }
}
```

Use cursor-based pagination (not offset-based) for all list endpoints.

---

## Core Processing Algorithm

### Event Processing Flow (EventProcessor)

```
1. Deserialize Kafka record → CloudEvents envelope + data payload
2. Idempotency check (cloudevents_id + source)
   - Check PostgreSQL event_log unique constraint (full DB check, no Redis TTL shortcut)
   - If duplicate → log, set processing_status='duplicate', acknowledge, return
3. Parse FHIR resource from data payload (if datacontenttype = application/fhir+json)
   - Use HAPI FHIR FhirContext.forR4().newJsonParser()
4. Protocol Instance Resolution
   - If protocolinstanceid extension → use it directly (explicit routing)
   - If protocoldefinitionid extension → narrow to instances of that protocol
   - Otherwise → look up ALL active protocol instances for this patient (subject)
5. Trigger Matching
   a. Tier 1 — Structural match via trigger_index table
      - For data-added: resource type + codeFilter (type, status, class, serviceType)
      - For named-event: name string match
      - Query trigger_index for candidate (plan_definition_id, action_id) pairs
   b. Intersect candidates with patient's active protocol instances
   c. Tier 2 — Condition evaluation (JSONLogic) on remaining candidates
   d. Runtime filters — step state (pending/due/overdue only) + timing window
   e. If actionid extension → bypass inference, validate trigger + state only (explicit)
6. Ambiguity Resolution
   - Zero matches → processing_status='zero_match', log, no action
   - One match → proceed to step completion
   - Multiple matches within same protocol → apply disambiguation:
     Dependencies → State → Timing proximity → Fail-safe (log as 'ambiguous')
   - Cross-protocol matches → complete independently in each protocol
7. Step Completion
   - Mark step instance state='completed', completed_at=event.time
   - Determine completion_status: on_time / early / late (based on due_date)
   - Record matched_event_id
   - Record completed_by_source = event.source
8. Progressive Instantiation
   - Check if completed step unblocks downstream steps (relatedAction dependencies)
   - For each unblocked step: create new step_instance in 'pending' state
   - Compute due_date from relatedAction.offsetDuration relative to enrollment or dependency
   - For recurring steps: instantiate next repeat_index if count not exhausted
9. Persist event_log record with processing_status and matched_step_instance_id
10. Acknowledge Kafka offset
```

### Enrollment Handling

When an event matches a PlanDefinition's **enrollment action** trigger (typically the first `action` with `id: "enrollment"`):
1. Create a new `protocol_instance` with `status='active'`, `protocol_canonical` = PlanDefinition URL|version
2. Instantiate the first step(s) whose dependencies are immediately satisfied (pending state)
3. Compute due dates from `relatedAction.offsetDuration`

### Two-Tier Trigger Matching Detail

**Tier 1 — Structural (indexed, fast):**
- At protocol load time, `TriggerIndexBuilder` parses each action's trigger definitions and populates the `trigger_index` table
- For `data-added` triggers: extract `DataRequirement.type` + all `codeFilter` entries → one row per (resource_type, code_system, code_value, plan_definition_id, action_id)
- For `named-event` triggers: index by `name` string as resource_type
- At event arrival: query `trigger_index` by resource type + code values from the incoming data payload → returns candidate (plan_definition_id, action_id) pairs

**Tier 2 — Condition (runtime, expressive):**
- For candidates that passed Tier 1, load the action's `condition` expression
- Dispatch to evaluator based on `expression.language`:
  - `text/jsonlogic` → `JsonLogic.apply(expression, context)` using `json-logic-java`
  - `text/fhirpath` → HAPI FHIR FHIRPath engine (future)
- Context variable binding for JSONLogic:

| Variable | Source | Description |
|----------|--------|-------------|
| `resource.*` | CloudEvents `data` payload | Fields from the clinical resource |
| `stepState` | Step instance | Current state enum |
| `daysOverdue` | Computed | Days since step became overdue |
| `daysUntilDue` | Computed | Days until step is due |
| `patientId` | CloudEvents `subject` | Patient identifier |
| `facilityId` | CloudEvents `facilityid` extension | Facility identifier |
| `protocolVersion` | Protocol instance `protocol_canonical` | Version string |

---

## Step Instance Lifecycle

### State Machine

```
                    ┌─────────┐
                    │ pending │
                    └────┬────┘
                         │
              ┌──────────┼──────────┐
              │          │          │
              ▼          ▼          ▼
         ┌─────────┐ ┌─────────┐ ┌─────────┐
         │completed│ │   due   │ │ skipped │
         └─────────┘ └────┬────┘ └─────────┘
                          │
               ┌──────────┼──────────┐
               │          │          │
               ▼          ▼          ▼
          ┌─────────┐ ┌─────────┐ ┌─────────┐
          │completed│ │ overdue │ │ skipped │
          └─────────┘ └────┬────┘ └─────────┘
                           │
                ┌──────────┼──────────┐
                │          │          │
                ▼          ▼          ▼
           ┌─────────┐ ┌─────────┐ ┌─────────┐
           │completed│ │ missed  │ │ skipped │
           └─────────┘ └─────────┘ └─────────┘
```

### Transition Rules

| Transition | Trigger | Owner |
|------------|---------|-------|
| (created) → pending | Dependencies satisfied, progressive instantiation | **Compliance Service** |
| pending → completed | Event matches step trigger (early completion) | **Compliance Service** |
| pending → due | `due_date` reached | **Scheduler Service** (via Kafka) |
| pending → skipped | Explicit skip (optional steps only) | **Compliance Service** (API — future) |
| due → completed | Event matches step trigger | **Compliance Service** |
| due → overdue | `overdue_date` reached (`due_date + tolerance_days`) | **Scheduler Service** (via Kafka) |
| due → skipped | Explicit skip (optional steps only) | **Compliance Service** (API — future) |
| overdue → completed | Event matches step trigger (late completion) | **Compliance Service** |
| overdue → missed | `missed_date` reached | **Scheduler Service** (via Kafka) |
| overdue → skipped | Explicit skip (optional steps only) | **Compliance Service** (API — future) |

### Critical Business Rules (Customer Clarifications)

1. **No fast-path skipping:** When an event for a later step arrives (e.g., Step 5) while earlier steps (Step 3, 4) are incomplete, do NOT auto-skip intermediate steps. Each step must go through the full `due → overdue → missed` lifecycle before downstream steps are created via progressive instantiation.

2. **Mandatory steps cannot be skipped:** Steps with `requiredBehavior` = `"must"` (or absent, default mandatory) can transition to `due`, `overdue`, `missed`, or `completed` — never `skipped`. Only optional steps (`requiredBehavior: "could"`) may be skipped.

3. **Conflicting events:** If two events with different IDs contain conflicting data for the same clinical activity, log both events. Use last-write-wins — the later event's data is authoritative. Both events are recorded in `event_log`.

4. **Idempotency enforcement:** Full PostgreSQL DB check via `event_log` unique constraint on `(cloudevents_id, source)`. No time-bounded Redis shortcut — the DB constraint is the single source of truth for this service.

5. **Zero-match events:** Events matching no PlanDefinition or step are logged with `processing_status='zero_match'`. No intelligence alert is generated — logging is sufficient.

6. **Intelligence triggers:** Out of scope for now. When step state changes would generate intelligence triggers, log the trigger data but do not publish to Kafka `cce.intelligence.triggers` topic.

7. **Protocol withdrawal/cancellation:** Out of scope. The `withdrawn` status exists in the schema but no API or trigger to set it.

8. **No external system integrations:** The Compliance Service does not query Patient Registry, SHR, or any external systems. The shared patient ID in events (`subject`) is sufficient.

---

## Progressive Instantiation

Steps are created **only** when their dependencies are satisfied — not all at enrollment.

### How It Works

1. **At enrollment:** Only steps with no dependencies (or dependencies already met) are instantiated as `pending`
2. **On step completion:** Check `relatedAction` entries across the PlanDefinition — any step whose `relationship: "after-end"` dependency on the completed step is now satisfied gets instantiated
3. **On step missed:** Same check — `missed` also satisfies `after-end` dependencies (the step lifecycle is complete, downstream steps should still progress)
4. **Recurring steps:** When a repeat instance completes, instantiate the next `repeat_index` if `timingTiming.repeat.count` is not exhausted

### Due Date Computation

- `relatedAction.relationship: "after-start"` with `offsetDuration` → due_date = enrollment_date + offset
- `relatedAction.relationship: "after-end"` with `offsetDuration` → due_date = dependency_completion_date + offset
- `overdue_date` = `due_date` + `tolerance-days` extension value
- `missed_date` = protocol-defined cutoff (typically `overdue_date` + additional window, or a fixed protocol-level value)

---

## FHIR Handling

### FhirContext Configuration

```java
@Configuration
public class FhirConfig {
    @Bean
    public FhirContext fhirContext() {
        return FhirContext.forR4();  // Thread-safe singleton — expensive to create
    }
}
```

- **NEVER** hand-parse FHIR JSON — always use `fhirContext.newJsonParser().parseResource()`
- `FhirContext` is expensive to create — instantiate once as a Spring Bean
- Use `IParser.setPrettyPrint(false)` for Kafka/DB storage; `true` for API responses
- Parse PlanDefinition: `fhirContext.newJsonParser().parseResource(PlanDefinition.class, json)`
- Parse data payloads: `fhirContext.newJsonParser().parseResource(json)` (type-agnostic for unknown resource types)

### PlanDefinition Loading

When a PlanDefinition is registered via `POST /v1/protocol-definitions`:
1. Parse and validate the FHIR PlanDefinition using HAPI FHIR
2. Store the raw JSON in `plan_definition.definition` (JSONB)
3. Extract trigger metadata → populate `trigger_index` table entries
4. Set `status='active'`

### Trigger Index Building

```java
// For each action in the PlanDefinition:
for (PlanDefinition.PlanDefinitionActionComponent action : planDefinition.getAction()) {
    for (TriggerDefinition trigger : action.getTrigger()) {
        if (trigger.getType() == TriggerDefinition.TriggerType.DATAADDED) {
            for (DataRequirement dr : trigger.getData()) {
                String resourceType = dr.getType();
                for (DataRequirement.DataRequirementCodeFilterComponent cf : dr.getCodeFilter()) {
                    for (Coding code : cf.getCode()) {
                        // Insert into trigger_index:
                        // (resourceType, code.system, code.code, planDefId, action.id, "data-added")
                    }
                }
            }
        }
    }
}
```

---

## Scheduler Trigger Consumption

The Scheduler Service publishes time-based transition requests to `cce.scheduler.triggers`. The Compliance Service consumes these and applies the state transitions:

```json
{
  "type": "state_transition",
  "step_instance_id": "si-uuid-002",
  "from_state": "pending",
  "to_state": "due",
  "timestamp": "2026-02-15T00:00:00Z"
}
```

On receiving a scheduler trigger:
1. Load the step instance by ID
2. Validate current state matches `from_state` (guard against stale/duplicate triggers)
3. Apply the transition
4. If transition is `due → overdue` or `overdue → missed`, create a `deviation` record
5. If transition is `overdue → missed`, trigger progressive instantiation for dependent steps
6. Acknowledge

---

## Redis Usage (Compliance Service Scope)

The Compliance Service uses Redis for:

1. **Scheduler priority queue feed** — When creating step instances, `ZADD scheduler:due_queue {due_timestamp} {step_instance_id}` so the Scheduler knows about new steps
2. **Trigger index cache** (optional optimization) — Cache hot trigger_index entries to avoid DB round-trips on every event

Redis is **not** used by this service for idempotency (full DB check per customer requirement).

---

## Observability

### Metrics to Instrument

```java
// Events processed by outcome
Counter.builder("cce.events.processed")
    .tag("status", "matched")  // or zero_match, ambiguous, duplicate
    .register(meterRegistry);

// Step matching duration
Timer.builder("cce.step.matching.duration")
    .register(meterRegistry);

// Kafka consumer lag
Gauge.builder("cce.kafka.consumer.lag", ...)
    .register(meterRegistry);

// Protocol instances created
Counter.builder("cce.protocol.instances.created")
    .register(meterRegistry);

// Step transitions
Counter.builder("cce.step.transitions")
    .tag("from_state", "pending").tag("to_state", "completed")
    .register(meterRegistry);
```

### Logging Standards

- Use structured JSON logging (Logback + logstash-encoder)
- Always include `correlation_id` in MDC for every Kafka message processed
- Log at INFO: event received, processing outcome (matched/zero_match/ambiguous/duplicate), step transitions
- Log at WARN: ambiguous matches, zero matches
- Log at ERROR: unrecoverable processing failures (before dead-lettering)
- Include `patient_id`, `protocol_instance_id`, `step_instance_id` in structured log fields where available

### Health Checks

Expose via Spring Boot Actuator:
- `/actuator/health` — includes Kafka, PostgreSQL, Redis connectivity
- `/actuator/prometheus` — Micrometer metrics for Prometheus scraping

---

## Code Generation Guidelines

### General Patterns

- Use **constructor injection** exclusively — no `@Autowired` on fields
- Use `@RequiredArgsConstructor` (Lombok) or explicit constructors
- Entity classes: JPA `@Entity` with Hibernate
- Use `@Column(columnDefinition = "jsonb")` for JSONB fields
- Use `@Enumerated(EnumType.STRING)` for enum columns
- Use `Optional<T>` for nullable return values from repositories
- Use Java records for DTOs and value objects where appropriate
- Validate inputs with Bean Validation (`@Valid`, `@NotNull`, `@NotBlank`)

### Kafka Consumers

```java
@KafkaListener(
    topics = "${cce.kafka.topics.inbound}",
    groupId = "${cce.kafka.consumer-group}",
    containerFactory = "kafkaListenerContainerFactory"
)
public void onEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
    try {
        eventProcessor.process(record.value(), record.key());
        ack.acknowledge();
    } catch (RecoverableException e) {
        // Don't ack — Kafka will redeliver
        throw e;
    } catch (Exception e) {
        // Dead-letter and ack to avoid poison pill
        deadLetterProducer.send(record, e);
        ack.acknowledge();
    }
}
```

- Always use manual acknowledgment (`AckMode.MANUAL_IMMEDIATE`)
- Distinguish recoverable (rethrow for retry) vs. unrecoverable (dead-letter + ack) errors
- The Kafka partition key is `subject` (patient_id) — per-patient ordering is guaranteed

### Transaction Boundaries

- Use `@Transactional` on service methods that write to the database
- A single event's processing (event_log insert + step_instance update + deviation insert) should be in **one transaction**
- Kafka acknowledgment happens **after** transaction commit
- Keep transactions short — avoid holding DB locks across Kafka operations

### Error Handling

- `400 VALIDATION_ERROR` — invalid request body, missing required fields
- `404 NOT_FOUND` — resource does not exist
- `409 CONFLICT` — business rule violation (e.g., deleting an in-use PlanDefinition)
- `500 INTERNAL_SERVER_ERROR` — unexpected failures
- Use `@ControllerAdvice` with `@ExceptionHandler` for centralized error handling
- Always return the `{ "error": { "code", "message" } }` envelope

---

## Testing Strategy

### Unit Tests
- Engine classes (`TriggerMatcher`, `ConditionEvaluator`, `StepInstanceManager`, `AmbiguityResolver`) — pure logic, no Spring context
- FHIR parsing with test PlanDefinition fixtures
- JSONLogic condition evaluation with various contexts

### Integration Tests
- Use **Testcontainers** for PostgreSQL, Kafka, and Redis
- Test full event processing flow: Kafka message → event processing → DB state verification
- Test PlanDefinition registration → trigger index building
- Test progressive instantiation chains

### API Tests
- Use `@WebMvcTest` + `MockMvc` for controller tests
- Verify response envelopes, pagination, error formats

### Test Fixtures
- Maintain sample PlanDefinition JSON files in `src/test/resources/fixtures/`
- Include: simple linear protocol, protocol with optional steps, protocol with recurring steps, protocol with JSONLogic conditions

---

## Configuration (application.yml)

```yaml
spring:
  application:
    name: cce-compliance-service
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:cce}
    username: ${DB_USER:cce}
    password: ${DB_PASSWORD:cce}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
  jpa:
    hibernate:
      ddl-auto: validate  # Flyway manages schema
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
  flyway:
    enabled: true
    locations: classpath:db/migration
  kafka:
    bootstrap-servers: ${KAFKA_BROKERS:localhost:9092}
    consumer:
      group-id: compliance-service
      auto-offset-reset: earliest
      enable-auto-commit: false
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.apache.kafka.common.serialization.StringDeserializer
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
      acks: all
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}

cce:
  kafka:
    topics:
      inbound: cce.events.inbound
      scheduler-triggers: cce.scheduler.triggers
      intelligence-triggers: cce.intelligence.triggers
      dead-letter: cce.deadletter
    consumer-group: compliance-service

server:
  port: 8080

management:
  endpoints:
    web:
      exposure:
        include: health,prometheus,info
  metrics:
    tags:
      application: cce-compliance-service
```

---

## Docker & Local Development

### docker-compose.yml (local dev)

Include PostgreSQL 16, Kafka (KRaft, single broker), and Redis 7 for local development. Flyway runs automatically on application startup.

### Dockerfile

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/cce-compliance-service-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

Use multi-stage build for production: builder stage with Maven + JDK, runtime stage with JRE-alpine only.

---

## Deployment Notes

- **Stateless** — no in-memory state between requests. All state is in PostgreSQL.
- **Horizontally scalable** — add instances up to Kafka partition count (12)
- **Initial deployment:** 3 replicas, each consumes 4 Kafka partitions
- Resource allocation: 1.0 CPU request, 1 GB memory request per instance
- PgBouncer in transaction-mode pooling recommended for connection management
- No authentication/authorization logic in this service — the Gateway handles it and forwards claims as headers

---

## Out of Scope (Explicit Exclusions)

- Intelligence event generation and delivery (log trigger data only)
- Action definitions CRUD and action runs (provisionally handled — minimal stubs if needed)
- Protocol instance withdrawal/cancellation API
- Patient consent / data access revocation
- External system integrations (SHR, Patient Registry)
- Brownfield deployment / backfill of existing enrollments
