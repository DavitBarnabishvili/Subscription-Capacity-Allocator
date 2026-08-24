# Fund Subscription Capacity Allocator

REST service that decides which fund subscription requests to accept,
maximizing total fee revenue without exceeding the fund's capacity.
Every submitted request is persisted whether it was accepted or not, 
meaning past allocation decisions can be audited.

This is a 0/1 knapsack problem with an added complexity of remembering exactly which requests produce optimal results.
Each subscription request has a `requestedAmount` (weight), a `feeRevenue` (value), and a `maxCapacity`, which is the 
knapsack's capacity. Requests can't be split.


## Prerequisites

- JDK 21
- Docker (runs the database locally, and backs the Testcontainers-based integration tests)

## Running the Application

The application runs as a plain JAR on the host JVM. Only the database is containerized.

```bash
git clone https://github.com/DavitBarnabishvili/Subscription-Capacity-Allocator.git
cd Subscription-Capacity-Allocator

# 1. Start Postgres
docker-compose up -d
# If port 5432 is already in use port can be passed in as a variable, e.g. to use 5433 instead:
# DB_PORT=5433 docker-compose up -d

# 2. Build
./mvnw clean package

# 3. Run
java -jar target/subscriptioncapacityallocator-0.0.1-SNAPSHOT.jar
```

The app starts on `http://localhost:8080`. 
Swagger UI is available at `http://localhost:8080/swagger-ui/index.html`.

## Database Setup

`docker-compose.yml` starts a single Postgres 16 container:

|          |                          |
|----------|--------------------------|
| Database | `subscription_allocator` |
| Username | `subscription_allocator` |
| Password | `subscription_allocator` |
| Port     | `5432`                   |

All this is exposed instead of being injected through environment variables, 
since the assignment isn't intended to be deployed as a real project.

No manual schema setup is needed: Flyway runs `src/main/resources/db/migration/V1__init.sql` automatically on startup.

## Running Tests

```bash
./mvnw clean verify
```

Docker must be running, the integration test suite starts a real Postgres container via
Testcontainers instead of mocking the database. It exercises the actual schema,
migrations, JPA mappings end-to-end.

---

## API Endpoints

### 1. `POST /api/v1/subscriptions/optimize`

Accepts the input payload, runs the optimization, persists the request and result,
returns the output payload as described in the assignment.

Returns **201** when at least one subscription is accepted, **200** when the
optimal result is empty, which is a valid outcome, not an error.

**Valid Request**
```bash
curl -i -X POST http://localhost:8080/api/v1/subscriptions/optimize \
  -H "Content-Type: application/json" \
  -d '{
    "maxCapacity": 15,
    "availableSubscriptions": [
      {"investorName": "Investor A", "requestedAmount": 5, "feeRevenue": 120},
      {"investorName": "Investor B", "requestedAmount": 10, "feeRevenue": 200},
      {"investorName": "Investor C", "requestedAmount": 3, "feeRevenue": 80},
      {"investorName": "Investor D", "requestedAmount": 8, "feeRevenue": 160}
    ]
  }'
```

**Response - `201`**
```json
{
  "requestId": "91489047-33f7-4443-87d6-781bc73916fa",
  "acceptedSubscriptions": [
    {"investorName":"Investor A","requestedAmount":5.0000,"feeRevenue":120.0000},
    {"investorName":"Investor B","requestedAmount":10.0000,"feeRevenue":200.0000}
  ],
  "totalRequestedAmount": 15.0000,
  "totalFeeRevenue": 320.0000,
  "createdAt": "2026-08-23T22:30:53.749844Z"
}
```

**Request Where No Subscriptions Are Accepted**
```bash
curl -i -X POST http://localhost:8080/api/v1/subscriptions/optimize \
  -H "Content-Type: application/json" \
  -d '{
    "maxCapacity": 15,
    "availableSubscriptions": [
      {"investorName": "Investor A", "requestedAmount": 50, "feeRevenue": 120},
      {"investorName": "Investor B", "requestedAmount": 100, "feeRevenue": 200},
      {"investorName": "Investor C", "requestedAmount": 30, "feeRevenue": 80},
      {"investorName": "Investor D", "requestedAmount": 80, "feeRevenue": 160}
    ]
  }'
```

**Response - `200`**
```json
{
  "requestId": "53136701-9cb6-4332-b69f-4cc2e77c3609",
  "acceptedSubscriptions": [],
  "totalRequestedAmount": 0.0000,
  "totalFeeRevenue": 0.0000,
  "createdAt": "2026-08-24T10:49:43.139539Z"
}
```


**Invalid input - `400 Bad Request`**
```bash
curl -i -X POST http://localhost:8080/api/v1/subscriptions/optimize \
  -H "Content-Type: application/json" \
  -d '{"maxCapacity": -5, "availableSubscriptions": [{"investorName": "A", "requestedAmount": -20, "feeRevenue": -10}]}'
```
```json
{
  "message":"requestedAmount, maxCapacity, feeRevenue: must be greater than or equal to 0"
}
```

### 2. `GET /api/v1/subscriptions/{requestId}`

Returns the persisted optimization result for a given run. 
Not an audit trail for that run, just the result of the optimization.

**Request**
```bash
curl -i http://localhost:8080/api/v1/subscriptions/18752d72-d8fe-4dbf-b0d6-9e41476396c7
```

**Response - `200 OK`**
```json
{
  "requestId": "18752d72-d8fe-4dbf-b0d6-9e41476396c7",
  "acceptedSubscriptions": [
    {"investorName":"Investor A","requestedAmount":5.0000,"feeRevenue":120.0000},
    {"investorName":"Investor B","requestedAmount":10.0000,"feeRevenue":200.0000}
  ],
  "totalRequestedAmount": 15.0000,
  "totalFeeRevenue": 320.0000,
  "createdAt": "2026-08-23T22:30:53.749844Z"
}
```

