# Hướng dẫn triển khai zone_id trong JWT Token

## Tổng quan

Client truyền `zone_id` lên khi login → nhúng vào JWT claim → các API khác extract ra để convert/format datetime trong response và request.

**Luồng hoạt động:**
```
POST /api/auth/login { zone_id: "Asia/Bangkok" }
    → validate zone_id hợp lệ
    → generateToken(userId, zoneId) → JWT có claim zone_id
    → JwtAuthenticationFilter extract → UserPrincipal(userId, zoneId) vào SecurityContext
    → Service dùng SecurityUtil.getZoneId() để convert LocalDateTime → OffsetDateTime
```

---

## Bước 1 — Tạo `@ValidZoneId` annotation + Validator (file mới)

Tạo custom constraint để validate `zone_id` hợp lệ ngay tại DTO.

**File 1:** `src/main/java/vn/ghtk/loyalty/validation/ValidZoneId.java`

```java
package vn.ghtk.loyalty.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = ZoneIdValidator.class)
public @interface ValidZoneId {
    String message() default "Invalid zone_id. Must be a valid IANA timezone (e.g. Asia/Ho_Chi_Minh, UTC)";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
```

**File 2:** `src/main/java/vn/ghtk/loyalty/validation/ZoneIdValidator.java`

```java
package vn.ghtk.loyalty.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.ZoneId;

public class ZoneIdValidator implements ConstraintValidator<ValidZoneId, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) return true; // để @NotBlank xử lý
        try {
            ZoneId.of(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
```

---

## Bước 2 — Sửa `LoginRequest.java`

**File:** `src/main/java/vn/ghtk/loyalty/dto/request/LoginRequest.java`

Thêm field `zoneId` với `@NotBlank` + `@ValidZoneId`:

```java
package vn.ghtk.loyalty.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.ghtk.loyalty.validation.ValidZoneId;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginRequest {

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;

    @NotBlank(message = "Zone ID is required")
    @ValidZoneId
    private String zoneId;
}
```

Với cách này:
- `@NotBlank` → bắt null/rỗng, trả lỗi `"Zone ID is required"`
- `@ValidZoneId` → bắt timezone sai format, trả lỗi `"Invalid zone_id. Must be a valid IANA timezone..."`
- `AuthServiceImpl` không cần try-catch `ZoneId.of()` nữa, bỏ đoạn đó đi

---

## Bước 4 — Tạo `UserPrincipal.java` (file mới)

**File:** `src/main/java/vn/ghtk/loyalty/util/UserPrincipal.java`

Object giữ thông tin user sau khi JWT được validate, lưu vào SecurityContext:

```java
package vn.ghtk.loyalty.util;

import java.time.ZoneId;

public record UserPrincipal(Long userId, ZoneId zoneId) {}
```

---

## Bước 5 — Tạo `DateTimeUtil.java` (file mới)

**File:** `src/main/java/vn/ghtk/loyalty/util/DateTimeUtil.java`

Utility convert datetime giữa server timezone và user timezone:

```java
package vn.ghtk.loyalty.util;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public class DateTimeUtil {

    public static final ZoneId SERVER_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /**
     * Convert LocalDateTime từ DB (server timezone) → OffsetDateTime theo user zone.
     * Dùng khi build response trả về client.
     */
    public static OffsetDateTime toOffsetDateTime(LocalDateTime dbTime, ZoneId userZone) {
        if (dbTime == null) return null;
        return dbTime.atZone(SERVER_ZONE)
                     .withZoneSameInstant(userZone)
                     .toOffsetDateTime();
    }

    /**
     * Convert LocalDateTime từ request của user (theo user zone) → LocalDateTime theo server zone.
     * Dùng khi nhận datetime từ request để query DB.
     */
    public static LocalDateTime toServerLocalDateTime(LocalDateTime userLocalTime, ZoneId userZone) {
        if (userLocalTime == null) return null;
        return userLocalTime.atZone(userZone)
                            .withZoneSameInstant(SERVER_ZONE)
                            .toLocalDateTime();
    }
}
```

---

## Bước 6 — Sửa `JwtUtil.java`

**File:** `src/main/java/vn/ghtk/loyalty/util/JwtUtil.java`

Thêm tham số `zoneId` vào `generateToken()` và thêm method `extractZoneId()`:

