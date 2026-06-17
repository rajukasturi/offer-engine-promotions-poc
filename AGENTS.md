# Offer Engine Promotions - AI Agent Guide

## Project Overview

A Spring Boot 3.5.15 microservice that manages customer-merchant promotion offers with a dual-storage strategy: PostgreSQL for relational queries and Redis for high-performance lookups. Java 21 with virtual threads for non-blocking I/O.

**Three Core Modules**: Generator → Loader → Evaluation

## Architecture & Data Flow

### Three Functional Modules

**1. Generator** (`/generator`) - Test Data Creation
- `MockDataController` exposes `/api/v1/mock-data/generate` endpoint (sync, returns timing)
- Generates CSV files: `merchants.csv`, `customers.csv`, `offers.csv` 
- Output location: `${file-directory}` (property in `application.properties`)
- Scalable: accepts `customerCount` query param; auto-generates 500 merchants + 5K+ offers per merchant

**2. Loader** (`/loader`) - Batch ETL Pipeline  
- `IngestionController` orchestrates `/api/v1/ingest/local-files` (async via virtual threads)
- **Spring Batch job** (`dataPipelineJob`) runs 4 sequential steps:
  1. **Step 1**: CSV → PostgreSQL (merchants, customers, offers tables)
  2. **Step 2**: CSV → PostgreSQL (customers with `ON CONFLICT email DO NOTHING`)
  3. **Step 3**: CSV → PostgreSQL (offers)
  4. **Step 4**: Dual-write to both PostgreSQL and Redis simultaneously:
     - Reads all customers, cross-joins with all offers
     - Writes to `customer_offers` table (AVAILABLE status)
     - Caches as JSON arrays in Redis with key: `customer_offers:{customerId}:{merchantId}`
- **Key Optimization**: `PrecomputedOffer` record + object reference reuse prevents memory explosion at scale
- Chunk sizes: 10,000 (CSV), 200 (mapping generation) - NOT configurable
- Virtual threads prevent thread pool exhaustion during batch execution
- Returns immediately with tracking ID; job runs asynchronously

**3. Evaluation** (`/evaluation`) - Query Service
- `OfferEvaluationController` provides two endpoints:
  - **Database path**: `/api/v1/evaluations/db/customers/{customerId}/merchants/{merchantId}`
    - Uses `JdbcClient` via `CustomerOfferQueryRepository`
    - First time reads are slow; returns `OfferEvaluationResponse` records
  - **Redis path**: `/api/v1/evaluations/redis/customers/{customerId}/merchants/{merchantId}`
    - Direct string lookup; returns pre-formatted JSON array (milliseconds)
    - Key format must match exactly: Redis keys are lowercase with colons

### Data Models (All Java Records)

```
Merchants: merchant_id | business_name | mcc_code | created_at
Customers: customer_id | name | email | wallet_balance | created_at
Offers: offer_id | merchant_id | reward_type | reward_value | min_spend | max_cap | start_date | end_date | created_at
CustomerOffers: customer_id | offer_id | merchant_id | status (AVAILABLE/REDEEMED) | progress | assigned_at
```

**Redis JSON Format** (custom-built, not Jackson):
```json
[{"offerId":123,"rewardType":"PERCENTAGE","rewardValue":15.5,"minSpend":20.00,"maxCap":50.00}, ...]
```

## Critical Developer Workflows

### Build & Run
```bash
# Build JAR (Maven with Spring Boot plugin)
mvn clean package

# Run application (Spring Web port 8080)
java -jar target/offer-engine-promotions-0.0.1-SNAPSHOT.jar

# Requirements: PostgreSQL 13+, Redis 6+, Java 21
```

