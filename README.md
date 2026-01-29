# Loyalty Service

Service quản lý điểm danh hàng ngày và hệ thống điểm thưởng, được xây dựng với Java 21 và Spring Boot 3.3.x.

Hệ thống hỗ trợ **2 kiến trúc**:
1. **Synchronous API** (v1): Xử lý real-time, trực tiếp ghi DB
2. **Event-Driven API** (v2): Publish event to Kafka, xử lý bất đồng bộ bởi Flink

## 🚀 Tech Stack

- **Java 21**
- **Spring Boot 3.3.2**
- **MySQL 8.0** - Database chính
- **Redis 7** - Cache và distributed lock
- **Redisson** - Distributed lock implementation
- **Spring Security** - JWT authentication
- **Liquibase** - Database migration
- **Lombok** - Code generation
- **Apache Kafka** - Event streaming platform
- **Apache Flink 1.18.1** - Stream processing engine
- **Docker Compose** - Container orchestration

## 📋 Tính năng

### 1. Quản lý User
   - Tạo user mới
   - Lấy thông tin profile user
   - Xem tổng điểm và số ngày điểm danh trong tháng

### 2. Điểm danh hàng ngày

#### A. API v1 - Synchronous (Existing)
   - Endpoint: `POST /api/checkin`
   - Điểm danh trong 2 khung giờ: 9h-11h và 19h-21h
   - Mỗi ngày chỉ được điểm danh 1 lần
   - Mỗi tháng tối đa 7 lần điểm danh
   - Điểm thưởng theo thứ tự: 1, 2, 3, 5, 8, 13, 21
   - Sử dụng Redis lock để tránh double-checkin
   - Xử lý real-time, trực tiếp ghi DB

#### B. API v2 - Event-Driven (New)
   - Endpoint: `POST /api/v2/checkin`
   - Validate request và publish event to Kafka
   - Xử lý bất đồng bộ bởi Flink consumer
   - Exactly-once semantics end-to-end
   - Deduplication theo `eventId`
   - Suitable cho high throughput (1M-50M events/day)

### 3. Quản lý điểm
   - Xem lịch sử cộng/trừ điểm (có phân trang)
   - Trừ điểm (không cho phép âm)
   - Lọc theo tháng

## 🏗️ Kiến trúc hệ thống

```
┌─────────────────┐
│   User Client   │
└────────┬────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────┐
│              Loyalty Service (Spring Boot)               │
│  ┌──────────────┐              ┌──────────────────────┐ │
│  │  API v1      │              │     API v2           │ │
│  │ /api/checkin │              │  /api/v2/checkin     │ │
│  │ (sync)       │              │  (event-driven)      │ │
│  └──────┬───────┘              └──────────┬───────────┘ │
│         │                                 │             │
│         ▼                                 ▼             │
│  ┌────────────┐                  ┌──────────────┐      │
│  │ Redis Lock │                  │Kafka Producer│      │
│  └────────────┘                  └──────┬───────┘      │
│         │                               │              │
│         ▼                               │              │
│  ┌────────────┐                         │              │
│  │   MySQL    │◄────────────────────────┘              │
│  └────────────┘                         │              │
└──────────────────────────────────────────┼──────────────┘
                                           │
                                           ▼
                              ┌────────────────────────┐
                              │   Kafka Topic          │
                              │  loyalty.checkin       │
                              └─────────┬──────────────┘
                                        │
                                        ▼
                              ┌────────────────────────┐
                              │  Flink Consumer Job    │
                              │  - Deduplication       │
                              │  - Business Logic      │
                              │  - Exactly-once        │
                              └─────────┬──────────────┘
                                        │
                                        ▼
                              ┌────────────────────────┐
                              │   MySQL (Sink)         │
                              │  - users               │
                              │  - daily_checkin       │
                              │  - user_points_history │
                              │  - checkin_events      │
                              └────────────────────────┘
```

## 📁 Cấu trúc thư mục

