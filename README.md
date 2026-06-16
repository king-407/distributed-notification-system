# 🚀 Distributed Notification System

> A fault-tolerant event-driven notification platform built using Spring Boot microservices, Apache Kafka, Redis, and MySQL.

This project demonstrates how modern distributed backend systems achieve:
- asynchronous communication,
- reliable event publishing,
- retry handling,
- idempotency,
- eventual consistency,
- DLQ processing,
- and resilient microservice communication.

---

# ✨ Features

## ✅ Event-Driven Architecture
Microservices communicate asynchronously through Apache Kafka.

## ✅ Transactional Outbox Pattern
Prevents dual-write inconsistencies between database transactions and Kafka publishing.

## ✅ Eventual Consistency
Distributed services independently process events while eventually reaching consistent business state.

## ✅ Redis-based Idempotency
Prevents duplicate notification creation during retries or repeated client requests.

## ✅ Redis Fixed-Window Rate Limiting
Protects APIs from abuse and excessive request bursts.

## ✅ Retry Mechanisms
- Kafka publishing retries
- Email delivery retries
- Exponential backoff support

## ✅ Dead Letter Queue (DLQ)
Unrecoverable failures are routed to dedicated Kafka DLQ topics for debugging and replay.

## ✅ Failure Recovery
Handles:
- Kafka downtime
- Duplicate Kafka events
- SMTP failures
- Service crashes
- Retry exhaustion
- Temporary infrastructure failures

---

# 🏗️ High-Level Architecture

```text
                           ┌─────────────────────┐
                           │     Client/API      │
                           └──────────┬──────────┘
                                      │
                                      ▼
                    ┌────────────────────────────────┐
                    │      notification-service      │
                    └────────────────────────────────┘
                         │                 │
                         │                 │
                         ▼                 ▼
               notifications         outbox_events
                    (MySQL)             (MySQL)
                                              │
                                              ▼
                                   Outbox Scheduler
                                              │
                                              ▼
                         Kafka Topic: notification-events-topic
                                              │
                                              ▼
                    ┌────────────────────────────────┐
                    │          email-service         │
                    └────────────────────────────────┘
                         │                 │
                         │                 │
                         ▼                 ▼
                   sent_emails        outbox_events
                      (MySQL)             (MySQL)
                         │
                         ▼
                  Email Retry Scheduler
                         │
                         ▼
                  SMTP / Email Sender
                         │
                         ▼
               notification-status-topic
                         │
                         ▼
                    notification-service
                         │
                         ▼
               notifications.status updated
```

---

# 🧠 System Design Highlights

## 🔹 Transactional Outbox Pattern

Instead of directly publishing Kafka events after DB writes, events are first persisted inside an `outbox_events` table.

A scheduler later publishes those events reliably to Kafka.

This guarantees:
- no event loss,
- retry capability,
- eventual consistency,
- safe recovery from Kafka downtime.

---

## 🔹 Dual Retry Architecture

The system separately retries:

### 1. Email Delivery Retry
Retries actual SMTP/email delivery failures.

Stored in:
```text
sent_emails.retryCount
```

### 2. Kafka Publish Retry
Retries failed Kafka publishing of outbox events.

Stored in:
```text
outbox_events.retryCount
```

This separation models real production-grade distributed systems.

---

## 🔹 Idempotency Handling

Duplicate API requests are prevented using Redis idempotency keys.

```text
Idempotency-Key: abc-123
```

Repeated requests with same key:
- do not create duplicate notifications,
- do not create duplicate Kafka events.

---

## 🔹 Dead Letter Queue (DLQ)

Permanent failures are routed to:

```text
email-dlq-topic
```

Used for:
- debugging,
- replay,
- operational monitoring.

---

# 🧩 Microservices

---

# 1️⃣ notification-service

Responsible for:
- receiving notification requests,
- saving notification records,
- handling idempotency,
- rate limiting,
- publishing Kafka notification events,
- consuming final email status updates.

### Key Components

| Component | Responsibility |
|---|---|
| NotificationController | REST API layer |
| NotificationService | Core business logic |
| IdempotencyService | Redis-based duplicate protection |
| RateLimiterService | Redis fixed-window rate limiting |
| OutboxScheduler | Reliable Kafka publishing |
| NotificationStatusConsumer | Consumes EMAIL_SENT / EMAIL_FAILED |

---

# 2️⃣ email-service