**Unknown ID - `404 Not Found`**
```bash
curl -i http://localhost:8080/api/v1/subscriptions/00000000-0000-0000-0000-000000000000
```
```json
{
  "message":"No optimization run found with id 00000000-0000-0000-0000-000000000000"
}
```

### 3. `GET /api/v1/subscriptions`

Paginated audit trail of every past optimization run, newest first. Unlike endpoint 2, this
returns **every submitted subscription**, not just the accepted ones. Each subscription has an
`accepted` flag. Response also contains the `maxCapacity` run was optimized against, so a reviewer 
can inspect the inputs, constraint, and results.

The trailing zeroes are a product of using BigDecimal and NUMERIC(19,4) for all monetary values.

**Request**
```bash
curl -i "http://localhost:8080/api/v1/subscriptions?size=4"
```

**Response - `200 OK`**
```json
{
  "content": [
    {
      "requestId": "53136701-9cb6-4332-b69f-4cc2e77c3609",
      "maxCapacity": 15.0000,
      "subscriptions": [
        {"investorName":"Investor A","requestedAmount":50.0000,"feeRevenue":120.0000, "accepted":false},
        {"investorName":"Investor B","requestedAmount":100.0000,"feeRevenue":200.0000,"accepted":false},
        {"investorName":"Investor C","requestedAmount":30.0000,"feeRevenue":80.0000,"accepted":false},
        {"investorName":"Investor D","requestedAmount":80.0000,"feeRevenue":160.0000,"accepted":false}
      ],
      "totalRequestedAmount":0.0000,
      "totalFeeRevenue":0.0000,
      "createdAt":"2026-08-24T10:49:43.139539Z"
    },
    {
      "requestId": "91489047-33f7-4443-87d6-781bc73916fa",
      "maxCapacity": 15.0000,
      "subscriptions": [
        {"investorName":"Investor A","requestedAmount":5.0000,"feeRevenue":120.0000, "accepted":true},
        {"investorName":"Investor B","requestedAmount":10.0000,"feeRevenue":200.0000,"accepted":true},
        {"investorName":"Investor C","requestedAmount":3.0000,"feeRevenue":80.0000,"accepted":false},
        {"investorName":"Investor D","requestedAmount":8.0000,"feeRevenue":160.0000,"accepted":false}
      ],
      "totalRequestedAmount":15.0000,
      "totalFeeRevenue":320.0000,
      "createdAt":"2026-08-24T10:44:11.893105Z"
    },
    {
      "requestId": "e9b3815f-449b-4505-aba1-9a24642c9638",
      "maxCapacity": 15.0000,
      "subscriptions": [
        {"investorName":"Investor A","requestedAmount":5.0000,"feeRevenue":120.0000, "accepted":true},
        {"investorName":"Investor B","requestedAmount":10.0000,"feeRevenue":200.0000,"accepted":true},
        {"investorName":"Investor C","requestedAmount":3.0000,"feeRevenue":80.0000,"accepted":false},
        {"investorName":"Investor D","requestedAmount":8.0000,"feeRevenue":160.0000,"accepted":false}
      ],
      "totalRequestedAmount":15.0000,
      "totalFeeRevenue":320.0000,
      "createdAt":"2026-08-23T22:46:54.239496Z"
    },
    {
      "requestId":"03c4d8c2-075a-4765-aa79-3b3534c3aa27",
      "maxCapacity":100.0000,
      "subscriptions": [],
      "totalRequestedAmount":0.0000,
      "totalFeeRevenue":0.0000,
      "createdAt": "2026-08-23T22:33:33.081786Z"
    }
  ],
  "page": {
    "size":4,
    "number":0,
    "totalElements":6,
    "totalPages":2
  }
}
```

---

## Database Schema

The data is stored in two tables: one subscription_optimization_run record for each optimization run, 
and one subscription_request record for each subscription submitted as part of that run. 
Each subscription request is linked to its optimization run through a foreign key.

```
subscription_optimization_run
├── id                      UUID PRIMARY KEY
├── max_capacity            NUMERIC(19,4)
├── total_requested_amount  NUMERIC(19,4)
├── total_fee_revenue       NUMERIC(19,4)
└── created_at              TIMESTAMPTZ

subscription_request
├── id                BIGSERIAL PRIMARY KEY
├── run_id            UUID REFERENCES subscription_optimization_run(id) ON DELETE CASCADE
├── investor_name     VARCHAR(255)
├── requested_amount  NUMERIC(19,4)
├── fee_revenue       NUMERIC(19,4)
└── accepted          BOOLEAN
```

**Why `NUMERIC(19,4)`:** every monetary field uses `BigDecimal` in the application layer and
`NUMERIC` in the database since floating-point rounding error is unacceptable for financial figures. 
Scale 2 would be sufficient for precision down to cents, but 4 leaves space for
computed and intermediate values without truncating them.

**Indexes:**
- `subscription_request(run_id)` - Postgres does not automatically index foreign key columns,
  and every fetch of a run's subscriptions filters on this column.
- `subscription_optimization_run(created_at DESC)` - the audit trail endpoint is ordered newest-first. 
  Instead of requiring a full sort on every request, this index allows the database to retrieve the records 
  in the required order directly, which is useful as the table grows.

**`ON DELETE CASCADE`:** a run's subscriptions are meaningless if the run does not exist, deleting a
run should always take its requests with it.