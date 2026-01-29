package vn.ghtk.loyalty.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import vn.ghtk.loyalty.dto.request.CheckinV2Request;
import vn.ghtk.loyalty.dto.response.ApiResponse;
import vn.ghtk.loyalty.dto.response.CheckinV2Response;
import vn.ghtk.loyalty.service.CheckinV2Service;
import vn.ghtk.loyalty.util.SecurityUtil;

/**
 * Controller for V2 check-in API (event-driven via Kafka)
 * This is the new API that publishes events to Kafka instead of direct DB writes
 */
@RestController
@RequestMapping("/api/v2/checkin")
@RequiredArgsConstructor
public class CheckinV2Controller {

    private final CheckinV2Service checkinV2Service;

    /**
     * V2 Check-in endpoint - publishes event to Kafka
     * 
     * POST /api/v2/checkin
     * 
     * This endpoint validates the check-in request and publishes an event to Kafka.
     * The actual points crediting is handled asynchronously by Flink consumer.
     * 
     * @param authentication User authentication
     * @param request Optional CheckinV2Request with requestId for idempotency
     * @param httpRequest HttpServletRequest for metadata
     * @return ApiResponse with CheckinV2Response
     */
    @PostMapping
    public ResponseEntity<ApiResponse<CheckinV2Response>> checkin(
            Authentication authentication,
            @RequestBody(required = false) CheckinV2Request request,
            HttpServletRequest httpRequest) {
        
        Long userId = SecurityUtil.getUserIdFromAuthentication(authentication);
        CheckinV2Response response = checkinV2Service.checkin(userId, request, httpRequest);
        
        return ResponseEntity.ok(ApiResponse.<CheckinV2Response>builder()
                .success(true)
                .message(response.getMessage())
                .data(response)
                .build());
    }
}