### loyalty-service (Producer)
```
loyalty-service/
├── src/
│   ├── main/
│   │   ├── java/vn/ghtk/loyalty/
│   │   │   ├── config/          # Configuration (Redis, Security, JWT, Kafka)
│   │   │   ├── controller/      # REST Controllers (v1 & v2)
│   │   │   ├── dto/
│   │   │   │   ├── event/       # Kafka event DTOs
│   │   │   │   ├── request/     # Request DTOs
│   │   │   │   └── response/    # Response DTOs
│   │   │   ├── entity/          # JPA Entities
│   │   │   ├── enums/           # Enumerations
│   │   │   ├── exception/       # Custom exceptions
│   │   │   ├── filter/          # Security filters
│   │   │   ├── repository/      # JPA Repositories
│   │   │   ├── service/         # Business logic
│   │   │   │   └── impl/        # Service implementations
│   │   │   └── util/            # Utilities
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/
│   │           └── changelog/   # Liquibase migrations
│   └── test/
├── docker-compose.yml            # Kafka, Flink, MySQL, Redis
├── kafka-topics-setup.sh         # Kafka topic creation script
├── kafka-topics-setup.bat        # Windows version
├── SIZING.md                     # Sizing & capacity planning
├── pom.xml
└── README.md
```

### loyalty-flink-consumer (Consumer)
```
loyalty-flink-consumer/
├── src/
│   └── main/
│       ├── java/vn/ghtk/loyalty/flink/
│       │   ├── CheckinEventConsumerJob.java  # Main job
│       │   ├── model/
│       │   │   ├── CheckinEvent.java         # Event model
│       │   │   ├── EventMetadata.java        # Metadata
│       │   │   └── ProcessedCheckin.java     # Processed result
│       │   ├── function/
│       │   │   ├── CheckinEventDeserializer.java   # Kafka deserializer
│       │   │   ├── CheckinProcessFunction.java     # Process with dedup
│       │   │   └── MySQLCheckinSink.java           # JDBC sink (2PC)
│       │   └── config/
│       │       └── JobConfig.java            # Job configuration
│       └── resources/
│           └── log4j2.properties
├── pom.xml
└── README.md
```

## 🛠️ Setup và Chạy

### Yêu cầu

- Java 21
- Maven 3.8+
- Docker và Docker Compose

### Bước 1: Khởi động Infrastructure với Docker Compose

