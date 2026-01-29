package vn.ghtk.loyalty.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for V2 check-in API (event-driven)
 * Can be empty body or contain optional fields
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckinV2Request {
    
    /**
     * Optional: Client request ID for idempotency
     */
    private String requestId;
}