Responsible for:
- consuming notification events,
- sending emails asynchronously,
- retrying failed email deliveries,
- publishing final email delivery status,
- DLQ handling.

### Key Components

| Component | Responsibility |
|---|---|
| EmailNotificationConsumer | Kafka consumer |
| EmailService | Core email processing logic |
| EmailSenderService | SMTP/email sending |
| EmailRetryScheduler | Retries failed email sends |
| OutboxScheduler | Reliable Kafka publishing |
| DlqService | Handles unrecoverable failures |

---

# 📦 Kafka Topics

| Topic | Purpose |
|---|---|
| `notification-events-topic` | Notification creation events |
| `notification-status-topic` | Final email delivery status |
| `email-dlq-topic` | Failed unrecoverable events |

---

# 🗄️ Database Design

## notification_db

### notifications

Tracks business notification lifecycle.

| Status |
|---|
| PENDING |
| SENT |
| FAILED |

### outbox_events

Tracks reliable Kafka event publishing.

| Status |
|---|
| PENDING |
| FAILED |
| PUBLISHED |
| DEAD_LETTERED |

---

## email_db

### sent_emails

Tracks actual email delivery lifecycle.

| Status |
|---|
| PROCESSING |
| RETRY_PENDING |
| SENT |
| FAILED |

### outbox_events

Tracks reliable Kafka status event publishing.

---

# 🔁 End-to-End Success Flow

```text
Client Request
→ notification-service
→ Save Notification
→ Save Outbox Event
→ Kafka Publish
→ email-service Consumes Event
→ Email Sent Successfully
→ Save EMAIL_SENT Outbox Event
→ Kafka Publish
→ notification-service Consumes EMAIL_SENT
→ Notification marked SENT
```

---

# ❌ Failure Flow Example

```text
Client Request
→ Kafka Publish Success
→ email-service Consumes Event
→ SMTP Failure
→ Retry Scheduled
→ Retries Exhausted
→ EMAIL_FAILED Outbox Event Created
→ Kafka Publish
→ notification-service Consumes EMAIL_FAILED
→ Notification marked FAILED
→ Failure Routed to DLQ
```

---

# ⚙️ Tech Stack

| Technology | Usage |
|---|---|
| Java 21 | Backend Language |
| Spring Boot | Microservices Framework |
| Spring Kafka | Kafka Integration |
| Spring Data JPA | ORM Layer |
| Apache Kafka | Event Streaming |
| Redis | Idempotency + Rate Limiting |
| MySQL | Persistent Storage |
| Docker Compose | Local Infrastructure |
| Lombok | Boilerplate Reduction |
| Maven | Dependency Management |

---

# 📂 Project Structure

```text
distributed-notification/
│
├── common-events/
│
├── notification-service/
│
├── email-service/
│
├── docker-compose.yml
│
└── mysql-init/
```

---

# 🚀 Running the Project

## 1️⃣ Start Infrastructure

```bash
docker compose up -d
```

---

## 2️⃣ Start notification-service

```bash
cd notification-service
mvn spring-boot:run
```

---

## 3️⃣ Start email-service

```bash
cd email-service
mvn spring-boot:run
```

---

# 🧪 API Testing

## Create Notification

### Endpoint

```http
POST /api/v1/notifications
```

### Headers

```text
Idempotency-Key: abc-123
```

### Request Body

```json
{
  "recipient": "shivam@gmail.com",
  "subject": "Welcome",
  "message": "Welcome to NotifyX"
}
```

---

# 🔍 Failure Scenarios Tested

- ✅ Kafka broker downtime
- ✅ Duplicate API requests
- ✅ Duplicate Kafka delivery
- ✅ Email service downtime
- ✅ Kafka publish failures
- ✅ SMTP failures
- ✅ Retry exhaustion
- ✅ Scheduler recovery
- ✅ DLQ routing
- ✅ Eventual consistency recovery

---

# 📈 Future Improvements

- Kubernetes Deployment
- API Gateway
- Authentication & Authorization
- Prometheus + Grafana Monitoring
- Distributed Tracing
- OpenTelemetry
- Email Templates
- Multi-channel Notifications (SMS/Push)
- Circuit Breakers
- Saga Pattern

---

# 👨‍💻 Author

## Shivam Tiwari

Backend Engineer passionate about Java, AI, distributed systems, scalable backend architectures, and event-driven microservices.

---
