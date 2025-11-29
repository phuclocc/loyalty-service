package vn.ghtk.loyalty.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.ghtk.loyalty.dto.response.ApiResponse;
import vn.ghtk.loyalty.dto.response.CheckinResponse;
import vn.ghtk.loyalty.dto.response.CheckinStatusResponse;
import vn.ghtk.loyalty.service.CheckinService;
import vn.ghtk.loyalty.util.SecurityUtil;

import java.util.List;

@RestController
@RequestMapping("/api/checkin")
@RequiredArgsConstructor
public class CheckinController {

    private final CheckinService checkinService;

    @PostMapping
    public ResponseEntity<ApiResponse<CheckinResponse>> checkin(Authentication authentication) {
        Long userId = SecurityUtil.getUserIdFromAuthentication(authentication);
        CheckinResponse response = checkinService.checkin(userId);
        return ResponseEntity.ok(ApiResponse.<CheckinResponse>builder()
                .success(true)
                .message(response.getMessage())
                .data(response)
                .build());
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<List<CheckinStatusResponse>>> getCheckinStatus(Authentication authentication) {
        Long userId = SecurityUtil.getUserIdFromAuthentication(authentication);
        List<CheckinStatusResponse> statusList = checkinService.getCheckinStatusForCurrentMonth(userId);
        return ResponseEntity.ok(ApiResponse.<List<CheckinStatusResponse>>builder()
                .success(true)
                .message("Check-in status retrieved successfully")
                .data(statusList)
                .build());
    }
}

