package vn.ghtk.loyalty.dto.response;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

public class ApiResult {

    private ApiResult() {}

    // ── 200 OK ─────────────────────────────────────────────────────────────

    public static <T> ResponseEntity<ApiResponse<T>> ok(T data) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .success(true)
                .message("")
                .data(data)
                .build());
    }

    public static <T> ResponseEntity<ApiResponse<T>> ok(T data, String message) {
        return ResponseEntity.ok(ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .build());
    }

    // ── 201 Created ────────────────────────────────────────────────────────

    public static <T> ResponseEntity<ApiResponse<T>> created(T data, String message) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.<T>builder()
                        .success(true)
                        .message(message)
                        .data(data)
                        .build());
    }

    // ── Paginated ──────────────────────────────────────────────────────────

    public static <T> ResponseEntity<PagedApiResponse<T>> paged(List<T> data, MetaResponse meta) {
        return ResponseEntity.ok(PagedApiResponse.<T>builder()
                .success(true)
                .message("")
                .data(data)
                .meta(meta)
                .build());
    }

    public static <T> ResponseEntity<PagedApiResponse<T>> paged(List<T> data, MetaResponse meta, String message) {
        return ResponseEntity.ok(PagedApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .meta(meta)
                .build());
    }

    // ── Error ──────────────────────────────────────────────────────────────

    public static <T> ResponseEntity<ApiResponse<T>> error(String message, HttpStatus status) {
        return ResponseEntity.status(status)
                .body(ApiResponse.<T>builder()
                        .success(false)
                        .message(message)
                        .build());
    }
}
