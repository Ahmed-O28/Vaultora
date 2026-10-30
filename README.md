# Online Banking System (OBS)

> A modern, full-stack digital banking platform and distributed systems showcase built with **Spring Boot 3**, **React 18**, **PostgreSQL Sharding**, and **Redis**.

---

## Table of Contents
- [About the Project](#-about-the-project)
- [System Design Building Blocks](#-system-design-building-blocks)
- [Core Features](#-core-features)
- [Architecture Overview](#-architecture-overview)
- [Tech Stack](#-tech-stack)
- [Project Structure](#-project-structure)
- [Getting Started](#-getting-started)
- [Pre-configured Demo Credentials](#-pre-configured-demo-credentials)
- [Key API Endpoints](#-key-api-endpoints)
- [Troubleshooting & FAQ](#-troubleshooting--faq)

---

## About the Project

The **Online Banking System (OBS)** is an end-to-end financial platform that bridges real-world banking functionality with practical distributed systems architecture.

Beyond standard banking capabilities (money transfers, utility bill payments, transaction histories), this project incorporates foundational **System Design building blocks**—such as database horizontal sharding, consistent hashing, distributed unique ID generation, token bucket rate limiting, Redis caching, and a URL shortening service for tamper-proof digital receipts.

---

## System Design Building Blocks

This application demonstrates 7 real-world system design patterns in action:

1. **Horizontal Database Sharding**: Data is distributed across **4 separate PostgreSQL database instances** (`bank_shard1`, `bank_shard2`, `bank_shard3`, `bank_shard4`), proving horizontal scalability beyond a single database bottleneck.
2. **Consistent Hashing with Virtual Nodes**: An MD5-based consistent hash ring maps user accounts deterministically to shards, ensuring even data distribution and minimal remapping during node scale-up/scale-down.
3. **Token Bucket Rate Limiting**: Interceptor-level defense preventing DDoS, brute-force attacks, and API exhaustion with configurable token refills and HTTP `429 Too Many Requests` + `Retry-After` response headers.
4. **Distributed Unique ID Generator**: High-throughput, collision-resistant identifier generator producing global 64-bit unique IDs for transactions and receipts.
5. **Distributed Key-Value Caching**: Redis-backed cache layer for ultra-low latency lookups of account balances, active user sessions, and short URL mappings.
6. **URL Shortener Engine (TinyURL)**: In-house Base62 short-link service creating compact, shareable URLs (`/r/{code}`) for downloadable and verifiable digital transaction receipts.
7. **Trie Prefix Autocomplete**: High-performance in-memory prefix tree providing instant autocomplete for searching billers and saved beneficiaries.

---

## Core Features

### 👤 Customer Experience
* **Authentication & Security**: Stateless JWT-based authentication with BCrypt password hashing.
* **Account Dashboard**: Real-time balance display, account details, and quick action shortcuts.
* **Instant Money Transfers**: P2P transfers protected by mandatory 4-digit security **MPIN** verification.
* **Saved Beneficiaries**: Save frequent transfer contacts for 1-click transfers.
* **Utility Bill Payments**: Instant settlement for Electricity, Water, Gas, Internet, and Mobile providers with reference validation.
* **Shareable Digital Receipts**: Every transfer and bill payment generates a shareable, short-link receipt viewable by anyone with the link.
* **Transaction History**: Searchable, filterable ledger of debits, credits, and bill payments.
* **Live Market FX Rates**: Real-time currency exchange rates widget.
* **Customer Notifications**: In-app inbox alerting users to transfers, payments, and account events.

### 🛡️ Admin & System Health Portal
* **System Metrics Dashboard**: Real-time throughput, active connections, and latency tracking powered by Spring Boot Actuator.
* **Shard Distribution Visualizer**: Live view of customer and transaction distribution across all 4 database shards.
* **Shard Migration Utility**: Run automated data rebalancing across shards via `migrate_shards.py`.
* **Biller Management**: Add new utility billers, update billing categories, and toggle provider status (Active/Inactive).
* **Global Auditing**: Inspect customer accounts and system-wide transactions across all shards with safety masking.

---

## Architecture Overview

```text
               +----------------------------------------+
               |        React 18 + Vite Frontend        |
               |        (Port 3000 / Modern Dark UI)    |
               +-------------------+--------------------+
                                   |
                                   | HTTP / REST (JWT)
                                   v
               +----------------------------------------+
               |       Token Bucket Rate Limiter        |
               +-------------------+--------------------+
                                   |
                                   v
               +----------------------------------------+
               |       Spring Boot 3.3.5 Backend        |
               |               (Port 8081)              |
               |                                        |
               |  * Trie Autocomplete   * ID Generator   |
               |  * JWT Auth Guard      * URL Shortener  |
               +----------+-------------------+---------+
                          |                   |
            Fast Lookup   |                   | Shard Routing
            & Session     v                   v
              +---------------+   +-----------------------+
              |    Redis 7    |   | Consistent Hash Ring  |
              |  (Port 6380)  |   +-----------+-----------+
              +---------------+               |
                                              |
                   +--------------+-----------+--+--------------+
                   |              |              |              |
                   v              v              v              v
             +----------+   +----------+   +----------+   +----------+
             | Shard 1  |   | Shard 2  |   | Shard 3  |   | Shard 4  |
             | Postgres |   | Postgres |   | Postgres |   | Postgres |
             |Port: 5432|   |Port: 5433|   |Port: 5434|   |Port: 5435|
             +----------+   +----------+   +----------+   +----------+
```

---

## Tech Stack

| Layer | Technologies |
| :--- | :--- |
| **Backend** | Java 21, Spring Boot 3.3.5, Spring Data JPA, Hibernate, Spring Security Crypto (BCrypt), JJWT |
| **Frontend** | React 18, Vite 6, React Router DOM 6, Custom Vanilla CSS (Glassmorphism & Dark Mode) |
| **Databases** | 4x PostgreSQL 16 Instances (Horizontal Sharding) |
| **Caching** | Redis 7 Alpine |
| **Observability** | Spring Boot Actuator |
| **Orchestration** | Docker & Docker Compose |

---

## Project Structure

```text
OnlineBankingSystem/
├── docker-compose.yml              # Multi-shard PostgreSQL (1-4) & Redis definitions
├── backend/
│   ├── pom.xml                     # Maven project configuration (Java 21, Spring Boot 3)
│   ├── scripts/
│   │   └── migrate_shards.py       # Shard migration & rebalancing script
│   └── src/main/java/com/banking/
│       ├── config/                 # Security, JWT interceptor, RateLimiter filter, DataInitializer
│       ├── controller/             # REST APIs (Customer, Transfer, BillPay, Admin, ShortUrl, etc.)
│       ├── dto/                    # Request and response data transfer objects
│       ├── model/                  # JPA entities (Customer, Account, Transaction, Biller, Admin)
│       ├── repository/             # Spring Data repositories
│       ├── service/                # Business logic services
│       └── systemdesign/           # System design building blocks
│           ├── autocomplete/       # Trie-based prefix autocomplete
│           ├── consistenthashing/  # Consistent hashing ring implementation
│           ├── idgenerator/        # Distributed unique ID generator
│           ├── kvstore/            # Key-value caching layer
│           ├── ratelimiter/        # Token bucket rate limiting filter
│           ├── sharding/           # Shard connection manager & router
│           └── urlshortener/       # TinyURL Base62 receipt shortener
└── frontend/
    ├── package.json                # React 18 dependencies & Vite configuration
    ├── vite.config.js              # Proxy rules (/api -> :8081, /r/ -> :8081)
    └── src/
        ├── components/             # Reusable UI components (AppShell, Navbar, Modal)
        ├── pages/                  # Dashboard, Transfer, BillPay, Transactions, Admin, etc.
        └── services/               # API clients for backend integration
```

---

## Getting Started

### 📋 Prerequisites
Make sure you have the following installed on your machine:
* [Docker & Docker Compose](https://docs.docker.com/get-docker/)
* [Java 21 Development Kit (JDK)](https://adoptium.net/)
* [Apache Maven 3.9+](https://maven.apache.org/)
* [Node.js 18+ and npm](https://nodejs.org/)

---

### Step 1: Start Databases & Redis (Docker)
In the project root (where `docker-compose.yml` is located), spin up the 4 PostgreSQL shards and the Redis instance:

```bash
docker compose up -d
```

Verify that all 5 containers are running:
```bash
docker compose ps
```

---

### Step 2: Start the Spring Boot Backend
Navigate into the `backend` directory and run the application:

```bash
cd backend
./mvnw spring-boot:run
# or if you have maven installed globally:
mvn spring-boot:run
```

The backend server will start on **`http://localhost:8081`**.
> Note: On first run, `DataInitializer` will automatically seed the initial admin account, demo customers, accounts, and biller catalog.

---

### Step 3: Start the React Frontend
Open a new terminal, navigate into the `frontend` directory, and start the development server:

```bash
cd frontend
npm install
npm run dev
```

The frontend will run on **`http://localhost:3000`** (or `http://localhost:5173`).

Open your browser and navigate to **`http://localhost:3000`** to access the application!

---

## Pre-configured Demo Credentials

The database automatically seeds test accounts ready for instant testing:

### Customer Accounts (User Portal)
* **Default Password for all customers:** `password123`
* **Default Security MPIN for transfers:** `1234`

| Customer Name | Email | Account Number | Starting Balance | Primary Shard |
| :--- | :--- | :--- | :--- | :--- |
| **Alice Smith** | `alice@bank.local` | `ACC-1001` | $5,000.00 | Shard 1 |
| **Bob Jones** | `bob@bank.local` | `ACC-1002` | $3,500.00 | Shard 1 |
| **Charlie Brown** | `charlie@bank.local` | `ACC-2001` | $10,000.00 | Shard 2 |
| **Diana Prince** | `diana@bank.local` | `ACC-2002` | $2,500.00 | Shard 2 |

### Administrator Account (Admin Portal)
* **URL:** `http://localhost:3000/admin/login`
* **Username / Email:** `admin` or `admin@bank.local`
* **Password:** `admin123`

---

## Key API Endpoints

| Category | Method | Endpoint | Description |
| :--- | :--- | :--- | :--- |
| **Auth** | `POST` | `/api/customers/register` | Register a new customer |
| **Auth** | `POST` | `/api/customers/login` | Authenticate customer & receive JWT |
| **Accounts** | `GET` | `/api/customers/{id}/accounts` | Fetch customer accounts & live balances |
| **Transfers** | `POST` | `/api/transfers` | Execute P2P transfer with MPIN validation |
| **Bills** | `POST` | `/api/bills/pay` | Pay utility bill (Electricity, Gas, etc.) |
| **Receipts**| `POST` | `/api/shorten` | Generate Base62 short URL for transaction receipt |
| **Receipts**| `GET` | `/r/{shortCode}` | Public redirect to digital receipt |
| **Search** | `GET` | `/api/autocomplete/billers?q=...` | Trie prefix autocomplete for billers |
| **Admin** | `POST` | `/api/admin/login` | Admin authentication |
| **Admin** | `GET` | `/api/admin/stats` | System health & shard distribution metrics |
| **Admin** | `GET` | `/api/admin/customers` | Search & audit customers across all shards |
| **Metrics** | `GET` | `/actuator/metrics` | Spring Boot Actuator performance metrics |

---

## Troubleshooting

1. **Database connection error on backend startup?**
   Make sure Docker containers are running (`docker compose ps`). Shards 1 through 4 listen on local ports `5432`, `5433`, `5434`, and `5435`.
2. **Rate Limit exceeded (`429 Too Many Requests`)?**
   The built-in Token Bucket Rate Limiter restricts rapid-fire API hits. Wait 15 seconds for bucket replenishment.
3. **Receipt link not opening?**
   Vite's proxy is configured to route `/r/*` requests directly to backend port `8081`. Ensure both frontend and backend are running simultaneously.

---

