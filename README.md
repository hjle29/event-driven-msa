## Event-Driven-MSA
Focusing on Distributed Data Consistency and System Resilience

## 🎯 Overview
This project was initiated to address tight coupling and cascading failure issues common in synchronous REST-based architectures. The goal is to build a practical reference for a resilient system that ensures data integrity across distributed services using asynchronous event-driven communication.

## 🛠 Engineering Focus (What I solved)
1. Decoupling via Event-Driven Architecture
Problem: Direct service-to-service dependencies caused bottlenecks and shared failure points.

Solution: Integrated Apache Kafka to decouple services. This ensures that a delay or failure in downstream services (like Settlement) does not block upstream processes (like Ordering).

2. Distributed Transactions (Saga Pattern)
Problem: Impossible to use RDBMS @Transactional across multiple service-specific databases.

Solution: Applied the Choreography-based Saga Pattern. By implementing Compensating Transactions for each step, the system maintains Eventual Consistency even when a process fails mid-way.

3. Operational Observability
Philosophy: "You cannot manage what you cannot measure."

Implementation: Integrated Prometheus and Grafana to track system health. Beyond basic metrics, I focused on monitoring Kafka Consumer Lag to identify and resolve processing bottlenecks.

4. Defense in Depth (Fault Tolerance)
Resilience: Implemented Circuit Breakers using Resilience4j to prevent total system collapse under heavy load or partial outages.

Reliability: Designed Retry strategies to handle transient network issues without data loss.

## 🏗 Technical Decisions (Why?)
Gradle Multi-module: Chosen to keep service boundaries strict while efficiently managing shared event schemas (DTOs) in a Common module.

Message Idempotency: Since duplicate message delivery is inevitable in distributed systems, I prioritized idempotency logic in the consumer design to prevent data duplication.

## 🚀 Tech Stack
Core: Java 17, Spring Boot 3.x, Spring Cloud

Messaging: Apache Kafka

Storage: MySQL, Redis

Ops: Docker Compose, Prometheus, Grafana