```java
package vn.ghtk.loyalty.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Slf4j
@Component
public class JwtUtil {

    @Value("${spring.security.jwt.secret}")
    private String secret;

    @Value("${spring.security.jwt.expiration}")
    private Long expiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String userId, String zoneId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(userId)
                .claim("zone_id", zoneId)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public long getExpirationMillis() {
        return expiration;
    }

    public String extractUserId(String token) {
        return extractAllClaims(token).getSubject();
    }

    public String extractZoneId(String token) {
        return extractAllClaims(token).get("zone_id", String.class);
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            log.error("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
```

---

## Bước 7 — Sửa `AuthServiceImpl.java`

**File:** `src/main/java/vn/ghtk/loyalty/service/impl/AuthServiceImpl.java`

`zone_id` đã được validate ở DTO, service chỉ cần truyền thẳng vào `generateToken()`:

```java
package vn.ghtk.loyalty.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import vn.ghtk.loyalty.dto.request.LoginRequest;
import vn.ghtk.loyalty.dto.response.LoginResponse;
import vn.ghtk.loyalty.entity.User;
import vn.ghtk.loyalty.exception.BusinessException;
import vn.ghtk.loyalty.repository.UserRepository;
import vn.ghtk.loyalty.service.AuthService;
import vn.ghtk.loyalty.util.JwtUtil;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @Override
    public LoginResponse login(LoginRequest request) {
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException("Invalid username or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException("Invalid username or password");
        }

        String token = jwtUtil.generateToken(String.valueOf(user.getId()), request.getZoneId());

        return LoginResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .expiresIn(jwtUtil.getExpirationMillis())
                .build();
    }
}
```

---

## Bước 8 — Sửa `JwtAuthenticationFilter.java`

**File:** `src/main/java/vn/ghtk/loyalty/filter/JwtAuthenticationFilter.java`

Extract `zone_id` từ token, tạo `UserPrincipal` thay vì raw String:

```java
package vn.ghtk.loyalty.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.ghtk.loyalty.util.JwtUtil;
import vn.ghtk.loyalty.util.UserPrincipal;

import java.io.IOException;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractTokenFromRequest(request);

        if (token != null && jwtUtil.validateToken(token)) {
            String userId = jwtUtil.extractUserId(token);
            String zoneIdStr = jwtUtil.extractZoneId(token);
            if (userId != null && zoneIdStr != null) {
                UserPrincipal principal = new UserPrincipal(
                        Long.parseLong(userId),
                        ZoneId.of(zoneIdStr)
                );
                Authentication authentication = new UsernamePasswordAuthenticationToken(
                        principal, null, null);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
```

---

## Bước 9 — Sửa `SecurityUtil.java`

**File:** `src/main/java/vn/ghtk/loyalty/util/SecurityUtil.java`

Cập nhật để đọc từ `UserPrincipal`, thêm `getZoneId()`:

```java
package vn.ghtk.loyalty.util;

import org.springframework.security.core.Authentication;

import java.time.ZoneId;

public class SecurityUtil {

    public static Long getUserIdFromAuthentication(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal.userId();
        }
        return 0L;
    }

    public static ZoneId getZoneId(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal.zoneId();
        }
        return DateTimeUtil.SERVER_ZONE;
    }
}
```

---

## Bước 10 — Sửa `PointsHistoryResponse.java`

**File:** `src/main/java/vn/ghtk/loyalty/dto/response/PointsHistoryResponse.java`

Đổi `LocalDateTime` → `OffsetDateTime`:

```java
package vn.ghtk.loyalty.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import vn.ghtk.loyalty.enums.PointsTransactionType;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PointsHistoryResponse {

    private Long id;
    private Integer points;
    private PointsTransactionType transactionType;
    private String description;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private OffsetDateTime createdAt;
}
```

---

## Bước 11 — Sửa `UserResponse.java`

**File:** `src/main/java/vn/ghtk/loyalty/dto/response/UserResponse.java`

Đổi `LocalDateTime` → `OffsetDateTime`, thêm `@JsonFormat`:

```java
package vn.ghtk.loyalty.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UserResponse {

    private Long id;
    private String name;
    private String avatar;
    private Integer totalPoints;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private OffsetDateTime createdAt;
}
```

---

