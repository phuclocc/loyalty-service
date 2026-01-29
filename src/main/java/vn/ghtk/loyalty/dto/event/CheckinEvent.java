package vn.ghtk.loyalty.dto.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Kafka event for loyalty check-in
 * This event is published to Kafka topic and consumed by Flink
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CheckinEvent {
    
    /**
     * Unique event ID for deduplication
     * Format: {userId}_{yyyyMMdd}_{timestamp}
     */
    private String eventId;
    
    /**
     * User ID who performs check-in
     */
    private Long userId;
    
    /**
     * Check-in date (yyyy-MM-dd)
     */
    private LocalDate checkinDate;
    
    /**
     * Event timestamp (ISO-8601)
     */
    private LocalDateTime eventTimestamp;
    
    /**
     * Year-month for partitioning (yyyyMM)
     */
    private String yearMonth;
    
    /**
     * Additional metadata
     */
    private EventMetadata metadata;
}
