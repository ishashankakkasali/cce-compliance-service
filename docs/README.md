# CCE Compliance Service — Documentation

Welcome to the **CCE Compliance Service** documentation. This guide provides comprehensive technical and functional documentation for understanding, developing, and operating the service.

## Documentation Index

| Document | Description |
|---|---|
| [Architecture Overview](architecture-overview.md) | System context, component architecture, technology stack |
| [High-Level Design (HLD)](high-level-design.md) | Subsystem responsibilities, inter-service communication, security model |
| [Low-Level Design (LLD)](low-level-design.md) | Package structure, class relationships, service internals, algorithms |
| [Flow Diagrams](flow-diagrams.md) | Sequence & flowchart diagrams for all major workflows |
| [API Reference](api-reference.md) | REST endpoints, request/response schemas, authentication |
| [Data Model](data-model.md) | ER diagram, table schemas, JSONB structures, partitioning |
| [Kafka & Event Architecture](kafka-events.md) | Topics, message schemas, consumer/producer contracts |
| [Developer Setup & Configuration](developer-setup.md) | Local dev setup, configuration reference, Docker, environment variables |

## Quick Start

```bash
# Prerequisites: Java 21, Maven 3.9+, PostgreSQL 16, Kafka
# 1. Clone the repository
git clone <repo-url> && cd cce-compliance-service

# 2. Start infrastructure
docker compose up -d postgres kafka

# 3. Build
mvn clean package -DskipTests

# 4. Run
java -jar target/compliance-service-0.1.0-SNAPSHOT.jar
```

## Service Identity

| Property | Value |
|---|---|
| **Service Name** | `cce-compliance-service` |
| **Group ID** | `org.openphc.cce` |
| **Artifact ID** | `compliance-service` |
| **Java Version** | 21 LTS |
| **Spring Boot** | 3.4.2 |
| **Default Port** | 8080 |