## Bước 12 — Sửa `PointsServiceImpl.java`

**File:** `src/main/java/vn/ghtk/loyalty/service/impl/PointsServiceImpl.java`

Thêm tham số `ZoneId userZone` vào `getPointsHistory()` và dùng `DateTimeUtil` khi map response:

**10a. Sửa interface `PointsService.java` trước:**

`src/main/java/vn/ghtk/loyalty/service/PointsService.java` — cập nhật signature:

```java
PageResponse<PointsHistoryResponse> getPointsHistory(Long userId, Integer page, Integer size, Integer month, ZoneId userZone);
```

**10b. Sửa `PointsServiceImpl.java`:**

Thêm import và sửa method `getPointsHistory`:

```java
import vn.ghtk.loyalty.util.DateTimeUtil;
import java.time.ZoneId;
```

Sửa phần mapping trong `getPointsHistory`:

```java
@Override
@Transactional(readOnly = true)
public PageResponse<PointsHistoryResponse> getPointsHistory(Long userId, Integer page, Integer size, Integer month, ZoneId userZone) {
    Pageable pageable = PageRequest.of(page, size);
    Page<UserPointsHistory> historyPage;

    if (month != null) {
        LocalDate now = LocalDate.now();
        int year = now.getYear();
        historyPage = userPointsHistoryRepository.findByUserIdAndMonth(userId, year, month, pageable);
    } else {
        historyPage = userPointsHistoryRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
    }

    List<PointsHistoryResponse> content = historyPage.getContent().stream()
            .map(h -> PointsHistoryResponse.builder()
                    .id(h.getId())
                    .points(h.getPoints())
                    .transactionType(h.getTransactionType())
                    .description(h.getDescription())
                    .createdAt(DateTimeUtil.toOffsetDateTime(h.getCreatedAt(), userZone))
                    .build())
            .collect(Collectors.toList());

    return PageResponse.<PointsHistoryResponse>builder()
            .content(content)
            .page(historyPage.getNumber())
            .size(historyPage.getSize())
            .totalElements(historyPage.getTotalElements())
            .totalPages(historyPage.getTotalPages())
            .first(historyPage.isFirst())
            .last(historyPage.isLast())
            .build();
}
```

---

## Bước 13 — Sửa `UserServiceImpl.java`

**File:** `src/main/java/vn/ghtk/loyalty/service/impl/UserServiceImpl.java`

**11a. Sửa interface `UserService.java`:**

Cập nhật signature 2 method có trả `UserResponse`:

```java
UserResponse createUser(CreateUserRequest request);           // giữ nguyên, dùng SERVER_ZONE (public API)
UserResponse getUserById(Long userId, ZoneId userZone);      // thêm ZoneId
```

**11b. Sửa `UserServiceImpl.java`:**

Thêm import:

```java
import vn.ghtk.loyalty.util.DateTimeUtil;
import java.time.ZoneId;
```

Sửa `createUser` — public API, không có auth → dùng `SERVER_ZONE`:

```java
@Override
@Transactional
public UserResponse createUser(CreateUserRequest request) {
    userRepository.findByUsername(request.getUsername()).ifPresent(u -> {
        throw new BusinessException("Username already exists");
    });

    User user = User.builder()
            .username(request.getUsername())
            .password(passwordEncoder.encode(request.getPassword()))
            .name(request.getName())
            .avatar(request.getAvatar())
            .totalPoints(0)
            .build();

    User savedUser = userRepository.save(user);

    return UserResponse.builder()
            .id(savedUser.getId())
            .name(savedUser.getName())
            .avatar(savedUser.getAvatar())
            .totalPoints(savedUser.getTotalPoints())
            .createdAt(DateTimeUtil.toOffsetDateTime(savedUser.getCreatedAt(), DateTimeUtil.SERVER_ZONE))
            .build();
}
```

Sửa `getUserById` — authenticated API → nhận `ZoneId` từ controller:

```java
@Override
@Transactional(readOnly = true)
public UserResponse getUserById(Long userId, ZoneId userZone) {
    User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found with id: " + userId));

    return UserResponse.builder()
            .id(user.getId())
            .name(user.getName())
            .avatar(user.getAvatar())
            .totalPoints(user.getTotalPoints())
            .createdAt(DateTimeUtil.toOffsetDateTime(user.getCreatedAt(), userZone))
            .build();
}
```

