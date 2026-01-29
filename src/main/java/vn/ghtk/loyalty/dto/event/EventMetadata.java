package vn.ghtk.loyalty.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Metadata for checkin event
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventMetadata {
    
    /**
     * Source system or service name
     */
    private String source;
    
    /**
     * Event version for schema evolution
     */
    private String version;
    
    /**
     * Request ID for tracing
     */
    private String requestId;
    
    /**
     * Client IP address
     */
    private String clientIp;
    
    /**
     * User agent
     */
    private String userAgent;
}
