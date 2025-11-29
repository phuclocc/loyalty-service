# Loyalty Service

Service quản lý điểm danh hàng ngày và hệ thống điểm thưởng, được xây dựng với Java 21 và Spring Boot 3.3.x.

## 🚀 Tech Stack

- **Java 21**
- **Spring Boot 3.3.2**
- **MySQL 8.0** - Database chính
- **Redis 7** - Cache và distributed lock
- **Redisson** - Distributed lock implementation
- **Spring Security** - JWT authentication
- **Liquibase** - Database migration
- **Lombok** - Code generation
- **Docker Compose** - Container orchestration

## 📋 Tính năng

1. **Quản lý User**
   - Tạo user mới
   - Lấy thông tin profile user
   - Xem tổng điểm và số ngày điểm danh trong tháng

2. **Điểm danh hàng ngày**
   - Điểm danh trong 2 khung giờ: 9h-11h và 19h-21h
   - Mỗi ngày chỉ được điểm danh 1 lần
   - Mỗi tháng tối đa 7 lần điểm danh
   - Điểm thưởng theo thứ tự: 1, 2, 3, 5, 8, 13, 21
   - Sử dụng Redis lock để tránh double-checkin

3. **Quản lý điểm**
   - Xem lịch sử cộng/trừ điểm (có phân trang)
   - Trừ điểm (không cho phép âm)
   - Lọc theo tháng

## 🏗️ Cấu trúc thư mục

```
loyalty-service/
├── src/
│   ├── main/
│   │   ├── java/vn/ghtk/loyalty/
│   │   │   ├── config/          # Configuration (Redis, Security, JWT)
│   │   │   ├── controller/       # REST Controllers
│   │   │   ├── dto/             # Data Transfer Objects
│   │   │   │   ├── request/     # Request DTOs
│   │   │   │   └── response/    # Response DTOs
│   │   │   ├── entity/          # JPA Entities
│   │   │   ├── enums/           # Enumerations
│   │   │   ├── exception/       # Custom exceptions
│   │   │   ├── filter/          # Security filters
│   │   │   ├── repository/      # JPA Repositories
│   │   │   ├── service/         # Business logic
│   │   │   │   └── impl/       # Service implementations
│   │   │   └── util/           # Utilities
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/
│   │           └── changelog/  # Liquibase migrations
│   └── test/
├── docker-compose.yml
├── pom.xml
└── README.md
```

## 🛠️ Setup và Chạy

### Yêu cầu

- Java 21
- Maven 3.8+
- Docker và Docker Compose

### Chạy MySQL và Redis với Docker Compose

```bash
# Chạy MySQL và Redis
docker-compose up -d

# Kiểm tra containers đang chạy
docker-compose ps

# Xem logs
docker-compose logs -f

# Dừng tất cả
docker-compose down
```

**Lưu ý:** 
- File `docker-compose.yml` chỉ chạy MySQL và Redis
- App sẽ chạy ở local bằng `mvn spring-boot:run`
- Nếu muốn chạy app trong Docker, uncomment service `app` trong `docker-compose.yml` (cần có Dockerfile)

### Cấu hình Database

File `application.yml` đã được cấu hình sẵn:
- MySQL: `localhost:3306` (hoặc `mysql` khi chạy trong Docker)
- Database: `loyalty_db`
- Username: `root`
- Password: `root`

Liquibase sẽ tự động tạo tables khi ứng dụng khởi động.

### Build và chạy ứng dụng (nếu không dùng Docker cho app)

```bash
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

### 4. Điểm danh

**POST** `/api/checkin`

**Headers:**
```
Authorization: Bearer {token}
```

**Lưu ý:**
- Chỉ được điểm danh trong khung giờ 9h-11h hoặc 19h-21h
- Mỗi ngày chỉ được điểm danh 1 lần
- Mỗi tháng tối đa 7 lần

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

## 🔄 Transaction

Tất cả các thao tác cộng/trừ điểm đều được thực hiện trong transaction để đảm bảo:
- Tính nhất quán dữ liệu
- Rollback nếu có lỗi
- Atomic operations

## 📦 Postman Collection

Import file `postman/Loyalty-Service.postman_collection.json` vào Postman để test các API.

**Variables:**
- `baseUrl`: `http://localhost:8080`
- `token`: JWT token (nếu có)

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

## 🐛 Troubleshooting

### MySQL connection error
- Kiểm tra Docker container đang chạy: `docker-compose ps`
- Kiểm tra logs: `docker-compose logs mysql`

### Redis connection error
- Kiểm tra Redis container: `docker-compose logs redis`
- Test connection: `docker exec -it loyalty-redis redis-cli ping`

### Liquibase migration error
- Xóa database và tạo lại
- Hoặc check logs trong application startup

## 📄 License

Internal project

