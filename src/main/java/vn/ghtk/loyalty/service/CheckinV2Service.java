package vn.ghtk.loyalty.service;

import jakarta.servlet.http.HttpServletRequest;
import vn.ghtk.loyalty.dto.request.CheckinV2Request;
import vn.ghtk.loyalty.dto.response.CheckinV2Response;

/**
 * Service interface for V2 check-in (event-driven via Kafka)
 */
public interface CheckinV2Service {
    
    /**
     * Process check-in request and publish event to Kafka
     * 
     * @param userId User ID
     * @param request CheckinV2Request
     * @param httpRequest HttpServletRequest for metadata
     * @return CheckinV2Response
     */
    CheckinV2Response checkin(Long userId, CheckinV2Request request, HttpServletRequest httpRequest);
}