### Test Data Pipeline (From Scratch)
1. **Generate test data**: `POST /api/v1/mock-data/generate?customerCount=100000` (JSON response with timing)
2. **Wait** for files in `E:\Softwares\workspace\offer-engine-files\` directory
3. **Trigger batch ingestion**: `POST /api/v1/ingest/local-files` 
   ```json
   {
     "merchantFilePath": "E:\\Softwares\\workspace\\offer-engine-files\\merchants.csv",
     "customerFilePath": "E:\\Softwares\\workspace\\offer-engine-files\\customers.csv",
     "offerFilePath": "E:\\Softwares\\workspace\\offer-engine-files\\offers.csv"
   }
   ```
4. **Query results**:
   - Via DB: `GET /api/v1/evaluations/db/customers/1/merchants/5`
   - Via Redis: `GET /api/v1/evaluations/redis/customers/1/merchants/5`

### Database Schema Prerequisites
Create tables manually or rely on Spring Batch metadata tables (`BATCH_JOB_*`). **No JPA/Hibernate**: all JDBC.

## Project-Specific Patterns & Conventions

### 1. Virtual Threads for Async Operations
- **Location**: `IngestionController.triggerLocalIngestion()` calls `Thread.ofVirtual().start()`
- **Purpose**: Non-blocking batch job execution without thread pool exhaustion
- **Pattern**: Never use traditional `ExecutorService`; use virtual threads for I/O-bound tasks

### 2. Dual-Write Strategy for Cache Invalidation
- Every INSERT into `customer_offers` also writes to Redis in the SAME batch
- **No eventual consistency**: Both databases updated atomically within transaction
- **Trade-off**: Higher write latency, but guarantees consistency for read paths

### 3. Memory-Efficient Batch Processing
- `CustomerOfferMappingWriter`: Flattens nested lists from processor before batch write
- `PrecomputedOffer` record: Single object reference shared across all 100K+ customer iterations
- **Pattern**: Pre-compute domain objects ONCE in processor, reuse references in writer
- Prevents creation of 7.5B intermediate objects (100K customers × 7.5K offers)

### 4. Direct JSON Construction in Redis Writer
- Line 81-82 in `CustomerOfferMappingWriter`: Manually builds JSON strings with `StringBuilder`
- **Why**: Faster than Jackson serialization; avoids dependency on JSON library behavior
- **Pattern**: Acceptable for performance-critical paths only; use Jackson for API responses

### 5. Spring Batch Configuration (Not Spring Data)
- No `@Entity` classes; uses records for read models
- `@StepScope` beans refresh per job execution (safe for file paths from job parameters)
- `FlatFileItemReader` with `RecordFieldSetMapper` for CSV parsing
- `JdbcCursorItemReader` for database reads (avoids loading entire table into memory)
- All writers: `JdbcBatchItemWriter` or custom `ItemWriter` implementations

### 6. API Versioning
- All endpoints start with `/api/v1/` (future migration path: `/api/v2/`)
- Response codes: 200 (found), 204 (not found), 202 (async accepted), 500 (error)

## Integration Points & External Dependencies

### PostgreSQL
- **JDBC Driver**: `postgresql:21.1.1` (auto-managed by Boot parent)
- **Connection Pool**: HikariCP (configured in `application.properties`)
  - Max pool: 30 threads (safe for 10K-100K customer batches)
  - Batch inserts enabled: `reWriteBatchedInserts=true` in JDBC URL
- **Key Tables**: merchants, customers, offers, customer_offers (Spring Batch metadata tables auto-created)

### Redis
- **Client**: `spring-boot-starter-data-redis` → Lettuce driver
- **Serialization**: Custom string serialization (no Jackson object mapper)
- **Key Naming**: `customer_offers:{customerId}:{merchantId}` (lowercase, colon-delimited)
- **TTL**: Not configured (keys persist indefinitely until manual deletion)

### Spring Batch Framework
- Job parameter passing: `JobParametersBuilder` injects file paths at runtime
- Metadata storage: PostgreSQL `spring_batch_*` tables (auto-initialized if `spring.batch.jdbc.initialize-schema=always`)
- Job execution IDs: UUID generated per ingestion request (ensures idempotence)

## Key Files Reference

| Component | Entry Point | Key Logic |
|-----------|------------|-----------|
| Generator | `generator/controller/MockDataController.java` | Lines 24-45: endpoint + timing |
| Generator Logic | `generator/service/DataGenerationService.java` | Lines 25-51: orchestration; lines 66-96: offer creation with random counts |
| Loader Config | `loader/config/BatchConfig.java` | Line 36-37: chunk sizes; lines 139-161: dual-write processor |
| Loader Writer | `loader/service/CustomerOfferMappingWriter.java` | Lines 62-105: Redis JSON construction pipeline |
| Evaluation | `evaluation/service/OfferEvaluationService.java` | Lines 25-26: DB query; lines 32-36: Redis fetch |
| Query Repo | `evaluation/repository/CustomerOfferQueryRepository.java` | Lines 18-38: SQL with status filter |
| Application | `OfferEnginePromotionsApplication.java` | Standard Spring Boot entry point |
| Config | `application.properties` | Lines 11-25: DB/Redis credentials; lines 27-29: Batch job settings |

## Editing Guidelines for AI Agents

### Safe Zones (Low Risk Changes)
- **Controllers**: Add new endpoints; modify request/response handlers
- **Service logic**: Add query methods; extend evaluation algorithms
- **Models**: Add new record fields (test compatibility with CSV parsing)
- **Tests**: All test changes safe

### Risky Zones (High Complexity)
- **BatchConfig**: Chunk sizes impact memory; job order matters
- **RedisOfferCache**: Field names must align with JSON construction in `CustomerOfferMappingWriter`
- **Database schema**: Changes require migration script; affects customer_offers creation
- **Virtual thread usage**: Only for I/O-bound operations; do not parallelize CPU-bound logic
- **API contracts**: Changing endpoint paths/params breaks client integrations

### Testing Strategy
- No integration tests in codebase (empty `@SpringBootTest`)
- Manual testing via endpoints recommended
- Verify data in PostgreSQL: `SELECT COUNT(*) FROM customer_offers;`
- Verify data in Redis: `redis-cli GET "customer_offers:1:1"` returns JSON array

## Build & Deployment Commands

```bash
# Compile
mvn clean compile

# Test (minimal coverage)
mvn test

# Package
mvn clean package -DskipTests

# Run with custom properties
java -jar target/offer-engine-promotions-0.0.1-SNAPSHOT.jar \
  --spring.datasource.url=jdbc:postgresql://prod-db:5432/offers \
  --spring.data.redis.host=prod-redis
```

---

**Generated**: June 16, 2026 | **Version**: offer-engine-promotions 0.0.1-SNAPSHOT | **Java**: 21

