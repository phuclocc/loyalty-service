package vn.ghtk.loyalty.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for V2 check-in API
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckinV2Response {
    
    /**
     * Event ID for tracking
     */
    private String eventId;
    
    /**
     * Success status
     */
    private boolean success;
    
    /**
     * Response message
     */
    private String message;
    
    /**
     * Event timestamp
     */
    private String eventTimestamp;
}
