package vn.ghtk.loyalty.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.ghtk.loyalty.dto.request.DeductPointsRequest;
import vn.ghtk.loyalty.dto.response.ApiResponse;
import vn.ghtk.loyalty.dto.response.PageResponse;
import vn.ghtk.loyalty.dto.response.PointsHistoryResponse;
import vn.ghtk.loyalty.service.PointsService;
import vn.ghtk.loyalty.util.SecurityUtil;

@RestController
@RequestMapping("/api/points")
@RequiredArgsConstructor
public class PointsController {

    private final PointsService pointsService;

    @PostMapping("/deduct")
    public ResponseEntity<ApiResponse<Void>> deductPoints(
            Authentication authentication,
            @Valid @RequestBody DeductPointsRequest request) {
        Long userId = SecurityUtil.getUserIdFromAuthentication(authentication);
        pointsService.deductPoints(userId, request);
        return ResponseEntity.ok(ApiResponse.<Void>builder()
                .success(true)
                .message("Points deducted successfully")
                .build());
    }

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<PageResponse<PointsHistoryResponse>>> getPointsHistory(
            Authentication authentication,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) Integer month) {
        Long userId = SecurityUtil.getUserIdFromAuthentication(authentication);
        PageResponse<PointsHistoryResponse> history = pointsService.getPointsHistory(userId, page, size, month);
        return ResponseEntity.ok(ApiResponse.<PageResponse<PointsHistoryResponse>>builder()
                .success(true)
                .message("Points history retrieved successfully")
                .data(history)
                .build());
    }
}