---

## Bước 14 — Sửa các Controller

Tại các controller có authenticated endpoint, thêm `Authentication` và truyền `ZoneId` vào service.

**Ví dụ `PointsController.java` — method `getPointsHistory`:**

```java
@GetMapping("/history")
public ResponseEntity<ApiResponse<PageResponse<PointsHistoryResponse>>> getPointsHistory(
        Authentication authentication,
        @RequestParam(defaultValue = "0") Integer page,
        @RequestParam(defaultValue = "10") Integer size,
        @RequestParam(required = false) Integer month) {

    Long userId = SecurityUtil.getUserIdFromAuthentication(authentication);
    ZoneId userZone = SecurityUtil.getZoneId(authentication);
    PageResponse<PointsHistoryResponse> result = pointsService.getPointsHistory(userId, page, size, month, userZone);
    return ResponseEntity.ok(ApiResponse.success(result));
}
```

**Ví dụ `UserController.java` — method `getUserById`:**

```java
@GetMapping("/{id}")
public ResponseEntity<ApiResponse<UserResponse>> getUserById(
        Authentication authentication,
        @PathVariable Long id) {

    ZoneId userZone = SecurityUtil.getZoneId(authentication);
    UserResponse result = userService.getUserById(id, userZone);
    return ResponseEntity.ok(ApiResponse.success(result));
}
```

---

## Bước 15 — Nhận datetime từ Request (khi cần)

Với các API nhận datetime input từ client (ví dụ filter theo khoảng thời gian), field trong request DTO dùng `LocalDateTime` + `@JsonFormat` để Jackson parse đúng format:

```java
@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
private LocalDateTime fromDate;

@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
private LocalDateTime toDate;
```

Trong service, convert sang server timezone để query DB:

```java
ZoneId userZone = SecurityUtil.getZoneId(authentication);

LocalDateTime fromDb = DateTimeUtil.toServerLocalDateTime(request.getFromDate(), userZone);
LocalDateTime toDb   = DateTimeUtil.toServerLocalDateTime(request.getToDate(), userZone);

// dùng fromDb, toDb để query repository
```

---

## Checklist tổng hợp

- [ ] Bước 1: Tạo `@ValidZoneId` annotation + `ZoneIdValidator.java`
- [ ] Bước 2: Sửa `LoginRequest.java` — thêm `zoneId` + `@NotBlank` + `@ValidZoneId`
- [ ] Bước 3: Tạo `UserPrincipal.java`
- [ ] Bước 4: Tạo `UserPrincipal.java` (file mới)
- [ ] Bước 5: Tạo `DateTimeUtil.java`
- [ ] Bước 6: Sửa `JwtUtil.java` — thêm `zoneId` vào token
- [ ] Bước 7: Sửa `AuthServiceImpl.java` — truyền `zoneId` (bỏ try-catch thừa)
- [ ] Bước 8: Sửa `JwtAuthenticationFilter.java` — extract `zone_id`, tạo `UserPrincipal`
- [ ] Bước 9: Sửa `SecurityUtil.java` — thêm `getZoneId()`
- [ ] Bước 10: Sửa `PointsHistoryResponse.java` — đổi sang `OffsetDateTime`
- [ ] Bước 11: Sửa `UserResponse.java` — đổi sang `OffsetDateTime`
- [ ] Bước 12: Sửa `PointsService` interface + `PointsServiceImpl.java`
- [ ] Bước 13: Sửa `UserService` interface + `UserServiceImpl.java`
- [ ] Bước 14: Sửa các Controller liên quan
- [ ] Bước 15: Áp dụng cho request DTO nếu có field datetime

---

## Lưu ý

- `zone_id` phải theo chuẩn IANA timezone: `Asia/Ho_Chi_Minh`, `Asia/Bangkok`, `Asia/Singapore`, `UTC`, `America/New_York`...
- Server timezone đang là `Asia/Ho_Chi_Minh` (cấu hình trong JDBC URL). Nếu thay đổi server timezone thì cập nhật `DateTimeUtil.SERVER_ZONE`.
- `@JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")` trên `OffsetDateTime` sẽ serialize ra chuỗi theo giờ địa phương của offset đó, không hiện ký hiệu offset trong output.