File `docker-compose.yml` hiện bao gồm:
- **MySQL 8.0** - Database
- **Redis 7** - Cache & Distributed Lock
- **Kafka + Zookeeper** - Event streaming
- **Kafka UI** - Web UI for Kafka (http://localhost:8090)
- **Flink JobManager + TaskManager** - Stream processing
- **Flink Web UI** - http://localhost:8081

```bash
# Khởi động tất cả services
docker-compose up -d

# Kiểm tra containers đang chạy
docker-compose ps

# Xem logs
docker-compose logs -f

# Xem logs của service cụ thể
docker-compose logs -f kafka
docker-compose logs -f flink-jobmanager

# Dừng tất cả
docker-compose down

# Dừng và xóa volumes (clean slate)
docker-compose down -v
```

**Lưu ý:** 
- App `loyalty-service` sẽ chạy ở local bằng `mvn spring-boot:run`
- Nếu muốn chạy app trong Docker, uncomment service `app` trong `docker-compose.yml`

### Cấu hình Database

File `application.yml` đã được cấu hình sẵn:
- MySQL: `localhost:3306` (hoặc `mysql` khi chạy trong Docker)
- Database: `loyalty_db`
- Username: `root`
- Password: `root`

Liquibase sẽ tự động tạo tables khi ứng dụng khởi động.

### Bước 2: Tạo Kafka Topic

Sau khi Kafka đã chạy, tạo topic `loyalty.checkin`:

**Linux/Mac:**
```bash
chmod +x kafka-topics-setup.sh
./kafka-topics-setup.sh
```

**Windows:**
```bash
kafka-topics-setup.bat
```

Script sẽ hỏi bạn chọn configuration:
1. Development (1M events/day) - 4 partitions
2. Production (10M events/day) - 8 partitions
3. High Load (50M events/day) - 16 partitions
4. Custom

Hoặc tạo manual:
```bash
docker exec -it loyalty-kafka kafka-topics --create \
  --bootstrap-server localhost:9092 \
  --topic loyalty.checkin \
  --partitions 4 \
  --replication-factor 1
```

### Bước 3: Build và chạy loyalty-service (Producer)

```bash
cd loyalty-service

# Build project
mvn clean install

# Chạy ứng dụng
mvn spring-boot:run
```

Hoặc chạy JAR file:
```bash
java -jar target/loyalty-service-0.0.1-SNAPSHOT.jar
```

Ứng dụng sẽ chạy tại: `http://localhost:8080`

### Bước 4: Build và Deploy Flink Consumer Job

```bash
cd ../loyalty-flink-consumer

# Build Flink job
mvn clean package

# Submit job to Flink cluster
docker cp target/loyalty-flink-consumer-1.0.0-SNAPSHOT.jar loyalty-flink-jobmanager:/opt/flink/

docker exec -it loyalty-flink-jobmanager flink run \
  -c vn.ghtk.loyalty.flink.CheckinEventConsumerJob \
  -p 4 \
  /opt/flink/loyalty-flink-consumer-1.0.0-SNAPSHOT.jar \
  --kafka.bootstrap.servers kafka:29092 \
  --kafka.topic loyalty.checkin \
  --mysql.url jdbc:mysql://mysql:3306/loyalty_db \
  --mysql.username root \
  --mysql.password root \
  --checkpoint.interval 60000 \
  --parallelism 4
```

Kiểm tra Flink job đang chạy:
- Flink Web UI: http://localhost:8081
- Kafka UI: http://localhost:8090

### Chạy App trong Docker (Optional)

Nếu muốn chạy app trong Docker thay vì local:

1. Uncomment service `app` trong `docker-compose.yml`
2. Chạy:
```bash
docker-compose up -d --build
```

Hoặc build và chạy image riêng:
```bash
# Build image
docker build -t loyalty-service:latest .

# Chạy container
docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:mysql://host.docker.internal:3306/loyalty_db \
  -e SPRING_DATA_REDIS_HOST=host.docker.internal \
  loyalty-service:latest
```

## 📚 API Documentation

### 1. Tạo User

**POST** `/api/users`

```json
{
  "username": "john",
  "password": "secret123",
  "name": "John Doe",
  "avatar": "https://example.com/avatar.jpg"
}
```

### 2. Login

**POST** `/api/auth/login`

```json
{
  "username": "john",
  "password": "secret123"
}
```

Response mẫu:

```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "<JWT_TOKEN>",
    "tokenType": "Bearer",
    "expiresIn": 86400000
  }
}
```

### 3. Lấy Profile User

**GET** `/api/users/profile`

**Headers:**
```
Authorization: Bearer {token}
```

### 4. Điểm danh v1 (Synchronous)

**POST** `/api/checkin`

**Headers:**
```
Authorization: Bearer {token}
```

**Lưu ý:**
- Chỉ được điểm danh trong khung giờ 9h-11h hoặc 19h-21h
- Mỗi ngày chỉ được điểm danh 1 lần
- Mỗi tháng tối đa 7 lần
- Xử lý real-time, trực tiếp ghi DB

Response:
```json
{
  "success": true,
  "message": "Check-in successful",
  "data": {
    "success": true,
    "message": "Check-in successful",
    "pointsEarned": 5,
    "totalPoints": 120,
    "checkinOrder": 4
  }
}
```

### 4b. Điểm danh v2 (Event-Driven)

**POST** `/api/v2/checkin`

**Headers:**
```
Authorization: Bearer {token}
Content-Type: application/json
```

**Body (Optional):**
```json
{
  "requestId": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Lưu ý:**
- Chỉ validate và publish event to Kafka
- Xử lý bất đồng bộ bởi Flink
- Exactly-once guarantee
- Suitable cho high throughput

Response:
```json
{
  "success": true,
  "message": "Check-in event published successfully. Points will be credited shortly.",
  "data": {
    "eventId": "123_20260125_1737800000_a1b2c3d4",
    "success": true,
    "message": "Check-in event published successfully. Points will be credited shortly.",
    "eventTimestamp": "2026-01-25T10:30:00"
  }
}
```

### 5. Lấy trạng thái điểm danh 7 ngày

**GET** `/api/checkin/status`

**Headers:**
```
Authorization: Bearer {token}
```

### 6. Trừ điểm

**POST** `/api/points/deduct`

**Headers:**
```
Authorization: Bearer {token}
Content-Type: application/json
```

**Body:**
```json
{
    "points": 10
}
```

### 7. Lịch sử điểm

**GET** `/api/points/history?page=0&size=10&month=11`

**Query Parameters:**
- `page`: Số trang (default: 0)
- `size`: Số phần tử mỗi trang (default: 10)
- `month`: Tháng (1-12, optional)

## 🔐 Authentication

Hệ thống sử dụng **JWT** với các đặc điểm:

- Login bằng `username` + `password` qua endpoint `/api/auth/login`.
- Nếu login thành công, server trả về `accessToken` kiểu Bearer.
- Các API cần bảo vệ sẽ yêu cầu header:

```http
Authorization: Bearer <accessToken>
```

- Thông tin `userId` được lưu trong `sub` (subject) của JWT.
- Secret và thời gian sống token được cấu hình trong `application.yml` dưới `spring.security.jwt.*`.

## 🗄️ Database Schema

### Users
- `id` (BIGINT, PK)
- `username` (VARCHAR, unique, not null)
- `password` (VARCHAR, hashed, not null)
- `name` (VARCHAR)
- `avatar` (VARCHAR)
- `total_points` (INT)
- `created_at` (DATETIME)
- `updated_at` (DATETIME)

### User Points History
- `id` (BIGINT, PK)
- `user_id` (BIGINT, FK)
- `points` (INT)
- `transaction_type` (VARCHAR) - CHECKIN, DEDUCT, MANUAL_ADJUSTMENT
- `description` (VARCHAR)
- `created_at` (DATETIME)

**Indexes:**
- `user_id`
- `created_at`

### Daily Checkin
- `id` (BIGINT, PK)
- `user_id` (BIGINT, FK)
- `checkin_date` (DATE)
- `points_earned` (INT)
- `checkin_order` (INT) - Thứ tự điểm danh trong tháng (1-7)
- `created_at` (DATETIME)

**Indexes:**
- `user_id`
- `checkin_date`
- Unique constraint: `(user_id, checkin_date)`

### Checkin Events (New - for Event-Driven)
- `id` (BIGINT, PK)
- `event_id` (VARCHAR, unique, not null) - For deduplication
- `user_id` (BIGINT, FK)
- `checkin_date` (DATE)
- `event_timestamp` (DATETIME)
- `year_month` (VARCHAR(6)) - yyyyMM for partitioning
- `points_earned` (INT, nullable)
- `processed` (BOOLEAN) - Processing status
- `processed_at` (DATETIME, nullable)
- `error_message` (VARCHAR(500), nullable)
- `created_at` (DATETIME)
- `updated_at` (DATETIME)

**Indexes:**
- `event_id` (unique)
- `user_id, checkin_date`
- `year_month`
- `processed`
- `created_at`

## 🔒 Redis và Distributed Lock

### Tại sao dùng Redis?

1. **Performance**: Redis lưu trạng thái điểm danh theo ngày với tốc độ truy cập nhanh
2. **Distributed Lock**: Redisson cung cấp distributed lock để đảm bảo không có double-checkin trong môi trường multi-instance

### Redis Keys

- Check-in status: `checkin:{userId}:{yyyy-MM-dd}`
- Lock: `lock:checkin:{userId}`

### Lock Mechanism

Khi user điểm danh:
1. Kiểm tra Redis xem đã điểm danh chưa
2. Acquire Redisson lock với timeout 10s
3. Double-check sau khi có lock
4. Thực hiện transaction (cộng điểm, ghi lịch sử)
5. Lưu vào Redis với expiration đến cuối ngày
6. Release lock

## 🔄 Transaction & Exactly-Once

### API v1 (Synchronous)
- Sử dụng Spring `@Transactional`
- Redis distributed lock (Redisson)
- ACID guarantees

### API v2 (Event-Driven)
- **Kafka Producer**: Idempotent producer với `acks=all`
- **Flink Consumer**: 
  - Checkpoint với `EXACTLY_ONCE` mode
  - MapState cho deduplication
  - 2-Phase Commit (2PC) khi ghi MySQL
- **MySQL**: XA transactions support
- **End-to-end exactly-once** từ Kafka → Flink → MySQL

## 📦 Postman Collection

Import file `postman/Loyalty-Service.postman_collection.json` vào Postman để test các API.

**Variables:**
- `baseUrl`: `http://localhost:8080`
- `token`: JWT token (nếu có)

Collection bao gồm:
- Authentication (Login)
- User Management
- Check-in v1 (Synchronous)
- **Check-in v2 (Event-Driven)** - New!
- Points Management

## 📊 Sizing & Capacity Planning

Xem chi tiết tại `SIZING.md` cho:
- Throughput tính toán (1M, 10M, 50M events/day)
- Kafka partitions sizing
- Flink parallelism & memory
- MySQL storage growth
- Infrastructure requirements

## 🧪 Testing

```bash
# Chạy tests
mvn test
```

## 📝 Notes

- Code được viết rõ ràng, không viết tắt
- Sử dụng clean architecture pattern
- Tất cả API trả về DTO, không trả về Entity trực tiếp
- Exception handling toàn cục
- Validation đầy đủ cho request

## 🔀 So sánh API v1 vs v2

| Feature | API v1 (Sync) | API v2 (Event-Driven) |
|---------|---------------|----------------------|
| **Endpoint** | POST /api/checkin | POST /api/v2/checkin |
| **Response Time** | ~50-200ms | ~10-30ms (async) |
| **Throughput** | ~100-200 req/s | ~1000+ req/s |
| **Consistency** | Immediate | Eventual (~1-5s) |
| **Deduplication** | Redis Lock | Kafka eventId + Flink MapState |
| **Failure Handling** | Retry in request | Kafka replay + checkpoint |
| **Scalability** | Vertical | Horizontal (Kafka + Flink) |
| **Use Case** | Low traffic, real-time | High traffic, analytics |

## 📚 Related Projects

- **loyalty-flink-consumer**: Apache Flink consumer job (separate repository)
  - Location: `../loyalty-flink-consumer`
  - README: See project README for details

## 🐛 Troubleshooting

### MySQL connection error
- Kiểm tra Docker container đang chạy: `docker-compose ps`
- Kiểm tra logs: `docker-compose logs mysql`

### Redis connection error
- Kiểm tra Redis container: `docker-compose logs redis`
- Test connection: `docker exec -it loyalty-redis redis-cli ping`

### Kafka connection error
- Kiểm tra Kafka: `docker-compose logs kafka`
- Test topic: `docker exec -it loyalty-kafka kafka-topics --list --bootstrap-server localhost:9092`
- Check UI: http://localhost:8090

### Flink job not running
- Check Flink UI: http://localhost:8081
- Check logs: `docker-compose logs flink-jobmanager`
- Verify job submission: `docker exec -it loyalty-flink-jobmanager flink list`

### Events not processed
1. Check Kafka topic has messages:
   ```bash
   docker exec -it loyalty-kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic loyalty.checkin --from-beginning
   ```
2. Check Flink job status: http://localhost:8081
3. Check MySQL table `checkin_events` for processed events
4. Check Flink logs for errors

### Liquibase migration error
- Xóa database và tạo lại
- Hoặc check logs trong application startup

## 📄 License

Internal project

